package com.example.cluv;

import com.example.cluv.api.Api;
import com.example.cluv.api.NtfyApi;
import com.example.cluv.dados.Config;
import com.example.cluv.dados.Sessao;
import com.example.cluv.dados.UsuarioDbHelper;
import com.example.cluv.modelo.GravidadeQueda;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class AlertaActivity extends AppCompatActivity {

    private TextView txtAlertaTitulo, txtAlertaMotivo, txtContagem, txtAlertaInfo;
    private Button btnEstouBem, btnPedirAjuda;

    private String tipo;          // QUEDA, PANICO ou CHECKIN
    private String detalhe;
    private int segundosRestantes;
    private int segundosTotal;    // a contagem inteira (30 s numa queda, 5 s no panico)
    private float impacto;        // so tem valor quando foi uma queda de verdade
    private boolean terminou = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UsuarioDbHelper banco;

    /** Jeito facil de abrir esta tela a partir de qualquer outra. */
    public static void abrir(Context context, String tipo, String detalhe, int segundos, float impacto) {
        Intent intent = new Intent(context, AlertaActivity.class);
        intent.putExtra("tipo", tipo);
        intent.putExtra("detalhe", detalhe);
        intent.putExtra("segundos", segundos);
        intent.putExtra("impacto", impacto);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_alerta);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        txtAlertaTitulo = findViewById(R.id.txtAlertaTitulo);
        txtAlertaMotivo = findViewById(R.id.txtAlertaMotivo);
        txtContagem = findViewById(R.id.txtContagem);
        txtAlertaInfo = findViewById(R.id.txtAlertaInfo);
        btnEstouBem = findViewById(R.id.btnEstouBem);
        btnPedirAjuda = findViewById(R.id.btnPedirAjuda);

        banco = new UsuarioDbHelper(this);

        // dados que a outra tela mandou pelo Intent
        tipo = getIntent().getStringExtra("tipo");
        detalhe = getIntent().getStringExtra("detalhe");
        segundosRestantes = getIntent().getIntExtra("segundos", 30);
        segundosTotal = segundosRestantes;
        impacto = getIntent().getFloatExtra("impacto", 0);

        txtAlertaTitulo.setText(titulo());
        txtAlertaMotivo.setText(detalhe);
        txtContagem.setText(String.valueOf(segundosRestantes));

        btnEstouBem.setOnClickListener(v -> cancelar());
        btnPedirAjuda.setOnClickListener(v -> enviarAlerta("pedido manual"));

        // durante a contagem o botao "voltar" nao faz nada (tem que escolher um dos botoes)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
            }
        });

        // timer igual ao do professor: Handler + Runnable a cada 1 segundo
        vibrar();
        handler.postDelayed(timerContagem, 1000);
    }// fim do onCreate

    private final Runnable timerContagem = new Runnable() {
        @Override
        public void run() {
            segundosRestantes--;
            txtContagem.setText(String.valueOf(segundosRestantes));

            if (segundosRestantes <= 0) {
                // ninguem cancelou: envia sozinho
                enviarAlerta("envio automático");
            } else {
                vibrar();
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerContagem);
        executor.shutdown();
        banco.close();
    }

    // ============================================================
    //  CANCELAR OU ENVIAR
    // ============================================================

    private void cancelar() {
        if (terminou) {
            return;
        }
        terminou = true;
        handler.removeCallbacks(timerContagem);

        // o "extra" guarda o que estava acontecendo (aparece na janela de detalhes do historico)
        boolean simulado = detalhe.startsWith("Queda simulada");
        String extra = montarExtra("Cancelado pela pessoa (Estou bem)", Api.montarDados(this, 2, impacto, simulado));
        banco.registrarEvento(Sessao.id(this), titulo(), detalheHistorico(""), "cancelado", extra);
        Toast.makeText(this, "Alerta cancelado", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void enviarAlerta(String origem) {
        if (terminou) {
            return;
        }
        terminou = true;
        handler.removeCallbacks(timerContagem);

        txtAlertaInfo.setText("Enviando o alerta...");
        btnEstouBem.setEnabled(false);
        btnPedirAjuda.setEnabled(false);

        // queda simulada (botao de teste): o que faltar no aparelho vai com valor fixo de teste
        boolean simulado = detalhe.startsWith("Queda simulada");
        String dados = Api.montarDados(this, 2, impacto, simulado);   // 2 = alerta
        String detalheHist = detalheHistorico(origem);
        String extraEnviado = montarExtra("Enviado ao servidor (" + origem + ")", dados);
        String extraFalha = montarExtra("Falha ao enviar: sem internet ou servidor fora do ar", dados);

        // aviso para os contatos de confianca (ntfy): o texto e montado aqui, na thread principal
        boolean avisoLigado = Config.avisarContatos(this);
        String topico = Config.topicoNtfy(this);
        String tituloAviso = montarTituloAviso(simulado);
        String corpoAviso = montarCorpoAviso(dados);
        String linkAviso = montarLinkMapa(dados);
        int prioridade = 5;      // urgente
        if (simulado) {
            prioridade = 3;      // teste: notificacao normal
        }
        int prioridadeAviso = prioridade;

        // rede nunca na thread principal (igual ao appMove)
        executor.execute(() -> {
            boolean enviado = Api.enviar(dados);

            // os contatos sao avisados mesmo que o servidor do professor falhe
            String aviso;
            if (!avisoLigado) {
                aviso = "desligado no Perfil";
            } else if (NtfyApi.enviar(topico, tituloAviso, corpoAviso, prioridadeAviso, "rotating_light", linkAviso)) {
                aviso = "enviado (ntfy)";
            } else {
                aviso = "falhou (sem internet?)";
            }

            // a tela so pode ser mexida na thread principal
            runOnUiThread(() -> {
                if (enviado) {
                    banco.registrarEvento(Sessao.id(this), titulo(), detalheHist, "enviado",
                            extraEnviado + "\nAviso aos contatos: " + aviso);
                    Toast.makeText(this, "Alerta enviado", Toast.LENGTH_SHORT).show();
                } else {
                    banco.registrarEvento(Sessao.id(this), titulo(), detalheHist, "falha no envio",
                            extraFalha + "\nAviso aos contatos: " + aviso);
                    Toast.makeText(this, "Não foi possível enviar o alerta", Toast.LENGTH_LONG).show();
                }
                finish();
            });
        });
    }

    // ============================================================
    //  AUXILIARES
    // ============================================================

    private String titulo() {
        if (tipo.equals("QUEDA")) {
            // impacto = 0 quando e a queda simulada (teste): ai nao tem como classificar
            if (impacto > 0) {
                return "Queda " + GravidadeQueda.classificar(impacto);
            }
            return "Queda detectada";
        } else if (tipo.equals("PANICO")) {
            return "Botão de pânico";
        } else {
            return "Check-in sem resposta";
        }
    }

    // ============================================================
    //  AVISO PARA OS CONTATOS DE CONFIANCA (ntfy)
    // ============================================================

    /** Ex.: "Maria precisa de ajuda". Na queda simulada comeca com "TESTE:" para ninguem se assustar. */
    private String montarTituloAviso(boolean simulado) {
        String titulo = Sessao.nome(this) + " precisa de ajuda";
        if (simulado) {
            titulo = "TESTE: " + titulo;
        }
        return titulo;
    }

    /** O que aconteceu, a bateria e o link da localizacao. */
    private String montarCorpoAviso(String dados) {
        String texto;
        if (tipo.equals("QUEDA")) {
            if (impacto > 0) {
                texto = String.format(Locale.getDefault(), "Queda %s (%.1f G).",
                        GravidadeQueda.classificar(impacto), impacto / 9.8f);
            } else {
                texto = "Queda detectada.";
            }
        } else if (tipo.equals("PANICO")) {
            texto = "Acionou o botão de pânico.";
        } else {
            texto = "Não respondeu ao check-in de segurança.";
        }

        String bateria = Api.valorDoCampo(dados, "bateria");
        if (!bateria.isEmpty()) {
            texto += "\nBateria: " + bateria + "%";
        }

        String link = montarLinkMapa(dados);
        if (link.isEmpty()) {
            texto += "\nLocalização: sem posição do GPS";
        } else {
            texto += "\nLocalização: " + link;
        }
        return texto;
    }

    /** Link do Google Maps com a posicao (vazio se o GPS nao achou). Quem recebe abre no proprio celular. */
    private String montarLinkMapa(String dados) {
        String latitude = Api.valorDoCampo(dados, "latitude");
        String longitude = Api.valorDoCampo(dados, "longitude");
        if (latitude.isEmpty() || longitude.isEmpty()) {
            return "";
        }
        return "https://www.google.com/maps?q=" + latitude + "," + longitude;
    }

    /** Linhas "Campo: valor" para a janela de detalhes do historico. */
    private String montarExtra(String desfecho, String dados) {
        String nome = "Check-in";
        if (tipo.equals("QUEDA")) {
            nome = "Queda";
        } else if (tipo.equals("PANICO")) {
            nome = "Pânico";
        }

        String texto = "Tipo: " + nome
                + "\nMotivo: " + detalhe
                + "\nContagem: " + segundosTotal + " s (faltavam " + Math.max(segundosRestantes, 0) + " s)"
                + "\nResultado: " + desfecho;
        return texto + "\n" + Api.resumoDosDados(dados);
    }

    /** Texto pequeno que aparece no historico (ex.: "teste, envio automático"). */
    private String detalheHistorico(String origem) {
        if (detalhe.startsWith("Queda simulada")) {
            if (origem.isEmpty()) {
                return "teste";
            }
            return "teste, " + origem;
        }
        return origem;
    }

    private void vibrar() {
        Vibrator vibrador = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vibrador.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
    }
}
