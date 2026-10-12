/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.domain.service;

import com.sgp.servico_questoes.api.dto.request.AlternativaRequest;
import com.sgp.servico_questoes.api.dto.request.QuestaoRequest;
import com.sgp.servico_questoes.api.dto.response.MateriaResponse;
import com.sgp.servico_questoes.api.dto.response.QuestaoResponse;
import com.sgp.servico_questoes.domain.entity.Alternativa;
import com.sgp.servico_questoes.domain.entity.Questao;
import com.sgp.servico_questoes.domain.enums.Dificuldade;
import com.sgp.servico_questoes.domain.exception.AcessoNegadoException;
import com.sgp.servico_questoes.domain.exception.RecursoNaoEncontradoException;
import com.sgp.servico_questoes.domain.exception.RegraNegocioException;
import com.sgp.servico_questoes.domain.exception.ServicoExternoException;
import com.sgp.servico_questoes.domain.repository.QuestaoRepository;
import com.sgp.servico_questoes.domain.repository.specification.QuestaoSpecification;
import static java.lang.StrictMath.log;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 *
 * @author diego-casemiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestaoService {

    private static final String ENDPOINT_MATERIA_POR_ID = "/v1/materias/{id}";

    private final QuestaoRepository questaoRepository;
    private final ModelMapper modelMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${servico-usuarios.url}")
    private String servicoUsuariosUrl;

    @Transactional(readOnly = true)
    public Page<QuestaoResponse> listar(Long materiaId, Dificuldade dificuldade, String enunciado, Pageable pageable,
            UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        validarOrdenacao(pageable.getSort());
        UUID autor = solicitanteAdmin ? null : solicitanteKeycloakId;
        Pageable paginacao = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return questaoRepository.findAll(QuestaoSpecification.filtrar(materiaId, dificuldade, enunciado, autor,
                pageable.getSort()), paginacao)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public QuestaoResponse buscarPorId(Long id, UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        Questao questao = buscarOuFalhar(id);
        validarAutor(questao, solicitanteKeycloakId, solicitanteAdmin);
        return toResponse(questao);
    }

    public Questao buscarOuFalhar(Long id) {
        return questaoRepository.findByIdAndDeletadoEmIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("questao.nao-encontrada", String.valueOf(id)));
    }

    @Transactional
    public QuestaoResponse criar(QuestaoRequest request, UUID usuarioKeycloakId) {
        Questao questao = new Questao();
        questao.setUsuarioKeycloakId(usuarioKeycloakId);
        aplicarRequest(questao, request);
        return toResponse(questaoRepository.save(questao));
    }

    @Transactional
    public QuestaoResponse atualizar(Long id, QuestaoRequest request, UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        Questao questao = buscarOuFalhar(id);
        validarAutor(questao, solicitanteKeycloakId, solicitanteAdmin);
        aplicarRequest(questao, request);
        return toResponse(questaoRepository.save(questao));
    }

    @Transactional
    public void remover(Long id, UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        Questao questao = buscarOuFalhar(id);
        validarAutor(questao, solicitanteKeycloakId, solicitanteAdmin);
        LocalDateTime agora = LocalDateTime.now();
        questao.setDeletadoEm(agora);
        questao.getAlternativas().forEach(alternativa -> alternativa.setDeletadoEm(agora));
        questaoRepository.save(questao);
    }

    private void validarAutor(Questao questao, UUID solicitanteKeycloakId, boolean solicitanteAdmin) {
        if (!solicitanteAdmin && !questao.getUsuarioKeycloakId().equals(solicitanteKeycloakId)) {
            throw new AcessoNegadoException("questao.somente-autor");
        }
    }

    private void validarAlternativas(List<AlternativaRequest> alternativas) {
        long corretas = alternativas.stream().filter(AlternativaRequest::getCorreta).count();
        if (corretas != 1) {
            throw new RegraNegocioException("questao.uma-alternativa-correta");
        }
    }

    private void validarMateria(Long materiaId) {
        JwtAuthenticationToken token = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        try {
            restClient.get()
                    .uri(servicoUsuariosUrl + ENDPOINT_MATERIA_POR_ID, materiaId)
                    .headers(headers -> headers.setBearerAuth(token.getToken().getTokenValue()))
                    .retrieve()
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
                        throw new RecursoNaoEncontradoException("materia.nao-encontrada", String.valueOf(materiaId));
                    })
                    .body(MateriaResponse.class);
        } catch (RestClientException ex) {
            log.error("Erro ao consultar a matéria {} no servico-usuarios", materiaId, ex);
            throw new ServicoExternoException("servico-usuarios.indisponivel");
        }
    }

    private void validarOrdenacao(Sort sort) {
        sort.stream()
                .map(Sort.Order::getProperty)
                .filter(campo -> !QuestaoSpecification.CAMPOS_ORDENAVEIS.contains(campo))
                .findFirst()
                .ifPresent(campo -> {
                    throw new RegraNegocioException("ordenacao.campo-invalido", campo);
                });
    }

    private void aplicarRequest(Questao questao, QuestaoRequest request) {
        validarAlternativas(request.getAlternativas());
        if (!request.getMateriaId().equals(questao.getMateriaId())) {
            validarMateria(request.getMateriaId());
        }
        questao.setMateriaId(request.getMateriaId());
        questao.setEnunciado(request.getEnunciado());
        questao.setDificuldade(request.getDificuldade());

        questao.getAlternativas().clear();
        request.getAlternativas().forEach(alternativaRequest -> {
            Alternativa alternativa = modelMapper.map(alternativaRequest, Alternativa.class);
            alternativa.setQuestao(questao);
            questao.getAlternativas().add(alternativa);
        });
    }

    private QuestaoResponse toResponse(Questao questao) {
        return modelMapper.map(questao, QuestaoResponse.class);
    }
}
