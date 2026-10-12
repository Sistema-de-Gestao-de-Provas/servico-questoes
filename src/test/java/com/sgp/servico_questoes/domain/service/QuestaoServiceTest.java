/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sgp.servico_questoes.api.dto.request.AlternativaRequest;
import com.sgp.servico_questoes.api.dto.request.QuestaoRequest;
import com.sgp.servico_questoes.api.dto.response.QuestaoResponse;
import com.sgp.servico_questoes.config.mapper.ModelMapperConfig;
import com.sgp.servico_questoes.domain.entity.Alternativa;
import com.sgp.servico_questoes.domain.entity.Questao;
import com.sgp.servico_questoes.domain.enums.Dificuldade;
import com.sgp.servico_questoes.domain.exception.AcessoNegadoException;
import com.sgp.servico_questoes.domain.exception.RecursoNaoEncontradoException;
import com.sgp.servico_questoes.domain.exception.RegraNegocioException;
import com.sgp.servico_questoes.domain.exception.ServicoExternoException;
import com.sgp.servico_questoes.domain.repository.QuestaoRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

/**
 *
 * @author diego-casemiro
 */
@ExtendWith(MockitoExtension.class)
class QuestaoServiceTest {

    private static final UUID AUTOR = UUID.randomUUID();
    private static final UUID OUTRO_USUARIO = UUID.randomUUID();
    private static final String TOKEN = "token-do-solicitante";

    @Mock
    private QuestaoRepository questaoRepository;

    private QuestaoService questaoService;

    // Simula o servico-usuarios: guarda as requisições recebidas e responde com o status configurado
    private HttpServer servicoUsuarios;
    private final List<String> caminhosRecebidos = new ArrayList<>();
    private final List<String> authorizationsRecebidos = new ArrayList<>();
    private int statusMateria = 200;

    @BeforeEach
    void setUp() throws IOException {
        servicoUsuarios = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servicoUsuarios.createContext("/api/v1/materias/", exchange -> {
            caminhosRecebidos.add(exchange.getRequestURI().getPath());
            authorizationsRecebidos.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] corpo = "{\"id\":1,\"nome\":\"Cálculo\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusMateria, corpo.length);
            exchange.getResponseBody().write(corpo);
            exchange.close();
        });
        servicoUsuarios.start();

        questaoService = new QuestaoService(questaoRepository, new ModelMapperConfig().modelMapper());
        ReflectionTestUtils.setField(questaoService, "servicoUsuariosUrl",
                "http://localhost:" + servicoUsuarios.getAddress().getPort() + "/api");

        Jwt jwt = Jwt.withTokenValue(TOKEN)
                .header("alg", "none")
                .subject(AUTOR.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        servicoUsuarios.stop(0);
        SecurityContextHolder.clearContext();
    }

    @Test
    void listarDeveMapearAsQuestoesEPassarAOrdenacaoParaASpecification() {
        Pageable pageable = PageRequest.of(2, 10, Sort.by("dificuldade"));
        when(questaoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(questao(1L, AUTOR, 1L))));

        Page<QuestaoResponse> resultado = questaoService.listar(1L, Dificuldade.FACIL, "brasil", pageable, AUTOR,
                false);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(resultado.getContent().get(0).getAlternativas()).hasSize(2);

        // A ordenação vai na specification; o Pageable chega ao repository só com página e tamanho
        ArgumentCaptor<Pageable> paginacao = ArgumentCaptor.forClass(Pageable.class);
        verify(questaoRepository).findAll(any(Specification.class), paginacao.capture());
        assertThat(paginacao.getValue().getPageNumber()).isEqualTo(2);
        assertThat(paginacao.getValue().getPageSize()).isEqualTo(10);
        assertThat(paginacao.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void listarDeveRecusarOrdenacaoPorCampoInexistente() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("senha"));

        assertThatThrownBy(() -> questaoService.listar(null, null, null, pageable, AUTOR, false))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("ordenacao.campo-invalido");
        verify(questaoRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void buscarPorIdDeveRetornarAQuestaoParaOAutor() {
        when(questaoRepository.findByIdAndDeletadoEmIsNull(1L)).thenReturn(Optional.of(questao(1L, AUTOR, 1L)));

        QuestaoResponse resultado = questaoService.buscarPorId(1L, AUTOR, false);

        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getUsuarioKeycloakId()).isEqualTo(AUTOR);
        assertThat(resultado.getEnunciado()).isEqualTo("Qual a capital do Brasil?");
    }

    @Test
    void buscarPorIdDevePermitirQueOAdminVejaQuestaoDeOutroUsuario() {
        when(questaoRepository.findByIdAndDeletadoEmIsNull(1L)).thenReturn(Optional.of(questao(1L, AUTOR, 1L)));

        QuestaoResponse resultado = questaoService.buscarPorId(1L, OUTRO_USUARIO, true);

        assertThat(resultado.getId()).isEqualTo(1L);
    }

    @Test
    void buscarPorIdDeveNegarAcessoAQuemNaoEOAutor() {
        when(questaoRepository.findByIdAndDeletadoEmIsNull(1L)).thenReturn(Optional.of(questao(1L, AUTOR, 1L)));

        assertThatThrownBy(() -> questaoService.buscarPorId(1L, OUTRO_USUARIO, false))
                .isInstanceOf(AcessoNegadoException.class)
                .hasMessage("questao.somente-autor");
    }

    @Test
    void buscarPorIdDeveLancarNaoEncontradoQuandoAQuestaoNaoExiste() {
        when(questaoRepository.findByIdAndDeletadoEmIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> questaoService.buscarPorId(99L, AUTOR, false))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("questao.nao-encontrada");
    }

    @Test
    void criarDeveGravarAQuestaoComOAutorDoTokenEValidarAMateriaNoServicoUsuarios() {
        when(questaoRepository.save(any(Questao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        QuestaoResponse resultado = questaoService.criar(request(1L, true, false), AUTOR);

        ArgumentCaptor<Questao> salva = ArgumentCaptor.forClass(Questao.class);
        verify(questaoRepository).save(salva.capture());
        Questao questao = salva.getValue();
        assertThat(questao.getUsuarioKeycloakId()).isEqualTo(AUTOR);
        assertThat(questao.getMateriaId()).isEqualTo(1L);
        assertThat(questao.getAlternativas()).hasSize(2)
                .allSatisfy(alternativa -> assertThat(alternativa.getQuestao()).isSameAs(questao));
        assertThat(resultado.getAlternativas()).hasSize(2);

        // Consulta a matéria repassando o token de quem fez a requisição
        assertThat(caminhosRecebidos).containsExactly("/api/v1/materias/1");
        assertThat(authorizationsRecebidos).containsExactly("Bearer " + TOKEN);
    }

    @Test
    void criarDeveExigirExatamenteUmaAlternativaCorreta() {
        assertThatThrownBy(() -> questaoService.criar(request(1L, false, false), AUTOR))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("questao.uma-alternativa-correta");
        assertThatThrownBy(() -> questaoService.criar(request(1L, true, true), AUTOR))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("questao.uma-alternativa-correta");

        assertThat(caminhosRecebidos).isEmpty();
        verify(questaoRepository, never()).save(any());
    }

    @Test
    void criarDeveLancarNaoEncontradoQuandoAMateriaNaoExisteNoServicoUsuarios() {
        statusMateria = 404;

        assertThatThrownBy(() -> questaoService.criar(request(99L, true, false), AUTOR))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("materia.nao-encontrada");
        verify(questaoRepository, never()).save(any());
    }

    @Test
    void criarDeveLancarServicoExternoQuandoOServicoUsuariosFalha() {
        statusMateria = 500;

        assertThatThrownBy(() -> questaoService.criar(request(1L, true, false), AUTOR))
                .isInstanceOf(ServicoExternoException.class)
                .hasMessage("servico-usuarios.indisponivel");
        verify(questaoRepository, never()).save(any());
    }

    @Test
    void criarDeveLancarServicoExternoQuandoOServicoUsuariosEstaFora() {
        servicoUsuarios.stop(0);

        assertThatThrownBy(() -> questaoService.criar(request(1L, true, false), AUTOR))
                .isInstanceOf(ServicoExternoException.class)
                .hasMessage("servico-usuarios.indisponivel");
        verify(questaoRepository, never()).save(any());
    }

    @Test
    void atualizarNaoDeveConsultarOServicoUsuariosQuandoAMateriaNaoMudou() {
        when(questaoRepository.findByIdAndDeletadoEmIsNull(1L)).thenReturn(Optional.of(questao(1L, AUTOR, 1L)));
        when(questaoRepository.save(any(Questao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        QuestaoResponse resultado = questaoService.atualizar(1L, request(1L, false, true), AUTOR, false);

        assertThat(caminhosRecebidos).isEmpty();
        assertThat(resultado.getEnunciado()).isEqualTo("Enunciado do request");
        // As alternativas antigas são trocadas pelas do request
        assertThat(resultado.getAlternativas()).extracting("texto").containsExactly("A", "B");
    }

    @Test
    void atualizarDeveValidarANovaMateriaQuandoElaMudou() {
        when(questaoRepository.findByIdAndDeletadoEmIsNull(1L)).thenReturn(Optional.of(questao(1L, AUTOR, 1L)));
        when(questaoRepository.save(any(Questao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        QuestaoResponse resultado = questaoService.atualizar(1L, request(2L, true, false), AUTOR, false);

        assertThat(caminhosRecebidos).containsExactly("/api/v1/materias/2");
        assertThat(resultado.getMateriaId()).isEqualTo(2L);
    }

    @Test
    void atualizarDeveNegarAcessoAQuemNaoEOAutor() {
        when(questaoRepository.findByIdAndDeletadoEmIsNull(1L)).thenReturn(Optional.of(questao(1L, AUTOR, 1L)));

        assertThatThrownBy(() -> questaoService.atualizar(1L, request(1L, true, false), OUTRO_USUARIO, false))
                .isInstanceOf(AcessoNegadoException.class);
        verify(questaoRepository, never()).save(any());
    }

    @Test
    void removerDeveMarcarAQuestaoEAsAlternativasComoDeletadas() {
        Questao questao = questao(1L, AUTOR, 1L);
        when(questaoRepository.findByIdAndDeletadoEmIsNull(1L)).thenReturn(Optional.of(questao));

        questaoService.remover(1L, AUTOR, false);

        verify(questaoRepository).save(questao);
        assertThat(questao.getDeletadoEm()).isNotNull();
        assertThat(questao.getAlternativas()).allSatisfy(alternativa ->
                assertThat(alternativa.getDeletadoEm()).isEqualTo(questao.getDeletadoEm()));
    }

    @Test
    void removerDeveNegarAcessoAQuemNaoEOAutor() {
        when(questaoRepository.findByIdAndDeletadoEmIsNull(1L)).thenReturn(Optional.of(questao(1L, AUTOR, 1L)));

        assertThatThrownBy(() -> questaoService.remover(1L, OUTRO_USUARIO, false))
                .isInstanceOf(AcessoNegadoException.class);
        verify(questaoRepository, never()).save(any());
    }

    private Questao questao(Long id, UUID autor, Long materiaId) {
        Questao questao = new Questao();
        ReflectionTestUtils.setField(questao, "id", id);
        questao.setUsuarioKeycloakId(autor);
        questao.setMateriaId(materiaId);
        questao.setEnunciado("Qual a capital do Brasil?");
        questao.setDificuldade(Dificuldade.FACIL);
        questao.getAlternativas().add(alternativa(questao, "Brasília", true));
        questao.getAlternativas().add(alternativa(questao, "Rio de Janeiro", false));
        return questao;
    }

    private Alternativa alternativa(Questao questao, String texto, boolean correta) {
        Alternativa alternativa = new Alternativa();
        alternativa.setQuestao(questao);
        alternativa.setTexto(texto);
        alternativa.setCorreta(correta);
        return alternativa;
    }

    private QuestaoRequest request(Long materiaId, boolean primeiraCorreta, boolean segundaCorreta) {
        return new QuestaoRequest(materiaId, "Enunciado do request", Dificuldade.MEDIO, List.of(
                new AlternativaRequest("A", primeiraCorreta),
                new AlternativaRequest("B", segundaCorreta)));
    }
}
