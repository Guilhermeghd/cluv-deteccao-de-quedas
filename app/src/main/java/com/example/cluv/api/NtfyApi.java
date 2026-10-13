package com.example.cluv.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Manda o aviso para os contatos de confianca pelo ntfy (https://ntfy.sh): servico gratuito de notificacao push.
 * Quem instalou o app ntfy e assinou o topico do usuario recebe a notificacao no celular.
 * O envio e um POST com JSON (assim os acentos funcionam). Mesma logica da Api: HttpURLConnection.
 * ATENCAO: rede nao pode rodar na thread principal, entao chame dentro de um executor.execute(...)
 */
public class NtfyApi {

    private static final String URL_NTFY = "https://ntfy.sh";

    /**
     * Devolve true se o ntfy aceitou a mensagem.
     * prioridade: 1 (minima) a 5 (urgente, toca alto). tag: nome de um emoji do ntfy (ex.: "rotating_light").
     * linkAoToque: pagina que abre quando a pessoa toca na notificacao (pode ser vazio).
     */
    public static boolean enviar(String topico, String titulo, String mensagem, int prioridade,
                                 String tag, String linkAoToque) {
        HttpURLConnection comm = null;
        boolean deuCerto = false;

        try {
            JSONObject json = new JSONObject();
            json.put("topic", topico);
            json.put("title", titulo);
            json.put("message", mensagem);
            json.put("priority", prioridade);
            json.put("tags", new JSONArray().put(tag));
            if (linkAoToque != null && !linkAoToque.isEmpty()) {
                json.put("click", linkAoToque);
            }

            URL url = new URL(URL_NTFY);
            comm = (HttpURLConnection) url.openConnection();
            comm.setRequestMethod("POST");
            comm.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            comm.setConnectTimeout(5000);
            comm.setReadTimeout(5000);
            comm.setDoOutput(true);

            OutputStream os = comm.getOutputStream();
            os.write(json.toString().getBytes(StandardCharsets.UTF_8));
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
        return deuCerto;
    }
}
