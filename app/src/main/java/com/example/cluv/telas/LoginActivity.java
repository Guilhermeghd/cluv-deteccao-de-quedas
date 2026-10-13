package com.example.cluv.telas;

import com.example.cluv.R;
import com.example.cluv.dados.Sessao;
import com.example.cluv.dados.UsuarioDbHelper;
import com.example.cluv.modelo.Usuario;
import com.example.cluv.util.AoDigitar;
import com.example.cluv.util.CampoForm;
import com.example.cluv.util.Tema;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private EditText editEmail, editSenha;
    private TextView txtErroEmail, txtErroSenha, txtErroLogin, btnIrCadastro;
    private View bannerErroLogin;
    private Button btnEntrar;
    private ImageButton btnOlhoLogin;

    private UsuarioDbHelper banco;
    private Tema tema;   // claro/escuro pelo sensor de luz

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Se ja tem alguem logado, pula direto para a tela principal
        if (Sessao.logado(this)) {
            abrirPrincipal();
            return;
        }

        setContentView(R.layout.activity_login);

        // fundo claro: icones da barra de status escuros
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        banco = new UsuarioDbHelper(this);
        tema = new Tema(this);

        editEmail = findViewById(R.id.editLoginEmail);
        editSenha = findViewById(R.id.editLoginSenha);
        txtErroEmail = findViewById(R.id.txtErroEmailLogin);
        txtErroSenha = findViewById(R.id.txtErroSenhaLogin);
        txtErroLogin = findViewById(R.id.txtErroLogin);
        bannerErroLogin = findViewById(R.id.bannerErroLogin);
        btnEntrar = findViewById(R.id.btnEntrar);
        btnIrCadastro = findViewById(R.id.btnIrCadastro);
        btnOlhoLogin = findViewById(R.id.btnOlhoLogin);

        btnEntrar.setOnClickListener(v -> entrar());
        btnIrCadastro.setOnClickListener(v -> startActivity(new Intent(this, CadastroActivity.class)));
        btnOlhoLogin.setOnClickListener(v -> CampoForm.alternarSenha(editSenha, btnOlhoLogin));

        // ao digitar: some o erro daquele campo e o botao fica verde-azulado quando os dois estao preenchidos
        editEmail.addTextChangedListener(new AoDigitar(() -> {
            CampoForm.limparErro(editEmail, txtErroEmail);
            atualizarBotao();
        }));
        editSenha.addTextChangedListener(new AoDigitar(() -> {
            CampoForm.limparErro(editSenha, txtErroSenha);
            bannerErroLogin.setVisibility(View.GONE);
            atualizarBotao();
        }));
        atualizarBotao();
    }

    private void atualizarBotao() {
        boolean preenchido = !editEmail.getText().toString().trim().isEmpty()
                && !editSenha.getText().toString().isEmpty();
        CampoForm.pintarBotao(btnEntrar, preenchido);
        tema.aplicar();   // se estiver no tema escuro, o Tema ajusta as cores do botao
    }

    private void entrar() {
        String email = editEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
        String senha = editSenha.getText().toString();

        // valida os dois campos e mostra o que faltar
        boolean ok = true;
        if (email.isEmpty()) {
            CampoForm.mostrarErro(editEmail, txtErroEmail, "Informe o e-mail");
            editEmail.requestFocus();
            ok = false;
        }
        if (senha.isEmpty()) {
            CampoForm.mostrarErro(editSenha, txtErroSenha, "Informe a senha");
            if (ok) {
                editSenha.requestFocus();
            }
            ok = false;
        }
        if (!ok) {
            tema.aplicar();
            return;
        }

        Usuario usuario = banco.autenticar(email, senha);
        if (usuario == null) {
            // a mensagem e a mesma para e-mail errado e senha errada, de proposito
            bannerErroLogin.setVisibility(View.VISIBLE);
            editSenha.setText("");
            tema.aplicar();
            return;
        }

        Sessao.entrar(this, usuario);
        abrirPrincipal();
    }

    private void abrirPrincipal() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tema != null) {
            tema.ligar();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tema != null) {
            tema.desligar();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (banco != null) {
            banco.close();
        }
    }
}
