package com.example.cluv;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.location.LocationManager;

/** Liga e desliga o GPS. Varias telas usam, entao fica aqui para nao repetir o codigo. */
public class Gps {

    public static boolean temPermissao(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Pede a posicao a cada 5 segundos, pelo GPS e pela rede (a rede funciona dentro de casa). */
    public static void ligar(Context context, LocationListener ouvinte) {
        if (!temPermissao(context)) {
            return;
        }
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, ouvinte);
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, ouvinte);
            }
        } catch (SecurityException e) {
            // sem permissao: so nao recebe posicao
        }
    }

    public static void desligar(Context context, LocationListener ouvinte) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        lm.removeUpdates(ouvinte);
    }
}
