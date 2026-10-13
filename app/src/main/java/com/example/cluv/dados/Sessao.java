package com.example.cluv.dados;

import com.example.cluv.modelo.Usuario;

import android.content.Context;
import android.content.SharedPreferences;

/** Guarda quem esta logado, para o app nao pedir login toda vez que abre. */
public class Sessao {

    private static final String PREFS = "cluv_sessao";
    private static final String CHAVE_ID = "usuario_id";
    private static final String CHAVE_NOME = "usuario_nome";
    private static final String CHAVE_EMAIL = "usuario_email";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void entrar(Context context, Usuario usuario) {
        prefs(context).edit()
                .putLong(CHAVE_ID, usuario.getId())
                .putString(CHAVE_NOME, usuario.getNome())
                .putString(CHAVE_EMAIL, usuario.getEmail())
                .apply();
    }

    /** Troca so o nome guardado na sessao (o usuario continua logado). */
    public static void atualizarNome(Context context, String nome) {
        prefs(context).edit().putString(CHAVE_NOME, nome).apply();
    }

    public static boolean logado(Context context) {
        return prefs(context).getLong(CHAVE_ID, -1) != -1;
    }

    public static long id(Context context) {
        return prefs(context).getLong(CHAVE_ID, -1);
    }

    public static String nome(Context context) {
        return prefs(context).getString(CHAVE_NOME, "");
    }

    public static String email(Context context) {
        return prefs(context).getString(CHAVE_EMAIL, "");
    }

    public static void sair(Context context) {
        prefs(context).edit().clear().apply();
    }
}
