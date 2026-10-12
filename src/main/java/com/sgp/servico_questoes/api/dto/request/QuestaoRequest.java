/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.api.dto.request;

import com.sgp.servico_questoes.domain.enums.Dificuldade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
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
public class QuestaoRequest {

    // Validado no servico-usuarios ao criar ou ao trocar a matéria da questão
    @NotNull
    private Long materiaId;

    @NotBlank
    private String enunciado;

    @NotNull
    private Dificuldade dificuldade;

    @NotNull
    @Size(min = 2)
    private List<@NotNull @Valid AlternativaRequest> alternativas;
}
