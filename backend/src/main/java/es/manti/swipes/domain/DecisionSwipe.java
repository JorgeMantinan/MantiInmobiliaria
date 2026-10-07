package es.manti.swipes.domain;

/**
 * Decisión del usuario sobre un inmueble. Los valores están fijados por la
 * constraint {@code ck_swipes_decision} ({@code LIKE}, {@code NOPE}); aquí sólo
 * se modela lo que el dominio necesita saber: si esa decisión es un favorito.
 */
public enum DecisionSwipe {

    /** Me gusta: además de sacar la carta del deck, aparece en favoritos. */
    LIKE,

    /** No me gusta: saca la carta del deck y no aparece en favoritos. */
    NOPE;

    /** Sólo {@link #LIKE} es un favorito (los {@code NOPE} no se listan ni se borran aquí). */
    public boolean esFavorito() {
        return this == LIKE;
    }
}
