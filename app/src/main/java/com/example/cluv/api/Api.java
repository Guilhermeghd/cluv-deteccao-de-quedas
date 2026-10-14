package com.example.cluv.api;

import com.example.cluv.dados.Leituras;
import com.example.cluv.dados.Sessao;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.provider.Settings;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;


public class Api {

    private static final String URL_API = "https://ideaberta.com.br/socorro/";

    public static String statusServidor = "aguardando";
    private static final double TESTE_LATITUDE = -21.16451667;
    private static final double TESTE_LONGITUDE = -51.03855667;
    private static final float TESTE_VELOCIDADE_KMH = 12.4f;

    private static final float PRESSAO_PADRAO = 1000f;
    private static final float TEMPERATURA_PADRAO = 28.1f;

    public static String montarDados(Context context, int tipoRegistro, float impacto) {
        return montarDados(context, tipoRegistro, impacto, false);
    }

    public static String montarDados(Context context, int tipoRegistro, float impacto, boolean simulado) {
        String nome = Sessao.nome(context);
        if (nome.isEmpty()) {
            nome = "Não informado";
        }
        String deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String modelo = Build.MANUFACTURER + " " + Build.MODEL;

        String dados = "usuario=" + codificar(nome)
                + "&tipo_registro=" + tipoRegistro
                + "&device_id=" + codificar(deviceId)
                + "&modelo=" + codificar(modelo)
                + "&bateria=" + Leituras.bateria(context);

        if (impacto > 0) {
            dados += "&nivel_queda=" + String.format(Locale.US, "%.1f", impacto / 9.8);
        }

        Location local = Leituras.localizacao;
        if (simulado) {
            dados += "&latitude=" + TESTE_LATITUDE;
            dados += "&longitude=" + TESTE_LONGITUDE;
            dados += "&velocidade=" + String.format(Locale.US, "%.1f", TESTE_VELOCIDADE_KMH);
            dados += "&pressao=" + String.format(Locale.US, "%.1f", PRESSAO_PADRAO);
            dados += "&temperatura=" + String.format(Locale.US, "%.1f", TEMPERATURA_PADRAO);
            return dados;
        }

        if (local != null) {
            dados += "&latitude=" + local.getLatitude();
            dados += "&longitude=" + local.getLongitude();
            if (local.hasSpeed()) {
                dados += "&velocidade=" + String.format(Locale.US, "%.1f", local.getSpeed() * 3.6);
            }
        }

        float pressao = Leituras.pressao;
        if (Float.isNaN(pressao)) {
            pressao = PRESSAO_PADRAO;
        }
        float temperatura = Leituras.temperatura;
        if (Float.isNaN(temperatura)) {
            temperatura = TEMPERATURA_PADRAO;
        }
        dados += "&pressao=" + String.format(Locale.US, "%.1f", pressao);
        dados += "&temperatura=" + String.format(Locale.US, "%.1f", temperatura);
        return dados;
    }

    public static String valorDoCampo(String dados, String campo) {
        for (String par : dados.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes.length == 2 && partes[0].equals(campo)) {
                try {
                    return URLDecoder.decode(partes[1], "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return partes[1];
                }
            }
        }
        return "";
    }

    public static String resumoDosDados(String dados) {
        String aparelho = "", bateria = "", latitude = "", longitude = "", velocidade = "";
        String temperatura = "", pressao = "", queda = "";

        for (String par : dados.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes.length < 2) {
                continue;
            }
            String valor;
            try {
                valor = URLDecoder.decode(partes[1], "UTF-8");
            } catch (UnsupportedEncodingException e) {
                valor = partes[1];
            }
            switch (partes[0]) {
                case "modelo": aparelho = valor; break;
                case "bateria": bateria = valor; break;
                case "latitude": latitude = valor; break;
                case "longitude": longitude = valor; break;
                case "velocidade": velocidade = valor; break;
                case "temperatura": temperatura = valor; break;
                case "pressao": pressao = valor; break;
                case "nivel_queda": queda = valor; break;
            }
        }

        String texto = "Aparelho: " + aparelho + "\nBateria: " + bateria + "%";
        if (!queda.isEmpty()) {
            texto += "\nIntensidade: " + queda + " G";
        }
        if (!latitude.isEmpty()) {
            texto += "\nLocalização: " + latitude + ", " + longitude;
        } else {
            texto += "\nLocalização: sem posição do GPS";
        }
        if (!velocidade.isEmpty()) {
            texto += "\nVelocidade: " + velocidade + " km/h";
        }
        if (!temperatura.isEmpty()) {
            texto += "\nTemperatura: " + temperatura + " °C";
        }
        if (!pressao.isEmpty()) {
            texto += "\nPressão: " + pressao + " hPa";
        }
        return texto;
    }

    public static boolean enviar(String dados) {
        HttpURLConnection comm = null;
        boolean deuCerto = false;

        try {
            URL url = new URL(URL_API);

            comm = (HttpURLConnection) url.openConnection();
            comm.setRequestMethod("POST");
            comm.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            comm.setConnectTimeout(5000);
            comm.setReadTimeout(5000);
            comm.setDoOutput(true);

            OutputStream os = comm.getOutputStream();
            os.write(dados.getBytes(StandardCharsets.UTF_8));
            os.close();

            if (comm.getResponseCode() == 200) {
                deuCerto = true;
            }
        } catch (Exception e) {
            deuCerto = false;
        } finally {
            if (comm != null) {
                comm.disconnect();
            }
        }

        if (deuCerto) {
            statusServidor = "online";
        } else {
            statusServidor = "offline";
        }
        return deuCerto;
    }

    private static String codificar(String texto) {
        try {
            return URLEncoder.encode(texto, "UTF-8");
        } catch (Exception e) {
            return texto;
        }
    }
}
