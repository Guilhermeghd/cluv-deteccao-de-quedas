package com.example.cluv.api;

import com.example.cluv.modelo.Rota;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;


public class RotaApi {

    private static final String URL_BASE = "https://routing.openstreetmap.de/";

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
            comm.setRequestProperty("User-Agent", "Cluv-AppFaculdade/1.0 (com.example.cluv)");

            int codigo = comm.getResponseCode();

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
            return null;
        }
        if (!codigo.equals("Ok")) {
            throw new Exception("resposta do servidor: " + codigo);
        }

        JSONObject primeira = json.getJSONArray("routes").getJSONObject(0);
        Rota rota = new Rota();
        rota.distanciaM = primeira.getDouble("distance");
        rota.duracaoS = primeira.getDouble("duration");

        JSONArray coordenadas = primeira.getJSONObject("geometry").getJSONArray("coordinates");
        for (int i = 0; i < coordenadas.length(); i++) {
            JSONArray ponto = coordenadas.getJSONArray(i);
            rota.pontos.add(new GeoPoint(ponto.getDouble(1), ponto.getDouble(0)));
        }
        return rota;
    }
}
