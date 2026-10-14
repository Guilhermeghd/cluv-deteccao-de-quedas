package com.example.cluv.api;

import com.example.cluv.BuildConfig;
import com.example.cluv.modelo.ClimaDados;

import android.location.Location;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class ClimaApi {


    private static final String CHAVE_OPENWEATHER = BuildConfig.OPENWEATHER_KEY;
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

            BufferedReader leitor = new BufferedReader(new InputStreamReader(comm.getInputStream()));
            String resposta = "";
            String linha;
            while ((linha = leitor.readLine()) != null) {
                resposta += linha;
            }
            leitor.close();

            ClimaDados dados = ClimaDados.lerJson(resposta);

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
