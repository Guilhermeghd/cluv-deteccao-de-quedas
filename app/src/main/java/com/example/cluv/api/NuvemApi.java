package com.example.cluv.api;

import com.example.cluv.dados.Sessao;
import com.example.cluv.modelo.RegistroNuvem;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.Html;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class NuvemApi {

    private static final String URL_PAINEL = "https://ideaberta.com.br/socorro/";
    private static final int FILTRO_QUEDAS = 2;
    private static final int MAX_PAGINAS = 8;
    private static final int MAX_REGISTROS = 30;

    private static final Pattern P_ID = Pattern.compile("#(\\d+)</td>");
    private static final Pattern P_DATA = Pattern.compile("font-monospace text-dark\">([^<]+)</td>");
    private static final Pattern P_USUARIO = Pattern.compile("<strong class=\"text-dark\">([^<]*)</strong>");
    private static final Pattern P_MODELO = Pattern.compile("small fw-semibold text-dark\">([^<]+)</div>");
    private static final Pattern P_DEVICE = Pattern.compile("ID:\\s*([0-9A-Za-z\\-]+)");
    private static final Pattern P_BATERIA = Pattern.compile("badge-bateria[^>]*>\\s*<i[^>]*></i>\\s*(\\d+)%");
    private static final Pattern P_POSICAO = Pattern.compile("abrirModalMapa\\((-?[0-9.]+),\\s*(-?[0-9.]+)");
    private static final Pattern P_QUEDA = Pattern.compile("Queda:\\s*([0-9.]+)\\s*G");
    private static final Pattern P_VELOCIDADE = Pattern.compile("([0-9.]+)\\s*km/h");
    private static final Pattern P_TEMPERATURA = Pattern.compile("(-?[0-9.]+)\\s*°C");
    private static final Pattern P_PRESSAO = Pattern.compile("([0-9.]+)\\s*hPa");

    public static List<RegistroNuvem> buscarMeusAlertas(Context context) throws Exception {
        String meuAparelho = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String meuNome = Sessao.nome(context);

        List<RegistroNuvem> meus = new ArrayList<>();

        for (int pagina = 1; pagina <= MAX_PAGINAS; pagina++) {
            String html = baixarPagina(pagina);
            List<RegistroNuvem> daPagina = lerLinhas(html);

            if (daPagina.isEmpty()) {
                break;
            }
            for (RegistroNuvem r : daPagina) {
                if (ehMeu(r, meuAparelho, meuNome)) {
                    meus.add(r);
                }
            }
            if (meus.size() >= MAX_REGISTROS) {
                break;
            }
        }
        return meus;
    }

    private static boolean ehMeu(RegistroNuvem r, String meuAparelho, String meuNome) {
        if (meuAparelho == null || r.deviceId.isEmpty()) {
            return false;
        }
        boolean mesmoAparelho = meuAparelho.startsWith(r.deviceId) || r.deviceId.startsWith(meuAparelho);
        boolean mesmoNome = r.usuario.equalsIgnoreCase(meuNome);
        return mesmoAparelho && mesmoNome;
    }

    private static String baixarPagina(int pagina) throws Exception {
        URL url = new URL(URL_PAINEL + "?filtro=" + FILTRO_QUEDAS + "&pagina=" + pagina);
        HttpURLConnection comm = null;
        try {
            comm = (HttpURLConnection) url.openConnection();
            comm.setRequestMethod("GET");
            comm.setRequestProperty("User-Agent", "Cluv-AppFaculdade/1.0 (com.example.cluv)");
            comm.setConnectTimeout(8000);
            comm.setReadTimeout(15000);

            int codigo = comm.getResponseCode();
            if (codigo != 200) {
                throw new Exception("o servidor respondeu HTTP " + codigo);
            }

            BufferedReader leitor = new BufferedReader(
                    new InputStreamReader(comm.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder texto = new StringBuilder();
            String linha;
            while ((linha = leitor.readLine()) != null) {
                texto.append(linha).append('\n');
            }
            leitor.close();
            return texto.toString();
        } finally {
            if (comm != null) {
                comm.disconnect();
            }
        }
    }

    private static List<RegistroNuvem> lerLinhas(String html) {
        List<RegistroNuvem> lista = new ArrayList<>();

        int inicioTabela = html.indexOf("<tbody");
        if (inicioTabela < 0) {
            return lista;
        }
        String[] linhas = html.substring(inicioTabela).split("<tr class=\"");

        for (int i = 1; i < linhas.length; i++) {
            String linha = linhas[i];

            RegistroNuvem r = new RegistroNuvem();
            String id = primeiro(P_ID, linha);
            if (id == null) {
                continue;
            }
            r.id = Integer.parseInt(id);
            r.dataHora = textoLimpo(primeiro(P_DATA, linha));
            r.dataMs = lerHora(r.dataHora);
            r.usuario = textoLimpo(primeiro(P_USUARIO, linha));
            r.modelo = textoLimpo(primeiro(P_MODELO, linha));
            r.deviceId = textoLimpo(primeiro(P_DEVICE, linha));

            String bateria = primeiro(P_BATERIA, linha);
            if (bateria != null) {
                r.bateria = Integer.parseInt(bateria);
            }

            Matcher posicao = P_POSICAO.matcher(linha);
            if (posicao.find()) {
                r.latitude = Double.parseDouble(posicao.group(1));
                r.longitude = Double.parseDouble(posicao.group(2));
                r.temPosicao = true;
            }

            r.nivelQueda = numero(primeiro(P_QUEDA, linha));
            r.velocidade = numero(primeiro(P_VELOCIDADE, linha));
            r.temperatura = numero(primeiro(P_TEMPERATURA, linha));
            r.pressao = numero(primeiro(P_PRESSAO, linha));

            lista.add(r);
        }
        return lista;
    }

    private static long lerHora(String texto) {
        try {
            Date data = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).parse(texto);
            if (data != null) {
                return data.getTime();
            }
        } catch (Exception e) {
        }
        return 0;
    }

    private static String primeiro(Pattern padrao, String texto) {
        Matcher m = padrao.matcher(texto);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static float numero(String texto) {
        if (texto == null) {
            return Float.NaN;
        }
        try {
            return Float.parseFloat(texto);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private static String textoLimpo(String texto) {
        if (texto == null) {
            return "";
        }
        return Html.fromHtml(texto, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }
}
