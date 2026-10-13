package com.example.cluv.util;

import com.example.cluv.R;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.TextView;

/**
 * A barra de abas de baixo (layout barra_abas.xml) fica FIXA na MainActivity.
 * Clicar numa aba nao abre outra tela: a MainActivity so troca o conteudo que aparece em cima da barra.
 * Aqui ficam os numeros das abas e a pintura da aba atual.
 */
public class BarraAbas {

    public static final int INICIO = 0;
    public static final int TRAJETO = 1;
    public static final int CLIMA = 2;
    public static final int HISTORICO = 3;
    public static final int PERFIL = 4;

    public static final int[] IDS_ABAS = {
            R.id.tabInicio, R.id.tabTrajeto, R.id.tabClima, R.id.tabHistorico, R.id.tabPerfil
    };

    /** Pinta a aba atual (colorida e em negrito) e deixa as outras cinza. */
    public static void marcar(Activity tela, int abaAtual) {
        for (int i = 0; i < IDS_ABAS.length; i++) {
            TextView botao = tela.findViewById(IDS_ABAS[i]);
            int cor;
            if (i == abaAtual) {
                cor = Color.parseColor("#0B6672");
                botao.setTypeface(null, Typeface.BOLD);
            } else {
                cor = Color.parseColor("#5A6572");
                botao.setTypeface(null, Typeface.NORMAL);
            }
            botao.setTextColor(cor);
            botao.setCompoundDrawableTintList(ColorStateList.valueOf(cor));
        }
    }
}
