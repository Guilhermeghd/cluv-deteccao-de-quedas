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

/**
 * Envio para a API do professor (mesma logica do appMove).
 * tipo_registro 2 = alerta (queda, panico, check-in)  |  tipo_registro 1 = telemetria do trajeto
 */
public class Api {

    private static final String URL_API = "https://ideaberta.com.br/socorro/";

    // "aguardando", "online" ou "offline" (as telas Inicio e Historico mostram isso)
    public static String statusServidor = "aguardando";

    // valores de teste. So entram na queda simulada (botao "Simular queda") e entram SEMPRE,
    // mesmo que o aparelho tenha GPS e sensores: assim o teste sai igual toda vez.
    private static final double TESTE_LATITUDE = -21.16451667;
    private static final double TESTE_LONGITUDE = -51.03855667;
    private static final float TESTE_VELOCIDADE_KMH = 12.4f;

    // valores usados em QUALQUER envio quando o celular nao tem o sensor (a maioria nao tem barometro nem termometro)
    private static final float PRESSAO_PADRAO = 1000f;       // hPa
    private static final float TEMPERATURA_PADRAO = 28.1f;   // graus Celsius

    /** Monta o texto do POST: "campo=valor&campo=valor..." */
    public static String montarDados(Context context, int tipoRegistro, float impacto) {
        return montarDados(context, tipoRegistro, impacto, false);
    }

    /**
     * Igual ao de cima. Com simulado = true (botao "Simular queda"), localizacao, velocidade,
     * temperatura e pressao vao com os valores fixos de teste.
     */
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

        // forca da queda em G (1 G = 9,8 m/s2). So vai quando foi uma queda de verdade.
        if (impacto > 0) {
            dados += "&nivel_queda=" + String.format(Locale.US, "%.1f", impacto / 9.8);
        }

        Location local = Leituras.localizacao;
        if (simulado) {
            // tudo fixo (o GPS parado diria velocidade 0, por exemplo)
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
                dados += "&velocidade=" + String.format(Locale.US, "%.1f", local.getSpeed() * 3.6);   // m/s para km/h
            }
        }

        // sensor do aparelho; se nao tiver (NaN), vai o valor padrao
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

    /** Pega o valor de um campo do texto do POST ("campo=valor&campo=valor"). Devolve "" se nao tiver. */
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

    /**
     * Transforma o texto do POST ("campo=valor&campo=valor") em linhas legiveis para a janela de detalhes.
     * Mostra os valores que foram mandados (ou que seriam, se o alerta foi cancelado), inclusive os de teste e os padroes.
     */
    public static String resumoDosDados(String dados) {
        String aparelho = "", bateria = "", latitude = "", longitude = "", velocidade = "";
        String temperatura = "", pressao = "", queda = "";

        for (String par : dados.split("&")) {
            String[] partes = par.split("=", 2);
            if (partes.length < 2) {
                continue;
            }
            // o nome "UTF-8" em texto funciona em qualquer versao do Android (a versao com Charset trava no Android 11)
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

    /**
     * Faz o POST. Devolve true se a API respondeu 200.
     * ATENCAO: rede nao pode rodar na thread principal, entao chame isso dentro de um executor.execute(...)
     */
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
