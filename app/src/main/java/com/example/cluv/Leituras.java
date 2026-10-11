package com.example.cluv;

import android.content.Context;
import android.location.Location;
import android.os.BatteryManager;

/**
 * Guarda as ultimas leituras para todas as telas usarem.
 * Ex.: a MainActivity le o barometro e guarda aqui; depois a Api usa esse valor no envio.
 */
public class Leituras {

    public static Location localizacao = null;      // null = ainda nao tem posicao
    public static float pressao = Float.NaN;        // NaN = ainda nao tem leitura
    public static float temperatura = Float.NaN;

    // so guarda se o valor fizer sentido (o emulador manda valores absurdos)
    public static void salvarPressao(float valor) {
        if (valor > 300 && valor < 1100) {
            pressao = valor;
        }
    }

    public static void salvarTemperatura(float valor) {
        if (valor > -60 && valor < 120) {
            temperatura = valor;
        }
    }

    /** Nivel da bateria de 0 a 100. */
    public static int bateria(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
}
