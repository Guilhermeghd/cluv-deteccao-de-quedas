package com.example.cluv.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.HashMap;
import java.util.Map;


public class Tema implements SensorEventListener {

    private static final float LIMIAR_LUZ = 50f;   // lux: abaixo disso fica escuro

    /** Estado atual, igual para todas as telas (a tela nova ja abre no tema certo). */
    public static boolean escuro = false;

    // cor clara -> cor escura (fundos e textos separados, porque o branco do texto nao muda)
    private static final Map<Integer, Integer> FUNDO_ESCURO = new HashMap<>();
    private static final Map<Integer, Integer> FUNDO_CLARO = new HashMap<>();
    private static final Map<Integer, Integer> TEXTO_ESCURO = new HashMap<>();
    private static final Map<Integer, Integer> TEXTO_CLARO = new HashMap<>();

    static {
        par(FUNDO_ESCURO, FUNDO_CLARO, "#FFFFFF", "#1A2027");   // cards e barra de abas
        par(FUNDO_ESCURO, FUNDO_CLARO, "#F2F4F7", "#0F1317");   // fundo da tela
        par(FUNDO_ESCURO, FUNDO_CLARO, "#E6EAEF", "#2A323B");   // divisores e chips cinza
        par(FUNDO_ESCURO, FUNDO_CLARO, "#DDE2E8", "#313A44");
        par(FUNDO_ESCURO, FUNDO_CLARO, "#E8EEF3", "#222A33");
        par(FUNDO_ESCURO, FUNDO_CLARO, "#D6EDF0", "#14363B");   // chip verde-azulado
        par(FUNDO_ESCURO, FUNDO_CLARO, "#E3F2E5", "#1C3320");   // chip verde
        par(FUNDO_ESCURO, FUNDO_CLARO, "#FEF3C7", "#3A2F10");   // aviso amarelo
        par(FUNDO_ESCURO, FUNDO_CLARO, "#FDECEA", "#3A1B1B");   // aviso vermelho

        par(TEXTO_ESCURO, TEXTO_CLARO, "#1A1F24", "#E6E9ED");   // texto principal
        par(TEXTO_ESCURO, TEXTO_CLARO, "#5A6572", "#A3AEBA");   // texto secundario e icones
        par(TEXTO_ESCURO, TEXTO_CLARO, "#6B7683", "#93A0AD");
        par(TEXTO_ESCURO, TEXTO_CLARO, "#3A4651", "#C5CED8");
        par(TEXTO_ESCURO, TEXTO_CLARO, "#0B6672", "#5BC0CE");   // aba selecionada
        par(TEXTO_ESCURO, TEXTO_CLARO, "#0B5560", "#8FDCE6");   // texto sobre o chip verde-azulado
        par(TEXTO_ESCURO, TEXTO_CLARO, "#1B5E20", "#9AD79D");   // texto sobre o chip verde
        par(TEXTO_ESCURO, TEXTO_CLARO, "#2E7D32", "#6CC070");   // "online" e afins
        par(TEXTO_ESCURO, TEXTO_CLARO, "#6B2E07", "#F2D58A");   // texto sobre o aviso amarelo
        par(TEXTO_ESCURO, TEXTO_CLARO, "#B71C1C", "#F2A7A3");   // texto sobre o aviso vermelho
    }

    private static void par(Map<Integer, Integer> paraEscuro, Map<Integer, Integer> paraClaro,
                            String claro, String escuro) {
        int c = Color.parseColor(claro);
        int e = Color.parseColor(escuro);
        paraEscuro.put(c, e);
        paraClaro.put(e, c);
    }

    private final Activity tela;
    private SensorManager sensorManager;
    private Sensor sensorLuz;

    public Tema(Activity tela) {
        this.tela = tela;
        sensorManager = (SensorManager) tela.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            sensorLuz = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }
    }

    public void ligar() {
        if (sensorLuz != null) {
            sensorManager.registerListener(this, sensorLuz, SensorManager.SENSOR_DELAY_NORMAL);
        }
        aplicar();
    }

    public void desligar() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (atualizar(event.values[0])) {
            aplicar();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /** Guarda o novo estado pela luz. Devolve true se o tema mudou (claro para escuro ou o contrario). */
    public static boolean atualizar(float lux) {
        boolean novoEscuro = lux < LIMIAR_LUZ;
        boolean mudou = novoEscuro != escuro;
        escuro = novoEscuro;
        return mudou;
    }

    // ============================================================
    //  PINTAR A TELA
    // ============================================================

    /** Atalho para quem so quer repintar (ex.: fragments que criaram views novas). */
    public static void aplicarEm(Activity tela) {
        new Tema(tela).aplicar();
    }

    /** Pinta so uma parte (ex.: a janela de teste de sensores, que nao e filha da tela). */
    public static void aplicarNaView(View raiz) {
        pintar(raiz);
    }

    /** Pinta a tela inteira com o tema atual. Pode chamar quantas vezes quiser. */
    public void aplicar() {
        WindowInsetsControllerCompat barras =
                WindowCompat.getInsetsController(tela.getWindow(), tela.getWindow().getDecorView());
        barras.setAppearanceLightStatusBars(!escuro);
        barras.setAppearanceLightNavigationBars(!escuro);

        pintar(tela.findViewById(android.R.id.content));
    }

    private static void pintar(View view) {
        Map<Integer, Integer> fundos = escuro ? FUNDO_ESCURO : FUNDO_CLARO;
        Map<Integer, Integer> textos = escuro ? TEXTO_ESCURO : TEXTO_CLARO;

        // fundo de cor lisa
        Drawable fundo = view.getBackground();
        if (fundo instanceof ColorDrawable) {
            Integer nova = fundos.get(((ColorDrawable) fundo).getColor());
            if (nova != null) {
                view.setBackgroundColor(nova);
            }
        }

        // fundo com tint (os "chips" arredondados)
        ColorStateList tintFundo = view.getBackgroundTintList();
        if (tintFundo != null) {
            Integer nova = fundos.get(tintFundo.getDefaultColor());
            if (nova != null) {
                view.setBackgroundTintList(ColorStateList.valueOf(nova));
            }
        }

        if (view instanceof TextView) {
            TextView texto = (TextView) view;
            Integer nova = textos.get(texto.getCurrentTextColor());
            if (nova != null) {
                texto.setTextColor(nova);
            }
            // icones das abas
            ColorStateList tintIcone = texto.getCompoundDrawableTintList();
            if (tintIcone != null) {
                Integer novaCor = textos.get(tintIcone.getDefaultColor());
                if (novaCor != null) {
                    texto.setCompoundDrawableTintList(ColorStateList.valueOf(novaCor));
                }
            }
        }

        if (view instanceof ImageView) {
            ImageView imagem = (ImageView) view;
            ColorStateList tintImagem = imagem.getImageTintList();
            if (tintImagem != null) {
                Integer nova = textos.get(tintImagem.getDefaultColor());
                if (nova != null) {
                    imagem.setImageTintList(ColorStateList.valueOf(nova));
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup grupo = (ViewGroup) view;
            for (int i = 0; i < grupo.getChildCount(); i++) {
                pintar(grupo.getChildAt(i));
            }
        }
    }
}
