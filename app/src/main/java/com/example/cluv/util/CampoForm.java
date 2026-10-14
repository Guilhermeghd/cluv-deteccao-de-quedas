package com.example.cluv.util;

import com.example.cluv.R;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;



public class CampoForm {

    public static void mostrarErro(EditText campo, TextView txtErro, String mensagem) {
        campo.setBackgroundResource(R.drawable.bg_campo_form_erro);
        txtErro.setText(mensagem);
        txtErro.setVisibility(View.VISIBLE);
    }

    public static void limparErro(EditText campo, TextView txtErro) {
        campo.setBackgroundResource(R.drawable.bg_campo_form);
        txtErro.setVisibility(View.GONE);
    }

    public static void alternarSenha(EditText campo, ImageButton olho) {
        boolean escondida = campo.getTransformationMethod() instanceof PasswordTransformationMethod;
        if (escondida) {
            campo.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            olho.setImageResource(R.drawable.ic_olho_fechado);
        } else {
            campo.setTransformationMethod(PasswordTransformationMethod.getInstance());
            olho.setImageResource(R.drawable.ic_olho);
        }
        campo.setSelection(campo.getText().length());   // o cursor nao pula para o comeco
    }

    public static void pintarBotao(Button botao, boolean preenchido) {
        if (preenchido) {
            botao.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0E7C8B")));
            botao.setTextColor(Color.WHITE);
        } else {
            botao.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E6EAEF")));
            botao.setTextColor(Color.parseColor("#5A6572"));
        }
    }
}
