package com.example.cluv.dados;

import android.content.Context;
import android.location.Location;
import android.os.BatteryManager;


public class Leituras {

    public static Location localizacao = null;
    public static float pressao = Float.NaN;
    public static float temperatura = Float.NaN;

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

    public static int bateria(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
}
