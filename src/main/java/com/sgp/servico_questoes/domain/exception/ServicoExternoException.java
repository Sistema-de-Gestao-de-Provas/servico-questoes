/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.domain.exception;

import org.springframework.http.HttpStatus;

/**
 *
 * @author diego-casemiro
 */
public class ServicoExternoException extends ApiException {

    public ServicoExternoException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
