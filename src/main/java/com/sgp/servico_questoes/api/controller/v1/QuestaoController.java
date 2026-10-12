/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.api.controller.v1;

import com.sgp.servico_questoes.api.dto.request.QuestaoRequest;
import com.sgp.servico_questoes.api.dto.response.QuestaoResponse;
import com.sgp.servico_questoes.domain.enums.Dificuldade;
import com.sgp.servico_questoes.domain.service.QuestaoService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author diego-casemiro
 */
@RestController
@RequestMapping("/v1/questoes")
@RequiredArgsConstructor
public class QuestaoController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final QuestaoService questaoService;

    // Ex.: GET /v1/questoes?materiaId=1&dificuldade=FACIL&enunciado=brasil&page=0&size=20&sort=dificuldade,asc
    // Campos ordenáveis: id, materiaId, enunciado, dificuldade, criadoEm, atualizadoEm
    @GetMapping
    public PagedModel<QuestaoResponse> listar(
            @RequestParam(required = false) Long materiaId,
            @RequestParam(required = false) Dificuldade dificuldade,
            @RequestParam(required = false) String enunciado,
            @PageableDefault(sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable,
            JwtAuthenticationToken token) {
        return new PagedModel<>(questaoService.listar(materiaId, dificuldade, enunciado, pageable,
                keycloakId(token), admin(token)));
    }

    @GetMapping("/{id}")
    public QuestaoResponse buscarPorId(@PathVariable Long id, JwtAuthenticationToken token) {
        return questaoService.buscarPorId(id, keycloakId(token), admin(token));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuestaoResponse criar(@Valid @RequestBody QuestaoRequest request, JwtAuthenticationToken token) {
        return questaoService.criar(request, keycloakId(token));
    }

    @PutMapping("/{id}")
    public QuestaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody QuestaoRequest request,
            JwtAuthenticationToken token) {
        return questaoService.atualizar(id, request, keycloakId(token), admin(token));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id, JwtAuthenticationToken token) {
        questaoService.remover(id, keycloakId(token), admin(token));
    }

    private UUID keycloakId(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getSubject());
    }

    private boolean admin(JwtAuthenticationToken token) {
        return token.getAuthorities().stream().anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }
}
