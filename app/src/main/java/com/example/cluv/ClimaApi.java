package com.example.cluv;

import android.location.Location;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Consulta o clima no OpenWeatherMap (GET que devolve JSON). Fica aqui porque duas telas usam:
 * a aba Clima e o aviso de risco antes de iniciar um trajeto.
 * ATENCAO: rede nao pode rodar na thread principal, entao chame dentro de um executor.execute(...)
 */
public class ClimaApi {

    // A chave NAO fica no codigo: ela vem do arquivo local.properties (linha OPENWEATHER_KEY=...),
    // que o Gradle entrega aqui como BuildConfig. Chave gratuita em https://openweathermap.org/api
    private static final String CHAVE_OPENWEATHER = BuildConfig.OPENWEATHER_KEY;

    /** Devolve o clima da posicao. Se o servidor responder erro, lanca uma Exception "O servidor respondeu HTTP ...". */
    public static ClimaDados consultar(Location local) throws Exception {
        if (CHAVE_OPENWEATHER.isEmpty()) {
            throw new Exception("Chave do OpenWeatherMap não configurada (veja o local.properties.example).");
        }
        String endereco = "https://api.openweathermap.org/data/2.5/weather"
                + "?lat=" + local.getLatitude()
                + "&lon=" + local.getLongitude()
                + "&units=metric&lang=pt_br&appid=" + CHAVE_OPENWEATHER;

        HttpURLConnection comm = null;
        try {
            URL url = new URL(endereco);
            comm = (HttpURLConnection) url.openConnection();
            comm.setRequestMethod("GET");
            comm.setConnectTimeout(5000);
            comm.setReadTimeout(5000);

            int codigo = comm.getResponseCode();
            if (codigo != 200) {
                throw new Exception("O servidor respondeu HTTP " + codigo + ".");
            }

            // le a resposta inteira (um JSON)
            BufferedReader leitor = new BufferedReader(new InputStreamReader(comm.getInputStream()));
            String resposta = "";
            String linha;
            while ((linha = leitor.readLine()) != null) {
                resposta += linha;
            }
            leitor.close();

            ClimaDados dados = ClimaDados.lerJson(resposta);

            // guarda o ultimo clima para outras telas usarem sem consultar de novo
            ClimaDados.ultimo = dados;
            ClimaDados.ultimoEmMs = System.currentTimeMillis();
            return dados;
        } finally {
            if (comm != null) {
                comm.disconnect();
            }
        }
    }
}
