/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.api.controller.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sgp.servico_questoes.api.dto.request.QuestaoRequest;
import com.sgp.servico_questoes.api.dto.response.AlternativaResponse;
import com.sgp.servico_questoes.api.dto.response.QuestaoResponse;
import com.sgp.servico_questoes.config.internationalization.InternationalizationConfig;
import com.sgp.servico_questoes.config.security.SecurityConfig;
import com.sgp.servico_questoes.domain.enums.Dificuldade;
import com.sgp.servico_questoes.domain.exception.AcessoNegadoException;
import com.sgp.servico_questoes.domain.exception.RecursoNaoEncontradoException;
import com.sgp.servico_questoes.domain.service.QuestaoService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 *
 * @author diego-casemiro
 */
@WebMvcTest(QuestaoController.class)
@Import({SecurityConfig.class, InternationalizationConfig.class})
class QuestaoControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();

    private static final String QUESTAO_JSON = """
            {
              "materiaId": 1,
              "enunciado": "Qual a capital do Brasil?",
              "dificuldade": "FACIL",
              "alternativas": [
                {"texto": "Brasília", "correta": true},
                {"texto": "Rio de Janeiro", "correta": false}
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestaoService questaoService;

    // Os tokens dos testes já chegam prontos pelo jwt(); o decoder só existe para o contexto subir sem o Keycloak
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listarDeveRepassarOsFiltrosEAPaginacaoParaOService() throws Exception {
        when(questaoService.listar(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of(response())));

        mockMvc.perform(get("/v1/questoes")
                        .param("materiaId", "1")
                        .param("dificuldade", "FACIL")
                        .param("enunciado", "brasil")
                        .param("page", "1")
                        .param("size", "5")
                        .with(professor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].alternativas.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(questaoService).listar(eq(1L), eq(Dificuldade.FACIL), eq("brasil"), pageable.capture(), eq(USUARIO),
                eq(false));
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        // Sem sort na URL, vale a ordenação padrão do @PageableDefault
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "criadoEm"));
    }

    @Test
    void listarSemFiltrosDevePassarNuloEIdentificarOAdmin() throws Exception {
        when(questaoService.listar(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/v1/questoes").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(questaoService).listar(isNull(), isNull(), isNull(), any(Pageable.class), eq(USUARIO), eq(true));
    }

    @Test
    void listarDeveExigirAutenticacao() throws Exception {
        mockMvc.perform(get("/v1/questoes"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(questaoService);
    }

    @Test
    void buscarPorIdDeveRetornarAQuestao() throws Exception {
        when(questaoService.buscarPorId(1L, USUARIO, false)).thenReturn(response());

        mockMvc.perform(get("/v1/questoes/1").with(professor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.enunciado").value("Qual a capital do Brasil?"))
                .andExpect(jsonPath("$.dificuldade").value("FACIL"));
    }

    @Test
    void buscarPorIdDeveResponder404QuandoAQuestaoNaoExiste() throws Exception {
        when(questaoService.buscarPorId(99L, USUARIO, false))
                .thenThrow(new RecursoNaoEncontradoException("questao.nao-encontrada", "99"));

        mockMvc.perform(get("/v1/questoes/99").with(professor()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Questão 99 não encontrada."));
    }

    @Test
    void buscarPorIdDeveResponder403QuandoOUsuarioNaoEOAutor() throws Exception {
        when(questaoService.buscarPorId(1L, USUARIO, false))
                .thenThrow(new AcessoNegadoException("questao.somente-autor"));

        mockMvc.perform(get("/v1/questoes/1").with(professor()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("Apenas o autor da questão ou um administrador pode acessá-la."));
    }

    @Test
    void criarDeveResponder201ComAQuestaoCriadaPeloUsuarioDoToken() throws Exception {
        when(questaoService.criar(any(QuestaoRequest.class), eq(USUARIO))).thenReturn(response());

        mockMvc.perform(post("/v1/questoes")
                        .with(professor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUESTAO_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        ArgumentCaptor<QuestaoRequest> request = ArgumentCaptor.forClass(QuestaoRequest.class);
        verify(questaoService).criar(request.capture(), eq(USUARIO));
        assertThat(request.getValue().getMateriaId()).isEqualTo(1L);
        assertThat(request.getValue().getAlternativas()).hasSize(2);
    }

    @Test
    void criarDeveResponder400ComOsCamposInvalidos() throws Exception {
        mockMvc.perform(post("/v1/questoes")
                        .with(professor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enunciado": "", "alternativas": [{"texto": "Única", "correta": true}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Um ou mais campos são inválidos."))
                .andExpect(jsonPath("$.erros.materiaId").exists())
                .andExpect(jsonPath("$.erros.enunciado").exists())
                .andExpect(jsonPath("$.erros.dificuldade").exists())
                .andExpect(jsonPath("$.erros.alternativas").exists());

        verifyNoInteractions(questaoService);
    }

    @Test
    void atualizarDeveRepassarOIdEOSolicitante() throws Exception {
        when(questaoService.atualizar(eq(1L), any(QuestaoRequest.class), eq(USUARIO), eq(true)))
                .thenReturn(response());

        mockMvc.perform(put("/v1/questoes/1")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(QUESTAO_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void removerDeveResponder204() throws Exception {
        mockMvc.perform(delete("/v1/questoes/1").with(professor()))
                .andExpect(status().isNoContent());

        verify(questaoService).remover(1L, USUARIO, false);
    }

    private JwtRequestPostProcessor professor() {
        return jwt().jwt(jwt -> jwt.subject(USUARIO.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_PROFESSOR"));
    }

    private JwtRequestPostProcessor admin() {
        return jwt().jwt(jwt -> jwt.subject(USUARIO.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private QuestaoResponse response() {
        return new QuestaoResponse(1L, USUARIO, 1L, "Qual a capital do Brasil?", Dificuldade.FACIL, List.of(
                new AlternativaResponse(1L, "Brasília", true),
                new AlternativaResponse(2L, "Rio de Janeiro", false)), null, null);
    }
}
