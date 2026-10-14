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

    private String tipo;
    private String detalhe;
    private int segundosRestantes;
    private int segundosTotal;
    private float impacto;
    private boolean terminou = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UsuarioDbHelper banco;

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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
            }
        });

        vibrar();
        handler.postDelayed(timerContagem, 1000);
    }

    private final Runnable timerContagem = new Runnable() {
        @Override
        public void run() {
            segundosRestantes--;
            txtContagem.setText(String.valueOf(segundosRestantes));

            if (segundosRestantes <= 0) {
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


    private void cancelar() {
        if (terminou) {
            return;
        }
        terminou = true;
        handler.removeCallbacks(timerContagem);
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

        boolean simulado = detalhe.startsWith("Queda simulada");
        String dados = Api.montarDados(this, 2, impacto, simulado);   // 2 = alerta
        String detalheHist = detalheHistorico(origem);
        String extraEnviado = montarExtra("Enviado ao servidor (" + origem + ")", dados);
        String extraFalha = montarExtra("Falha ao enviar: sem internet ou servidor fora do ar", dados);

        boolean avisoLigado = Config.avisarContatos(this);
        String topico = Config.topicoNtfy(this);
        String tituloAviso = montarTituloAviso(simulado);
        String corpoAviso = montarCorpoAviso(dados);
        String linkAviso = montarLinkMapa(dados);
        int prioridade = 5;
        if (simulado) {
            prioridade = 3;
        }
        int prioridadeAviso = prioridade;

        executor.execute(() -> {
            boolean enviado = Api.enviar(dados);

            String aviso;
            if (!avisoLigado) {
                aviso = "desligado no Perfil";
            } else if (NtfyApi.enviar(topico, tituloAviso, corpoAviso, prioridadeAviso, "rotating_light", linkAviso)) {
                aviso = "enviado (ntfy)";
            } else {
                aviso = "falhou (sem internet?)";
            }

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


    private String titulo() {
        if (tipo.equals("QUEDA")) {
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

    private String montarTituloAviso(boolean simulado) {
        String titulo = Sessao.nome(this) + " precisa de ajuda";
        if (simulado) {
            titulo = "TESTE: " + titulo;
        }
        return titulo;
    }

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

    private String montarLinkMapa(String dados) {
        String latitude = Api.valorDoCampo(dados, "latitude");
        String longitude = Api.valorDoCampo(dados, "longitude");
        if (latitude.isEmpty() || longitude.isEmpty()) {
            return "";
        }
        return "https://www.google.com/maps?q=" + latitude + "," + longitude;
    }

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
