package com.example.cluv;

import com.example.cluv.api.Api;
import com.example.cluv.api.ClimaApi;
import com.example.cluv.dados.Config;
import com.example.cluv.dados.Leituras;
import com.example.cluv.dados.Sessao;
import com.example.cluv.dados.UsuarioDbHelper;
import com.example.cluv.modelo.ClimaDados;
import com.example.cluv.modelo.Evento;
import com.example.cluv.modelo.GravidadeQueda;
import com.example.cluv.util.BarraAbas;
import com.example.cluv.util.Gps;
import com.example.cluv.util.Tema;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private static final float LIMITE_IMPACTO = 25f;
    private static final float LIMITE_GIRO = 3.5f;
    private static final long JANELA_MS = 1500;


    private View abaInicio, containerAbas;
    private int abaAtual = BarraAbas.INICIO;
    private final Fragment[] fragmentsAbas = new Fragment[5];

    // aba Inicio
    private TextView txtOla, txtDataHoje, txtAvatar, txtStatusMonitor, txtStatusSensores, txtContagemChip;
    private TextView txtMiniPosicao, txtMiniBateria, txtMiniServidor, txtMiniCheckin;
    private TextView txtUltimoEventoVazio, btnVerHistorico;
    private LinearLayout containerUltimoEvento;
    private View btnPanico;
    private ImageButton btnSair;

    private TextView txtClimaTemp, txtClimaDesc, txtClimaDetalhe, txtClimaVento, txtClimaUmidade, txtClimaPressao, txtClimaRisco;
    private ImageView imgClimaInicio;
    private long ultimaConsultaClima = 0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Dialog dialogSensores;
    private View raizDialogSensores;
    private TextView txtModoTema, txtLocalizacao;
    private TextView txtAcelerometro, txtGiroscopio, txtProximidade, txtLuz, txtBarometro, txtTemperatura;
    private Tema tema;
    private SensorManager sensorManager;
    private Sensor acelerometro, giroscopio, sensorProximidade, sensorLuz, barometro, sensorTemperatura;

    private long tempoImpacto = 0;
    private long tempoGiro = 0;
    private float maiorImpacto = 0;
    private boolean alertaAberto = false;

    private static final float[] SIMULACAO_G = {3f, 5f, 7f};
    private int proximaSimulacao = 0;

    private long ultimaAtualizacaoTela = 0;

    private UsuarioDbHelper banco;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // sem usuario logado, volta para o login
        if (!Sessao.logado(this)) {
            irParaLogin();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        for (int i = 0; i < BarraAbas.IDS_ABAS.length; i++) {
            int aba = i;
            findViewById(BarraAbas.IDS_ABAS[i]).setOnClickListener(v -> mostrarAba(aba));
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (abaAtual != BarraAbas.INICIO) {
                    mostrarAba(BarraAbas.INICIO);
                } else {
                    finishAffinity();
                }
            }
        });

        abaInicio = findViewById(R.id.abaInicio);
        containerAbas = findViewById(R.id.containerAbas);

        FragmentManager fm = getSupportFragmentManager();
        fragmentsAbas[BarraAbas.TRAJETO] = fm.findFragmentByTag("aba" + BarraAbas.TRAJETO);
        fragmentsAbas[BarraAbas.CLIMA] = fm.findFragmentByTag("aba" + BarraAbas.CLIMA);
        fragmentsAbas[BarraAbas.HISTORICO] = fm.findFragmentByTag("aba" + BarraAbas.HISTORICO);
        fragmentsAbas[BarraAbas.PERFIL] = fm.findFragmentByTag("aba" + BarraAbas.PERFIL);

        txtOla = findViewById(R.id.txtOla);
        txtStatusMonitor = findViewById(R.id.txtStatusMonitor);
        txtStatusSensores = findViewById(R.id.txtStatusSensores);
        txtMiniPosicao = findViewById(R.id.txtMiniPosicao);
        txtMiniBateria = findViewById(R.id.txtMiniBateria);
        txtMiniServidor = findViewById(R.id.txtMiniServidor);
        txtUltimoEventoVazio = findViewById(R.id.txtUltimoEventoVazio);
        btnVerHistorico = findViewById(R.id.btnVerHistorico);
        containerUltimoEvento = findViewById(R.id.containerUltimoEvento);
        btnPanico = findViewById(R.id.btnPanico);
        txtDataHoje = findViewById(R.id.txtDataHoje);
        txtAvatar = findViewById(R.id.txtAvatar);
        txtContagemChip = findViewById(R.id.txtContagemChip);
        txtMiniCheckin = findViewById(R.id.txtMiniCheckin);
        txtClimaTemp = findViewById(R.id.txtClimaTemp);
        txtClimaDesc = findViewById(R.id.txtClimaDesc);
        txtClimaDetalhe = findViewById(R.id.txtClimaDetalhe);
        txtClimaVento = findViewById(R.id.txtClimaVento);
        txtClimaUmidade = findViewById(R.id.txtClimaUmidade);
        txtClimaPressao = findViewById(R.id.txtClimaPressao);
        txtClimaRisco = findViewById(R.id.txtClimaRisco);
        imgClimaInicio = findViewById(R.id.imgClimaInicio);
        btnSair = findViewById(R.id.btnSair);

        tema = new Tema(this);

        banco = new UsuarioDbHelper(this);
        txtOla.setText("Olá, " + Sessao.nome(this));


        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sensorProximidade = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sensorLuz = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        barometro = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sensorTemperatura = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

        if (acelerometro == null) {
            txtStatusMonitor.setText("Detecção indisponível");
            txtStatusMonitor.setTextColor(Color.parseColor("#C62828"));
            txtStatusSensores.setText("Este celular não tem acelerômetro");
        } else if (giroscopio == null) {
            txtStatusMonitor.setText("Monitorando quedas");
            txtStatusSensores.setText("Só o acelerômetro (este celular não tem giroscópio)");
        } else {
            txtStatusMonitor.setText("Monitorando quedas");
            txtStatusSensores.setText("Acelerômetro e giroscópio ativos");
        }


        if (!Gps.temPermissao(this)) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

        btnPanico.setOnClickListener(v -> abrirAlerta("PANICO", "Botão de pânico acionado", 5, 0));
        btnVerHistorico.setOnClickListener(v -> mostrarAba(BarraAbas.HISTORICO));
        btnSair.setOnClickListener(v -> pedirSaida());
        findViewById(R.id.cardClimaInicio).setOnClickListener(v -> mostrarAba(BarraAbas.CLIMA));

        mostrarAba(BarraAbas.INICIO);
    }

    public void abrirAba(int aba) {
        mostrarAba(aba);
    }

    private void mostrarAba(int aba) {
        if (aba < BarraAbas.INICIO || aba > BarraAbas.PERFIL) {
            aba = BarraAbas.INICIO;
        }
        abaAtual = aba;

        abaInicio.setVisibility(aba == BarraAbas.INICIO ? View.VISIBLE : View.GONE);
        containerAbas.setVisibility(aba == BarraAbas.INICIO ? View.GONE : View.VISIBLE);

        FragmentTransaction troca = getSupportFragmentManager().beginTransaction();
        for (int i = BarraAbas.TRAJETO; i <= BarraAbas.PERFIL; i++) {
            if (i == aba && fragmentsAbas[i] == null) {
                fragmentsAbas[i] = criarFragment(i);
                troca.add(R.id.containerAbas, fragmentsAbas[i], "aba" + i);
            } else if (fragmentsAbas[i] != null) {
                if (i == aba) {
                    troca.show(fragmentsAbas[i]);
                } else {
                    troca.hide(fragmentsAbas[i]);
                }
            }
        }
        troca.commitNow();

        BarraAbas.marcar(this, abaAtual);
        tema.aplicar();
    }

    private Fragment criarFragment(int aba) {
        if (aba == BarraAbas.TRAJETO) {
            return new RotaActivity();
        } else if (aba == BarraAbas.CLIMA) {
            return new ClimaActivity();
        } else if (aba == BarraAbas.HISTORICO) {
            return new HistoricoActivity();
        }
        return new PerfilActivity();
    }


    public void abrirTesteSensores() {
        View raiz = getLayoutInflater().inflate(R.layout.dialog_sensores, null);
        raizDialogSensores = raiz;

        txtModoTema = raiz.findViewById(R.id.txtModoTema);
        txtAcelerometro = raiz.findViewById(R.id.txtAcelerometro);
        txtGiroscopio = raiz.findViewById(R.id.txtGiroscopio);
        txtProximidade = raiz.findViewById(R.id.txtProximidade);
        txtLuz = raiz.findViewById(R.id.txtLuz);
        txtBarometro = raiz.findViewById(R.id.txtBarometro);
        txtTemperatura = raiz.findViewById(R.id.txtTemperatura);
        txtLocalizacao = raiz.findViewById(R.id.txtLocalizacao);

        mostrarSeExiste(acelerometro, txtAcelerometro);
        mostrarSeExiste(giroscopio, txtGiroscopio);
        mostrarSeExiste(sensorProximidade, txtProximidade);
        mostrarSeExiste(sensorLuz, txtLuz);
        mostrarSeExiste(barometro, txtBarometro);
        mostrarSeExiste(sensorTemperatura, txtTemperatura);

        if (Tema.escuro) {
            mostrar(txtModoTema, "Modo escuro automático");
        } else {
            mostrar(txtModoTema, "Modo claro automático");
        }

        if (Leituras.localizacao != null) {
            txtLocalizacao.setText(textoLocalizacao(Leituras.localizacao));
        } else if (Gps.temPermissao(this)) {
            txtLocalizacao.setText("Buscando posição...");
        } else {
            txtLocalizacao.setText("Sem permissão de localização");
        }

        dialogSensores = new Dialog(this, R.style.Theme_Cluv);
        dialogSensores.setContentView(raiz);
        raiz.findViewById(R.id.btnFecharSensores).setOnClickListener(v -> dialogSensores.dismiss());

        dialogSensores.setOnDismissListener(d -> {
            txtModoTema = null;
            txtAcelerometro = null;
            txtGiroscopio = null;
            txtProximidade = null;
            txtLuz = null;
            txtBarometro = null;
            txtTemperatura = null;
            txtLocalizacao = null;
            raizDialogSensores = null;
            dialogSensores = null;
        });

        dialogSensores.show();
        dialogSensores.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        Tema.aplicarNaView(raiz);
    }

    private void mostrar(TextView campo, String texto) {
        if (campo != null) {
            campo.setText(texto);
        }
    }

    private String textoLocalizacao(Location location) {
        String origem = "rede";
        if ("gps".equals(location.getProvider())) {
            origem = "GPS";
        }
        return String.format(Locale.getDefault(),
                "%.5f, %.5f\nPrecisão ±%.0f m (%s)",
                location.getLatitude(), location.getLongitude(), location.getAccuracy(), origem);
    }

    private void mostrarSeExiste(Sensor sensor, TextView texto) {
        if (sensor == null) {
            texto.setText("sensor inexistente");
        } else {
            texto.setText("aguardando");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (acelerometro != null) {
            sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_GAME);
        }
        if (giroscopio != null) {
            sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_GAME);
        }
        // os outros podem ser no ritmo normal
        if (sensorProximidade != null) {
            sensorManager.registerListener(this, sensorProximidade, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (sensorLuz != null) {
            sensorManager.registerListener(this, sensorLuz, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (barometro != null) {
            sensorManager.registerListener(this, barometro, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (sensorTemperatura != null) {
            sensorManager.registerListener(this, sensorTemperatura, SensorManager.SENSOR_DELAY_NORMAL);
        }
        Gps.ligar(this, this);

        tempoImpacto = 0;
        tempoGiro = 0;
        maiorImpacto = 0;
        alertaAberto = false;

        atualizarCabecalho();
        atualizarResumo();
        atualizarClimaInicio();
        mostrarUltimoEvento();
        tema.aplicar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        Gps.desligar(this, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (banco != null) {
            banco.close();
        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        int tipo = event.sensor.getType();
        float valor = event.values[0];
        long agora = System.currentTimeMillis();

        if (tipo == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float forca = (float) Math.sqrt(x * x + y * y + z * z);

            if (forca > LIMITE_IMPACTO) {
                tempoImpacto = agora;
                if (forca > maiorImpacto) {
                    maiorImpacto = forca;
                }
            }

            if (agora - ultimaAtualizacaoTela > 200) {
                ultimaAtualizacaoTela = agora;
                mostrar(txtAcelerometro, String.format(Locale.getDefault(), "%.1f m/s²", forca));
            }
            verificarQueda(agora);
        }

        if (tipo == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float giro = (float) Math.sqrt(x * x + y * y + z * z);

            if (giro > LIMITE_GIRO) {
                tempoGiro = agora;
            }
            mostrar(txtGiroscopio, String.format(Locale.getDefault(), "%.1f rad/s", giro));
            verificarQueda(agora);
        }

        if (tipo == Sensor.TYPE_PROXIMITY) {
            if (valor < sensorProximidade.getMaximumRange()) {
                mostrar(txtProximidade, "coberto (bolso)");
            } else {
                mostrar(txtProximidade, "livre (na mão)");
            }
        }

        if (tipo == Sensor.TYPE_LIGHT) {
            mostrar(txtLuz, String.format(Locale.getDefault(), "%.1f lux", valor));
            if (Tema.atualizar(valor)) {
                tema.aplicar();
                if (raizDialogSensores != null) {
                    Tema.aplicarNaView(raizDialogSensores);
                }
            }
            if (Tema.escuro) {
                mostrar(txtModoTema, "Modo escuro automático");
            } else {
                mostrar(txtModoTema, "Modo claro automático");
            }
        }

        if (tipo == Sensor.TYPE_PRESSURE) {
            Leituras.salvarPressao(valor);
            if (valor > 300 && valor < 1100) {
                mostrar(txtBarometro, String.format(Locale.getDefault(), "%.1f hPa", valor));
            } else {
                mostrar(txtBarometro, "sem leitura válida");
            }
        }

        if (tipo == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            Leituras.salvarTemperatura(valor);
            if (valor > -60 && valor < 120) {
                mostrar(txtTemperatura, String.format(Locale.getDefault(), "%.1f °C", valor));
            } else {
                mostrar(txtTemperatura, "sem leitura válida");   // o emulador manda -1,0E30
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }



    private void verificarQueda(long agora) {
        if (tempoImpacto == 0 || alertaAberto) {
            return;
        }

        if (agora - tempoImpacto > JANELA_MS) {
            tempoImpacto = 0;
            maiorImpacto = 0;
            return;
        }

        boolean girou;
        if (giroscopio == null) {
            girou = true;
        } else {
            girou = tempoGiro != 0 && Math.abs(tempoImpacto - tempoGiro) <= JANELA_MS;
        }

        if (girou) {
            String detalhe = String.format(Locale.getDefault(), "Impacto de %.1f G (%.0f m/s²), queda %s",
                    maiorImpacto / 9.8f, maiorImpacto, GravidadeQueda.classificar(maiorImpacto));
            abrirAlerta("QUEDA", detalhe, Config.contagemQueda(this), maiorImpacto);
        }
    }


    public void simularQueda() {
        float g = SIMULACAO_G[proximaSimulacao];
        proximaSimulacao = (proximaSimulacao + 1) % SIMULACAO_G.length;

        float impacto = g * 9.8f;   // a MainActivity mede em m/s2
        String detalhe = String.format(Locale.getDefault(), "Queda simulada (teste): %.1f G, queda %s",
                g, GravidadeQueda.classificar(impacto));
        abrirAlerta("QUEDA", detalhe, Config.contagemQueda(this), impacto);
    }

    private void abrirAlerta(String tipo, String detalhe, int segundos, float impacto) {
        alertaAberto = true;
        AlertaActivity.abrir(this, tipo, detalhe, segundos, impacto);
    }


    @Override
    public void onLocationChanged(Location location) {
        Leituras.localizacao = location;

        mostrar(txtLocalizacao, textoLocalizacao(location));

        atualizarResumo();
        if (ClimaDados.ultimo == null) {
            atualizarClimaInicio();
        }
    }


    private void atualizarResumo() {
        Location local = Leituras.localizacao;
        if (local == null) {
            txtMiniPosicao.setText("sem posição");
        } else {
            txtMiniPosicao.setText(String.format(Locale.getDefault(), "±%.0f m", local.getAccuracy()));
        }

        txtMiniBateria.setText(Leituras.bateria(this) + "%");

        if (Config.checkinLigado(this)) {
            txtMiniCheckin.setText("ligado");
        } else {
            txtMiniCheckin.setText("desligado");
        }
        txtContagemChip.setText("Contagem " + Config.contagemQueda(this) + " s");

        txtMiniServidor.setText(Api.statusServidor);
        if (Api.statusServidor.equals("online")) {
            txtMiniServidor.setTextColor(Color.parseColor("#2E7D32"));
        } else if (Api.statusServidor.equals("offline")) {
            txtMiniServidor.setTextColor(Color.parseColor("#C62828"));
        } else {
            txtMiniServidor.setTextColor(Color.parseColor("#1A1F24"));
        }
    }


    private void atualizarCabecalho() {
        String nome = Sessao.nome(this);
        txtOla.setText("Olá, " + nome);
        if (nome.isEmpty()) {
            txtAvatar.setText("?");
        } else {
            txtAvatar.setText(nome.substring(0, 1).toUpperCase());
        }

        String data = new SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR")).format(new Date());
        txtDataHoje.setText(data.substring(0, 1).toUpperCase() + data.substring(1));
    }

    private void atualizarClimaInicio() {
        long agora = System.currentTimeMillis();
        ClimaDados clima = ClimaDados.ultimo;
        if (clima != null) {
            mostrarClimaInicio(clima);
        }

        Location local = Leituras.localizacao;
        if (clima == null) {
            if (local == null) {
                txtClimaDesc.setText("Aguardando a localização");
            } else {
                txtClimaDesc.setText("Buscando o clima...");
            }
        }

        boolean velho = clima == null || agora - ClimaDados.ultimoEmMs > 10 * 60000L;
        if (velho && local != null && agora - ultimaConsultaClima > 60000) {
            ultimaConsultaClima = agora;

            executor.execute(() -> {
                try {
                    ClimaDados novo = ClimaApi.consultar(local);
                    runOnUiThread(() -> mostrarClimaInicio(novo));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        if (ClimaDados.ultimo == null) {
                            txtClimaDesc.setText("Clima indisponível agora");
                        }
                    });
                }
            });
        }
    }

    private void mostrarClimaInicio(ClimaDados c) {
        txtClimaTemp.setText(Math.round(c.temperatura) + "°");
        txtClimaDesc.setText(c.descricao);

        String detalhe = "Sensação " + Math.round(c.sensacao) + "°";
        if (!c.cidade.isEmpty()) {
            detalhe += " · " + c.cidade;
        }
        txtClimaDetalhe.setText(detalhe);

        txtClimaVento.setText(Math.round(c.ventoKmh) + " km/h");
        txtClimaUmidade.setText(c.umidade + "%");
        txtClimaPressao.setText(c.pressao + " hPa");
        imgClimaInicio.setImageResource(c.iconeDrawable());

        String fundo, texto, frase;
        if (c.nivelRisco == 2) {
            fundo = "#FDECEA";
            texto = "#B71C1C";
            frase = "Risco de perigo";
        } else if (c.nivelRisco == 1) {
            fundo = "#FEF3C7";
            texto = "#6B2E07";
            frase = "Risco de atenção";
        } else {
            fundo = "#E3F2E5";
            texto = "#1B5E20";
            frase = "Risco baixo · bom para sair";
        }
        txtClimaRisco.setText(frase);
        txtClimaRisco.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(fundo)));
        txtClimaRisco.setTextColor(Color.parseColor(texto));

        tema.aplicar();
    }

    private void mostrarUltimoEvento() {
        List<Evento> eventos = banco.listarEventos(Sessao.id(this), 1);
        containerUltimoEvento.removeAllViews();

        if (eventos.isEmpty()) {
            txtUltimoEventoVazio.setVisibility(View.VISIBLE);
            btnVerHistorico.setVisibility(View.GONE);
        } else {
            txtUltimoEventoVazio.setVisibility(View.GONE);
            btnVerHistorico.setVisibility(View.VISIBLE);

            View linha = getLayoutInflater().inflate(R.layout.item_historico, containerUltimoEvento, false);
            HistoricoActivity.preencherLinha(linha, eventos.get(0));
            containerUltimoEvento.addView(linha);
        }
    }

    public void pedirSaida() {
        new AlertDialog.Builder(this)
                .setTitle("Sair da conta?")
                .setMessage("Você precisará entrar de novo para usar o app.")
                .setPositiveButton("Sair", (dialogo, botao) -> {
                    Sessao.sair(this);
                    irParaLogin();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void irParaLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
