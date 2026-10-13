package com.example.cluv.modelo;

/**
 * Diz se a queda foi leve, moderada ou grave, pela forca do impacto em G (1 G = 9,8 m/s2).
 * Para mudar a sensibilidade, e so ajustar os dois limites aqui embaixo.
 *
 *   ate LIMITE_MODERADA_G     -> leve
 *   ate LIMITE_GRAVE_G        -> moderada
 *   acima de LIMITE_GRAVE_G   -> grave
 *
 * (a queda so e detectada a partir de ~2,6 G, que e o LIMITE_IMPACTO da MainActivity)
 */
public class GravidadeQueda {

    public static final float LIMITE_MODERADA_G = 4f;
    public static final float LIMITE_GRAVE_G = 6f;

    /** Recebe o impacto em m/s2 (o mesmo valor que a MainActivity mede) e devolve "leve", "moderada" ou "grave". */
    public static String classificar(float impactoMs2) {
        float g = impactoMs2 / 9.8f;
        if (g > LIMITE_GRAVE_G) {
            return "grave";
        } else if (g > LIMITE_MODERADA_G) {
            return "moderada";
        }
        return "leve";
    }
}
