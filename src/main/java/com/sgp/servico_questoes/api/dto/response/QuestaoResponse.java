/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.api.dto.response;

import com.sgp.servico_questoes.domain.enums.Dificuldade;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 *
 * @author diego-casemiro
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestaoResponse {

    private Long id;

    private UUID usuarioKeycloakId;

    private Long materiaId;

    private String enunciado;

    private Dificuldade dificuldade;

    private List<AlternativaResponse> alternativas;

    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;
}
