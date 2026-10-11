package com.example.cluv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Pede a rota para o OSRM (servidor gratuito, sem chave). Mesma logica da consulta de clima: um GET que devolve JSON.
 * Usa o servidor routing.openstreetmap.de, que tem um perfil para cada meio de transporte:
 *   routed-foot (a pe), routed-bike (bicicleta) e routed-car (carro; a moto usa o mesmo, pois anda pelas mesmas ruas).
 * Exemplo: https://routing.openstreetmap.de/routed-foot/route/v1/driving/-51.03,-21.16;-51.04,-21.17?overview=full&geometries=geojson
 * ATENCAO: o OSRM pede a longitude ANTES da latitude. (O "driving" no meio do endereco e fixo, o perfil e o routed-xxx.)
 */
public class RotaApi {

    private static final String URL_BASE = "https://routing.openstreetmap.de/";

    /**
     * Devolve a rota, ou null se o servidor nao achou caminho (NoRoute / NoSegment).
     * Qualquer outro problema (sem internet, servidor com erro) vira uma Exception com o motivo.
     * Rede nao pode rodar na thread principal, entao chame dentro de um executor.execute(...)
     */
    public static Rota buscar(GeoPoint origem, GeoPoint destino, String perfil) throws Exception {
        String endereco = URL_BASE + perfil + "/route/v1/driving/"
                + String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f",
                origem.getLongitude(), origem.getLatitude(),
                destino.getLongitude(), destino.getLatitude())
                + "?overview=full&geometries=geojson";

        HttpURLConnection comm = null;
        try {
            URL url = new URL(endereco);
            comm = (HttpURLConnection) url.openConnection();
            comm.setRequestMethod("GET");
            comm.setConnectTimeout(8000);
            comm.setReadTimeout(8000);
            // o servidor pede que o app se identifique
            comm.setRequestProperty("User-Agent", "Cluv-AppFaculdade/1.0 (com.example.cluv)");

            int codigo = comm.getResponseCode();

            // em erro (400, 429...) o texto da resposta vem no "errorStream"
            BufferedReader leitor;
            if (codigo == 200) {
                leitor = new BufferedReader(new InputStreamReader(comm.getInputStream()));
            } else {
                leitor = new BufferedReader(new InputStreamReader(comm.getErrorStream()));
            }
            String resposta = "";
            String linha;
            while ((linha = leitor.readLine()) != null) {
                resposta += linha;
            }
            leitor.close();

            Rota rota = lerJson(resposta);
            if (rota == null && codigo != 200) {
                throw new Exception("o servidor respondeu HTTP " + codigo);
            }
            return rota;
        } finally {
            if (comm != null) {
                comm.disconnect();
            }
        }
    }

    private static Rota lerJson(String texto) throws Exception {
        JSONObject json = new JSONObject(texto);
        String codigo = json.optString("code");
        if (codigo.equals("NoRoute") || codigo.equals("NoSegment")) {
            return null;   // o servidor entendeu, mas nao existe caminho ate la
        }
        if (!codigo.equals("Ok")) {
            throw new Exception("resposta do servidor: " + codigo);
        }

        JSONObject primeira = json.getJSONArray("routes").getJSONObject(0);
        Rota rota = new Rota();
        rota.distanciaM = primeira.getDouble("distance");
        rota.duracaoS = primeira.getDouble("duration");

        // cada ponto vem como [longitude, latitude]
        JSONArray coordenadas = primeira.getJSONObject("geometry").getJSONArray("coordinates");
        for (int i = 0; i < coordenadas.length(); i++) {
            JSONArray ponto = coordenadas.getJSONArray(i);
            rota.pontos.add(new GeoPoint(ponto.getDouble(1), ponto.getDouble(0)));
        }
        return rota;
    }
}
