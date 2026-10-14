package com.example.cluv.modelo;


public class GravidadeQueda {

    public static final float LIMITE_MODERADA_G = 4f;
    public static final float LIMITE_GRAVE_G = 6f;

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
