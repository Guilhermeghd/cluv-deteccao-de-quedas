package com.example.cluv.util;

import android.text.Editable;
import android.text.TextWatcher;

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
