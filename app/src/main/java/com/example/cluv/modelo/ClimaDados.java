package com.example.cluv.modelo;

import com.example.cluv.R;

import org.json.JSONObject;


public class ClimaDados {

    public static ClimaDados ultimo = null;
    public static long ultimoEmMs = 0;

    public int idCondicao;
    public String icone = "";
    public String descricao = "";
    public String cidade = "";
    public double temperatura, sensacao, minima, maxima;
    public double ventoKmh = 0;
    public double rajadaKmh = 0;
    public double chuvaMmH = 0;
    public int umidade, pressao, nuvens;
    public int visibilidadeM = -1;
    public String nascer = "--:--";
    public String por = "--:--";

    public int nivelRisco = 0;
    public String motivosRisco = "";

    public int iconeDrawable() {
        boolean noite = icone.endsWith("n");

        if (idCondicao >= 200 && idCondicao < 300) {
            return R.drawable.ic_clima_tempestade;
        } else if (idCondicao >= 300 && idCondicao < 600) {
            return R.drawable.ic_clima_chuva;
        } else if (idCondicao >= 600 && idCondicao < 700) {
            return R.drawable.ic_clima_neve;
        } else if (idCondicao >= 700 && idCondicao < 800) {
            return R.drawable.ic_clima_neblina;
        } else if (idCondicao == 800) {
            if (noite) {
                return R.drawable.ic_clima_lua;
            }
            return R.drawable.ic_clima_sol;
        } else if (idCondicao == 801 || idCondicao == 802) {
            if (noite) {
                return R.drawable.ic_clima_nuvem;
            }
            return R.drawable.ic_clima;
        }
        return R.drawable.ic_clima_nuvem;
    }

    public static ClimaDados lerJson(String texto) throws Exception {
        JSONObject json = new JSONObject(texto);
        ClimaDados c = new ClimaDados();

        JSONObject tempo = json.getJSONArray("weather").getJSONObject(0);
        c.idCondicao = tempo.getInt("id");
        c.icone = tempo.optString("icon", "");
        c.descricao = tempo.optString("description", "");
        if (!c.descricao.isEmpty()) {
            c.descricao = c.descricao.substring(0, 1).toUpperCase() + c.descricao.substring(1);
        }

        c.cidade = json.optString("name", "");

        JSONObject principal = json.getJSONObject("main");
        c.temperatura = principal.getDouble("temp");
        c.sensacao = principal.optDouble("feels_like", c.temperatura);
        c.minima = principal.optDouble("temp_min", c.temperatura);
        c.maxima = principal.optDouble("temp_max", c.temperatura);
        c.umidade = principal.optInt("humidity", 0);
        c.pressao = principal.optInt("pressure", 0);

        JSONObject vento = json.optJSONObject("wind");
        if (vento != null) {
            c.ventoKmh = vento.optDouble("speed", 0) * 3.6;
            c.rajadaKmh = vento.optDouble("gust", 0) * 3.6;
        }

        JSONObject chuva = json.optJSONObject("rain");
        if (chuva != null) {
            c.chuvaMmH = chuva.optDouble("1h", 0);
        }

        JSONObject ceu = json.optJSONObject("clouds");
        if (ceu != null) {
            c.nuvens = ceu.optInt("all", 0);
        }

        c.visibilidadeM = json.optInt("visibility", -1);

        JSONObject sys = json.optJSONObject("sys");
        int fuso = json.optInt("timezone", 0);
        if (sys != null) {
            c.nascer = hora(sys.optLong("sunrise", 0), fuso);
            c.por = hora(sys.optLong("sunset", 0), fuso);
        }

        c.calcularRisco();
        return c;
    }

    private static String hora(long segundos, int fuso) {
        if (segundos == 0) {
            return "--:--";
        }
        long minutosDoDia = ((segundos + fuso) % 86400) / 60;
        return String.format("%02d:%02d", minutosDoDia / 60, minutosDoDia % 60);
    }

    public void calcularRisco() {
        nivelRisco = 0;
        motivosRisco = "";

        if (idCondicao >= 200 && idCondicao < 300) {
            nivelRisco = 2;
            motivosRisco += "Trovoada: evite sair agora.\n";
        } else if (chuvaMmH >= 7.5) {
            nivelRisco = 2;
            motivosRisco += "Chuva forte: pista escorregadia.\n";
        } else if (idCondicao >= 300 && idCondicao < 600) {
            nivelRisco = Math.max(nivelRisco, 1);
            motivosRisco += "Chuva: pista molhada e visibilidade reduzida.\n";
        }

        if (ventoKmh >= 60) {
            nivelRisco = 2;
            motivosRisco += "Vento de " + Math.round(ventoKmh) + " km/h: perigoso para moto e bicicleta.\n";
        } else if (ventoKmh >= 40) {
            nivelRisco = Math.max(nivelRisco, 1);
            motivosRisco += "Vento de " + Math.round(ventoKmh) + " km/h: redobre o cuidado.\n";
        }

        if (visibilidadeM >= 0 && visibilidadeM < 1000) {
            nivelRisco = 2;
            motivosRisco += "Visibilidade muito baixa: " + visibilidadeM + " m.\n";
        } else if (visibilidadeM >= 0 && visibilidadeM < 2000) {
            nivelRisco = Math.max(nivelRisco, 1);
            motivosRisco += "Visibilidade reduzida.\n";
        }

        // neve
        if (idCondicao >= 600 && idCondicao < 700) {
            nivelRisco = Math.max(nivelRisco, 1);
            motivosRisco += "Neve ou gelo: pista escorregadia.\n";
        }

        if (motivosRisco.isEmpty()) {
            motivosRisco = "Sem chuva e com vento fraco. Bom para sair.";
        } else {
            motivosRisco = motivosRisco.trim();   // tira o ultimo \n
        }
    }
}
