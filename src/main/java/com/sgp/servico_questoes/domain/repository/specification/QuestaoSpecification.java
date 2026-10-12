/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.domain.repository.specification;

import com.sgp.servico_questoes.domain.entity.Questao;
import com.sgp.servico_questoes.domain.enums.Dificuldade;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 *
 * @author diego-casemiro
 */
public final class QuestaoSpecification {

    public static final Set<String> CAMPOS_ORDENAVEIS = Set.of(
            "id", "materiaId", "enunciado", "dificuldade", "criadoEm", "atualizadoEm");

    private QuestaoSpecification() {
    }

    public static Specification<Questao> filtrar(Long materiaId, Dificuldade dificuldade, String enunciado,
            UUID usuarioKeycloakId, Sort sort) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.isNull(root.get("deletadoEm")));

            if (usuarioKeycloakId != null) {
                predicados.add(cb.equal(root.get("usuarioKeycloakId"), usuarioKeycloakId));
            }
            if (materiaId != null) {
                predicados.add(cb.equal(root.get("materiaId"), materiaId));
            }
            if (dificuldade != null) {
                predicados.add(cb.equal(root.get("dificuldade"), dificuldade));
            }
            if (StringUtils.hasText(enunciado)) {
                predicados.add(cb.like(cb.lower(root.get("enunciado")),
                        "%" + enunciado.trim().toLowerCase() + "%"));
            }

            if (!Long.class.equals(query.getResultType())) {
                query.orderBy(ordenar(sort, root, cb));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    private static List<Order> ordenar(Sort sort, Root<Questao> root, CriteriaBuilder cb) {
        List<Order> ordens = new ArrayList<>();
        for (Sort.Order ordem : sort) {
            Expression<?> campo = "dificuldade".equals(ordem.getProperty())
                    ? ordemDificuldade(root, cb)
                    : root.get(ordem.getProperty());
            ordens.add(ordem.isAscending() ? cb.asc(campo) : cb.desc(campo));
        }
        ordens.add(cb.asc(root.get("id")));
        return ordens;
    }

    private static Expression<Integer> ordemDificuldade(Root<Questao> root, CriteriaBuilder cb) {
        CriteriaBuilder.Case<Integer> ordem = cb.selectCase();
        for (Dificuldade dificuldade : Dificuldade.values()) {
            ordem = ordem.when(cb.equal(root.get("dificuldade"), dificuldade), dificuldade.ordinal());
        }
        return ordem.otherwise(Dificuldade.values().length);
    }
}
