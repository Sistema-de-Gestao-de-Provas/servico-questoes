/*
 * SGP - servico-questoes
 */
package com.sgp.servico_questoes.domain.entity;

import com.sgp.servico_questoes.domain.enums.Dificuldade;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 *
 * @author diego-casemiro
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Questao extends EntidadeBase {

    @Column(name = "usuario_keycloak_id", nullable = false, updatable = false)
    private UUID usuarioKeycloakId;

    @Column(name = "materia_id", nullable = false)
    private Long materiaId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String enunciado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Dificuldade dificuldade;

    @OneToMany(mappedBy = "questao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Alternativa> alternativas = new ArrayList<>();
}
