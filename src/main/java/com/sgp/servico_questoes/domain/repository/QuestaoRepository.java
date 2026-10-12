/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.domain.repository;

import com.sgp.servico_questoes.domain.entity.Questao;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 *
 * @author diego-casemiro
 */
public interface QuestaoRepository extends JpaRepository<Questao, Long>, JpaSpecificationExecutor<Questao> {

    Optional<Questao> findByIdAndDeletadoEmIsNull(Long id);
}
