package com.example.cluv.dados;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

/**
 * As configuracoes que o usuario muda na tela Perfil. Ficam no SharedPreferences (igual a Sessao),
 * uma de cada usuario (a chave leva o id dele).
 */
public class Config {

    private static final String PREFS = "cluv_config";

    // padrao e limites
    public static final int CHECKIN_PADRAO_MIN = 15;
    public static final int CHECKIN_MAX_MIN = 180;
    public static final int CONTAGEM_PADRAO_S = 30;
    public static final int CONTAGEM_MIN_S = 10;
    public static final int CONTAGEM_MAX_S = 60;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Avisar os contatos de confianca (pelo ntfy) quando um alerta for enviado? Ja vem ligado. */
    public static boolean avisarContatos(Context context) {
        return prefs(context).getBoolean("avisar_contatos_" + Sessao.id(context), true);
    }

    public static void salvarAvisarContatos(Context context, boolean ligado) {
        prefs(context).edit().putBoolean("avisar_contatos_" + Sessao.id(context), ligado).apply();
    }

    /**
     * O "canal" do usuario no ntfy. Os contatos assinam este topico no app ntfy e recebem os alertas.
     * Ele e criado na primeira vez, com letras e numeros ao acaso: quem souber o nome consegue ler os avisos,
     * entao o nome funciona como uma senha (por isso e longo e aleatorio).
     */
    public static String topicoNtfy(Context context) {
        String chave = "ntfy_topico_" + Sessao.id(context);
        String topico = prefs(context).getString(chave, null);
        if (topico == null) {
            String letras = "abcdefghijklmnopqrstuvwxyz0123456789";
            SecureRandom sorteio = new SecureRandom();
            StringBuilder nome = new StringBuilder("cluv-");
            for (int i = 0; i < 16; i++) {
                nome.append(letras.charAt(sorteio.nextInt(letras.length())));
            }
            topico = nome.toString();
            prefs(context).edit().putString(chave, topico).apply();
        }
        return topico;
    }

    /** Apaga o topico: o proximo pedido cria um novo (quem tinha o antigo deixa de receber). */
    public static void trocarTopicoNtfy(Context context) {
        prefs(context).edit().remove("ntfy_topico_" + Sessao.id(context)).apply();
    }

    /** O check-in de seguranca esta ligado? Ja vem ligado. */
    public static boolean checkinLigado(Context context) {
        return prefs(context).getBoolean("checkin_ligado_" + Sessao.id(context), true);
    }

    public static void salvarCheckinLigado(Context context, boolean ligado) {
        prefs(context).edit().putBoolean("checkin_ligado_" + Sessao.id(context), ligado).apply();
    }

    /** De quantos em quantos minutos o check-in pergunta "esta tudo bem?". */
    public static int checkinMinutos(Context context) {
        return prefs(context).getInt("checkin_min_" + Sessao.id(context), CHECKIN_PADRAO_MIN);
    }

    public static void salvarCheckinMinutos(Context context, int minutos) {
        prefs(context).edit().putInt("checkin_min_" + Sessao.id(context), minutos).apply();
    }

    /** Como a pessoa se desloca no trajeto: 0 = a pe, 1 = bicicleta, 2 = moto. */
    public static int meioTransporte(Context context) {
        return prefs(context).getInt("meio_" + Sessao.id(context), 0);
    }

    public static void salvarMeioTransporte(Context context, int meio) {
        prefs(context).edit().putInt("meio_" + Sessao.id(context), meio).apply();
    }

    /** Quantos segundos a contagem regressiva da queda espera antes de enviar o alerta. */
    public static int contagemQueda(Context context) {
        return prefs(context).getInt("contagem_s_" + Sessao.id(context), CONTAGEM_PADRAO_S);
    }

    public static void salvarContagemQueda(Context context, int segundos) {
        prefs(context).edit().putInt("contagem_s_" + Sessao.id(context), segundos).apply();
    }
}
