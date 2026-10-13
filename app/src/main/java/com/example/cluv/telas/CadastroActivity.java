package com.example.cluv.telas;

import com.example.cluv.R;
import com.example.cluv.dados.Sessao;
import com.example.cluv.dados.UsuarioDbHelper;
import com.example.cluv.modelo.Usuario;
import com.example.cluv.util.AoDigitar;
import com.example.cluv.util.CampoForm;
import com.example.cluv.util.Tema;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import java.util.Locale;

public class CadastroActivity extends AppCompatActivity {

    private EditText editNome, editEmail, editSenha, editConfirmar;
    private TextView txtErroNome, txtErroEmail, txtErroSenha, txtErroConfirmar;
    private TextView txtRegraTamanho, txtRegraIguais, btnVoltarLogin;
    private Button btnCadastrar;
    private ImageButton btnOlhoSenha, btnOlhoConfirmar;

    private UsuarioDbHelper banco;
    private Tema tema;   // claro/escuro pelo sensor de luz

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro);

        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        banco = new UsuarioDbHelper(this);
        tema = new Tema(this);

        editNome = findViewById(R.id.editCadNome);
        editEmail = findViewById(R.id.editCadEmail);
        editSenha = findViewById(R.id.editCadSenha);
        editConfirmar = findViewById(R.id.editCadConfirmar);
        txtErroNome = findViewById(R.id.txtErroNome);
        txtErroEmail = findViewById(R.id.txtErroEmail);
        txtErroSenha = findViewById(R.id.txtErroSenha);
        txtErroConfirmar = findViewById(R.id.txtErroConfirmar);
        txtRegraTamanho = findViewById(R.id.txtRegraTamanho);
        txtRegraIguais = findViewById(R.id.txtRegraIguais);
        btnCadastrar = findViewById(R.id.btnCadastrar);
        btnVoltarLogin = findViewById(R.id.btnVoltarLogin);
        btnOlhoSenha = findViewById(R.id.btnOlhoSenha);
        btnOlhoConfirmar = findViewById(R.id.btnOlhoConfirmar);

        btnCadastrar.setOnClickListener(v -> cadastrar());
        btnVoltarLogin.setOnClickListener(v -> finish());
        btnOlhoSenha.setOnClickListener(v -> CampoForm.alternarSenha(editSenha, btnOlhoSenha));
        btnOlhoConfirmar.setOnClickListener(v -> CampoForm.alternarSenha(editConfirmar, btnOlhoConfirmar));

        // ao digitar: some o erro do campo, as regras da senha acompanham e o botao muda de cor
        editNome.addTextChangedListener(new AoDigitar(() -> {
            CampoForm.limparErro(editNome, txtErroNome);
            atualizarTela();
        }));
        editEmail.addTextChangedListener(new AoDigitar(() -> {
            CampoForm.limparErro(editEmail, txtErroEmail);
            atualizarTela();
        }));
        editSenha.addTextChangedListener(new AoDigitar(() -> {
            CampoForm.limparErro(editSenha, txtErroSenha);
            atualizarTela();
        }));
        editConfirmar.addTextChangedListener(new AoDigitar(() -> {
            CampoForm.limparErro(editConfirmar, txtErroConfirmar);
            atualizarTela();
        }));
        atualizarTela();
    }

    /** Atualiza as duas regras da senha (verde quando cumpridas) e a cor do botao. */
    private void atualizarTela() {
        String senha = editSenha.getText().toString();
        String confirmar = editConfirmar.getText().toString();

        pintarRegra(txtRegraTamanho, senha.length() >= 6);
        pintarRegra(txtRegraIguais, !senha.isEmpty() && senha.equals(confirmar));

        boolean preenchido = !editNome.getText().toString().trim().isEmpty()
                && !editEmail.getText().toString().trim().isEmpty()
                && !senha.isEmpty()
                && !confirmar.isEmpty();
        CampoForm.pintarBotao(btnCadastrar, preenchido);
        tema.aplicar();   // se estiver no tema escuro, o Tema ajusta as cores
    }

    /** Regra cumprida: icone de "certo" e texto verde. Senao: circulo vazio e texto cinza. */
    private void pintarRegra(TextView regra, boolean cumprida) {
        int cor;
        int icone;
        if (cumprida) {
            cor = Color.parseColor("#1B5E20");
            icone = R.drawable.ic_check_circulo;
        } else {
            cor = Color.parseColor("#5A6572");
            icone = R.drawable.ic_circulo;
        }
        regra.setTextColor(cor);
        regra.setCompoundDrawablesWithIntrinsicBounds(icone, 0, 0, 0);
        regra.setCompoundDrawableTintList(ColorStateList.valueOf(cor));
    }

    private void cadastrar() {
        String nome = editNome.getText().toString().trim();
        String email = editEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
        String senha = editSenha.getText().toString();
        String confirmar = editConfirmar.getText().toString();

        // valida tudo e mostra TODOS os erros de uma vez (o primeiro campo errado recebe o foco)
        EditText primeiroErro = null;
        if (nome.length() < 2) {
            CampoForm.mostrarErro(editNome, txtErroNome, "Informe seu nome");
            primeiroErro = editNome;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            CampoForm.mostrarErro(editEmail, txtErroEmail, "E-mail inválido");
            if (primeiroErro == null) {
                primeiroErro = editEmail;
            }
        } else if (banco.emailExiste(email)) {
            CampoForm.mostrarErro(editEmail, txtErroEmail, "Este e-mail já tem cadastro");
            if (primeiroErro == null) {
                primeiroErro = editEmail;
            }
        }
        if (senha.length() < 6) {
            CampoForm.mostrarErro(editSenha, txtErroSenha, "A senha precisa ter pelo menos 6 caracteres");
            if (primeiroErro == null) {
                primeiroErro = editSenha;
            }
        }
        if (!senha.equals(confirmar)) {
            CampoForm.mostrarErro(editConfirmar, txtErroConfirmar, "As senhas não são iguais");
            if (primeiroErro == null) {
                primeiroErro = editConfirmar;
            }
        }
        if (primeiroErro != null) {
            primeiroErro.requestFocus();
            tema.aplicar();
            return;
        }

        long id = banco.cadastrar(nome, email, senha);
        if (id == -1) {
            Toast.makeText(this, "Não foi possível criar a conta", Toast.LENGTH_LONG).show();
            return;
        }

        // ja deixa a pessoa logada e abre o app (limpa a pilha para o "voltar" nao cair no login)
        Sessao.entrar(this, new Usuario(id, nome, email));
        Toast.makeText(this, "Conta criada!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tema.ligar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        tema.desligar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (banco != null) {
            banco.close();
        }
    }
}
