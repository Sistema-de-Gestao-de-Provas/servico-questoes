/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class AlternativaRequest {

    @NotBlank
    private String texto;

    @NotNull
    private Boolean correta;
}
