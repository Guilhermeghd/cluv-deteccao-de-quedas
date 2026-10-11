package com.example.cluv;

import android.text.Editable;
import android.text.TextWatcher;

/** Roda uma acao toda vez que o texto de um campo muda (assim nao precisa escrever os 3 metodos do TextWatcher). */
public class AoDigitar implements TextWatcher {

    private final Runnable acao;

    public AoDigitar(Runnable acao) {
        this.acao = acao;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int inicio, int contagem, int depois) {
    }

    @Override
    public void onTextChanged(CharSequence s, int inicio, int antes, int contagem) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        acao.run();
    }
}
