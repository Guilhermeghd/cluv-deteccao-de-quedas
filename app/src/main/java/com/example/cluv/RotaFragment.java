package com.example.cluv;

import com.example.cluv.api.Api;
import com.example.cluv.api.ClimaApi;
import com.example.cluv.api.RotaApi;
import com.example.cluv.dados.Config;
import com.example.cluv.dados.Leituras;
import com.example.cluv.dados.Sessao;
import com.example.cluv.dados.UsuarioDbHelper;
import com.example.cluv.modelo.ClimaDados;
import com.example.cluv.modelo.Rota;
import com.example.cluv.util.BarraAbas;
import com.example.cluv.util.Gps;
import com.example.cluv.util.Mapa;
import com.example.cluv.util.Tema;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import android.graphics.Color;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela ROTA (antes chamada Trajeto):
 *  - mapa com o percurso (linha azul) e a rota ate o destino (linha verde)
 *  - barra fixa embaixo com distancia, tempo, velocidade e os botoes de iniciar/encerrar e de panico
 *  - antes de sair, avisa se o clima esta com risco
 *  - durante o trajeto manda a telemetria para a API a cada 20 s (tipo_registro = 1)
 *  - check-in (so durante o trajeto): de tempos em tempos pergunta "esta tudo bem?"
 */
public class RotaFragment extends Fragment implements LocationListener {

    // como a pessoa se desloca (o mesmo numero escolhe o nome, o perfil do servidor de rotas e a frase)
    private static final String[] NOMES_MEIOS = {"A pé", "Bicicleta", "Moto"};
    private static final String[] PERFIS_ROTA = {"routed-foot", "routed-bike", "routed-car"};   // a moto usa as ruas do carro
    private static final String[] FRASES_MEIOS = {"a pé", "de bicicleta", "de moto"};
    private static final int[] ICONES_MEIOS = {R.drawable.ic_a_pe, R.drawable.ic_bicicleta, R.drawable.ic_moto};

    private static final long INTERVALO_TELEMETRIA_MS = 20000;   // 20 segundos
    private static final long TEMPO_RESPOSTA_CHECKIN_MS = 30000; // 30 segundos para responder

    private TextView txtTrajetoKm, txtTrajetoMin, txtTrajetoVel, txtTrajetoStatus, txtProximoCheckin, txtIntervaloCheckin;
    private Button btnTrajeto;

    // trajeto
    private boolean trajetoAtivo = false;
    private long inicioTrajeto = 0;
    private float distancia = 0;              // em metros
    private Location posicaoAnterior = null;
    private String statusTelemetria = "";
    private int telemetriasOk = 0;        // quantas telemetrias deste trajeto chegaram na API
    private int telemetriasFalha = 0;
    private boolean telaAberta = false;
    private float velocidadeAtual = 0;     // km/h, vem do GPS
    private long proximoCheckinMs = 0;     // quando o proximo check-in vai perguntar (0 = nenhum agendado)

    // mapa
    private MapView mapa;
    private Polyline linhaPercurso;     // azul: por onde a pessoa andou
    private Polyline linhaRota;         // verde: rota ate o destino
    private Marker marcadorAqui;        // onde a pessoa esta agora
    private Marker marcadorDestino;     // o ponto que ela tocou no mapa
    private GeoPoint destino = null;
    private boolean mapaCentralizado = false;
    private boolean seguirMapa = true;      // o mapa acompanha a pessoa (para quando ela arrasta o mapa)
    private TextView txtRotaInfo;
    private Button btnTracarRota, btnLimparRota;
    private View btnCentralizar;
    private LinearLayout containerMeios;
    private TextView txtGpsChip, txtProgressoFaltam, txtProgressoTempo;
    private View cardProgresso;
    private ProgressBar barraProgresso;
    private Rota rotaAtual = null;                       // a rota tracada (para calcular o progresso)
    private double rotaTotalM = 0;                       // o tamanho da linha dessa rota, em metros
    private boolean gpsChipVerde = false;
    private TextView txtRotaBarra;                       // distancia e tempo da rota, na barra de baixo
    private final TextView[] botoesMeio = new TextView[NOMES_MEIOS.length];
    private int meioAtual = 0;                           // 0 = a pe, 1 = bicicleta, 2 = moto

    private AlertDialog dialogCheckin;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UsuarioDbHelper banco;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // IMPORTANTE: configurar o osmdroid ANTES de inflar o layout. O MapView ja comeca a baixar o mapa
        // quando e criado, e sem o User-Agent certo o OpenStreetMap bloqueia (erro 403 "Access blocked").
        Mapa.configurar(requireContext());
        return inflater.inflate(R.layout.fragment_trajeto, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtTrajetoKm = view.findViewById(R.id.txtTrajetoKm);
        txtTrajetoMin = view.findViewById(R.id.txtTrajetoMin);
        txtTrajetoVel = view.findViewById(R.id.txtTrajetoVel);
        txtTrajetoStatus = view.findViewById(R.id.txtTrajetoStatus);
        txtProximoCheckin = view.findViewById(R.id.txtProximoCheckin);
        btnTrajeto = view.findViewById(R.id.btnTrajeto);
        // o check-in e ligado/desligado e ajustado no Perfil; aqui so aparece o status. Tocar leva ao Perfil
        view.findViewById(R.id.cardCheckin).setOnClickListener(v ->
                ((MainActivity) requireActivity()).abrirAba(BarraAbas.PERFIL));
        txtIntervaloCheckin = view.findViewById(R.id.txtIntervaloCheckin);
        txtRotaInfo = view.findViewById(R.id.txtRotaInfo);
        btnTracarRota = view.findViewById(R.id.btnTracarRota);
        btnLimparRota = view.findViewById(R.id.btnLimparRota);
        btnCentralizar = view.findViewById(R.id.btnCentralizar);
        containerMeios = view.findViewById(R.id.containerMeios);
        txtRotaBarra = view.findViewById(R.id.txtRotaBarra);
        txtGpsChip = view.findViewById(R.id.txtGpsChip);
        txtProgressoFaltam = view.findViewById(R.id.txtProgressoFaltam);
        txtProgressoTempo = view.findViewById(R.id.txtProgressoTempo);
        cardProgresso = view.findViewById(R.id.cardProgresso);
        barraProgresso = view.findViewById(R.id.barraProgresso);

        banco = new UsuarioDbHelper(requireContext());

        meioAtual = Config.meioTransporte(requireContext());
        criarBotoesMeio();
        configurarMapa(view);
        btnTracarRota.setOnClickListener(v -> tracarRota());
        btnLimparRota.setOnClickListener(v -> limparRota());
        btnCentralizar.setOnClickListener(v -> centralizarMapa());

        // mesmo botao de panico da tela Inicio: abre a contagem de 5 segundos
        view.findViewById(R.id.btnPanicoTrajeto).setOnClickListener(v ->
                AlertaActivity.abrir(requireContext(), "PANICO", "Botão de pânico acionado", 5, 0));

        btnTrajeto.setOnClickListener(v -> {
            if (trajetoAtivo) {
                encerrarTrajeto();
            } else {
                iniciarTrajeto();
            }
        });

    }// fim do onCreate

    @Override
    public void onResume() {
        super.onResume();
        telaAberta = true;
        Gps.ligar(requireContext(), this);
        mapa.onResume();
        pintarBotoesMeio();
        txtIntervaloCheckin.setText("Perguntar a cada " + minutosCheckin() + " min");
        handler.removeCallbacks(timerRelogio);
        handler.post(timerRelogio);   // o cronometro e o "proximo check-in" andam de 1 em 1 segundo
    }

    @Override
    public void onPause() {
        super.onPause();
        telaAberta = false;
        Gps.desligar(requireContext(), this);
        mapa.onPause();
        handler.removeCallbacks(timerRelogio);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);   // para os timers
        executor.shutdown();
        if (dialogCheckin != null && dialogCheckin.isShowing()) {
            dialogCheckin.dismiss();
        }
        banco.close();
        if (mapa != null) {
            mapa.onDetach();
        }
    }

    // ============================================================
    //  GPS (barometro e temperatura sao lidos pela MainActivity e ficam em Leituras)
    // ============================================================

    @Override
    public void onLocationChanged(Location location) {
        Leituras.localizacao = location;
        mostrarPosicaoNoMapa(location);

        // velocidade ao vivo (o GPS informa em m/s)
        if (location.hasSpeed()) {
            velocidadeAtual = location.getSpeed() * 3.6f;
        } else {
            velocidadeAtual = 0;
        }

        // chip do GPS no cabecalho
        txtGpsChip.setText("GPS ±" + Math.round(location.getAccuracy()) + " m");
        if (!gpsChipVerde) {
            gpsChipVerde = true;
            txtGpsChip.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E3F2E5")));
            txtGpsChip.setTextColor(Color.parseColor("#1B5E20"));
            Tema.aplicarEm(requireActivity());   // se estiver no tema escuro, o Tema ajusta as cores
        }

        if (!trajetoAtivo) {
            return;
        }
        // ignora posicoes imprecisas (mais de 50 m de erro)
        if (location.getAccuracy() > 50) {
            return;
        }

        if (posicaoAnterior == null) {
            posicaoAnterior = location;
            linhaPercurso.addPoint(new GeoPoint(location));   // primeiro ponto da linha azul
        } else {
            float passo = posicaoAnterior.distanceTo(location);   // em metros
            // menos de 5 m e so o GPS "tremendo" com o celular parado
            if (passo > 5) {
                distancia = distancia + passo;
                posicaoAnterior = location;
                linhaPercurso.addPoint(new GeoPoint(location));
            }
        }
        atualizarProgresso(location);
        mapa.invalidate();
        mostrarTrajeto();
    }

    // ============================================================
    //  MAPA (osmdroid + OpenStreetMap)
    // ============================================================

    private void configurarMapa(View view) {
        mapa = view.findViewById(R.id.mapa);
        mapa.setTileSource(Mapa.fonteDasRuas());
        mapa.setBackgroundResource(R.drawable.bg_mapa);   // cantos arredondados...
        mapa.setClipToOutline(true);                      // ...e o mapa e recortado nesse formato
        mapa.setMultiTouchControls(true);

        // o mapa esta dentro de um ScrollView: sem isso, arrastar o mapa rolaria a tela
        mapa.setOnTouchListener((v, evento) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (evento.getAction() == MotionEvent.ACTION_MOVE) {
                seguirMapa = false;   // a pessoa esta olhando outra parte do mapa: nao puxa de volta
            }
            return false;
        });

        // tocar no mapa escolhe o destino
        mapa.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint ponto) {
                marcarDestino(ponto);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint ponto) {
                return false;
            }
        }));

        linhaRota = new Polyline();
        linhaRota.getOutlinePaint().setColor(Color.parseColor("#2E7D32"));   // verde
        linhaRota.getOutlinePaint().setStrokeWidth(14f);
        mapa.getOverlays().add(linhaRota);

        linhaPercurso = new Polyline();
        linhaPercurso.getOutlinePaint().setColor(Color.parseColor("#1565C0"));   // azul
        linhaPercurso.getOutlinePaint().setStrokeWidth(12f);
        mapa.getOverlays().add(linhaPercurso);

        marcadorAqui = new Marker(mapa);
        marcadorAqui.setTitle("Você está aqui");
        marcadorAqui.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        marcadorDestino = new Marker(mapa);
        marcadorDestino.setTitle("Destino");
        marcadorDestino.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // se outra tela ja achou a posicao, comeca nela; senao mostra o Brasil todo ate o GPS achar
        if (Leituras.localizacao != null) {
            mostrarPosicaoNoMapa(Leituras.localizacao);
        } else {
            mapa.getController().setZoom(4.0);
            mapa.getController().setCenter(new GeoPoint(-15.78, -47.93));
        }
    }

    /** Move o marcador "voce esta aqui". Na primeira posicao recebida, o mapa vai ate la. */
    private void mostrarPosicaoNoMapa(Location location) {
        GeoPoint aqui = new GeoPoint(location);
        marcadorAqui.setPosition(aqui);
        if (!mapa.getOverlays().contains(marcadorAqui)) {
            mapa.getOverlays().add(marcadorAqui);
        }
        if (!mapaCentralizado) {
            mapaCentralizado = true;
            mapa.getController().setZoom(17.0);
            mapa.getController().setCenter(aqui);
        } else if (trajetoAtivo && seguirMapa) {
            mapa.getController().animateTo(aqui);   // o mapa acompanha a pessoa andando
        }
        mapa.invalidate();
    }

    /** Botao "Centralizar": volta para a posicao da pessoa e o mapa volta a acompanhar. */
    private void centralizarMapa() {
        seguirMapa = true;
        Location local = Leituras.localizacao;
        if (local == null) {
            Toast.makeText(requireContext(), "Aguardando a localização", Toast.LENGTH_SHORT).show();
            return;
        }
        mapa.getController().animateTo(new GeoPoint(local));
    }

    private void marcarDestino(GeoPoint ponto) {
        destino = ponto;
        marcadorDestino.setPosition(ponto);
        if (!mapa.getOverlays().contains(marcadorDestino)) {
            mapa.getOverlays().add(marcadorDestino);
        }
        txtRotaInfo.setText("Destino marcado. Toque em Traçar rota.");
        mapa.invalidate();
    }

    // ============================================================
    //  ROTA ATE O DESTINO (OSRM)
    // ============================================================

    private void tracarRota() {
        Location local = Leituras.localizacao;
        if (local == null) {
            Toast.makeText(requireContext(), "Aguardando a localização", Toast.LENGTH_SHORT).show();
            return;
        }
        if (destino == null) {
            Toast.makeText(requireContext(), "Toque no mapa para escolher o destino", Toast.LENGTH_SHORT).show();
            return;
        }

        txtRotaInfo.setText("Buscando a rota...");
        GeoPoint origem = new GeoPoint(local);
        GeoPoint ate = destino;
        int meio = meioAtual;   // se a pessoa trocar de meio enquanto busca, esta busca continua valendo para o antigo

        // rede nunca na thread principal
        executor.execute(() -> {
            try {
                Rota rota = RotaApi.buscar(origem, ate, PERFIS_ROTA[meio]);
                naUi(() -> mostrarRota(rota, meio));
            } catch (Exception e) {
                naUi(() -> txtRotaInfo.setText("Não deu para buscar a rota: " + e.getMessage()));
            }
        });
    }

    private void mostrarRota(Rota rota, int meio) {
        if (rota == null) {
            txtRotaInfo.setText("Não foi possível achar um caminho " + FRASES_MEIOS[meio] + " até esse ponto.");
            txtRotaBarra.setVisibility(View.GONE);
            rotaAtual = null;
            cardProgresso.setVisibility(View.GONE);
            return;
        }
        linhaRota.setPoints(rota.pontos);

        // guarda a rota para o progresso (so aparece com a rota em andamento)
        rotaAtual = rota;
        rotaTotalM = tamanhoDaLinha(rota.pontos);
        if (trajetoAtivo) {
            atualizarProgresso(Leituras.localizacao);
        }

        // a distancia e o tempo ficam na barra de baixo, perto do botao de iniciar o trajeto
        long minutos = Math.round(rota.duracaoS / 60);
        txtRotaBarra.setText(String.format(Locale.getDefault(),
                "Rota: %.1f km · cerca de %d min · %s", rota.distanciaM / 1000, minutos, FRASES_MEIOS[meio]));
        txtRotaBarra.setVisibility(View.VISIBLE);
        txtRotaInfo.setText("Rota traçada. A distância e o tempo estão na barra abaixo.");

        // da zoom para a rota inteira caber no mapa
        if (rota.pontos.size() > 1) {
            mapa.zoomToBoundingBox(BoundingBox.fromGeoPoints(rota.pontos), true, 80);
        }
        mapa.invalidate();
    }

    private void limparRota() {
        destino = null;
        linhaRota.setPoints(new ArrayList<>());
        mapa.getOverlays().remove(marcadorDestino);
        txtRotaInfo.setText("Toque no mapa para escolher o destino.");
        txtRotaBarra.setVisibility(View.GONE);
        rotaAtual = null;
        cardProgresso.setVisibility(View.GONE);
        mapa.invalidate();
    }

    // ============================================================
    //  PROGRESSO DA ROTA
    // ============================================================

    /** Soma o tamanho de cada pedacinho da linha, em metros. */
    private double tamanhoDaLinha(List<GeoPoint> pontos) {
        double total = 0;
        for (int i = 0; i < pontos.size() - 1; i++) {
            total += pontos.get(i).distanceToAsDouble(pontos.get(i + 1));
        }
        return total;
    }

    /**
     * Calcula quanto falta: acha o ponto da rota mais perto da pessoa e soma o que sobra dali ate o destino.
     * Funciona mesmo se ela sair um pouco do caminho. So aparece com a rota em andamento e uma rota tracada.
     */
    private void atualizarProgresso(Location aqui) {
        if (!trajetoAtivo || rotaAtual == null || rotaAtual.pontos.size() < 2 || rotaTotalM <= 0) {
            cardProgresso.setVisibility(View.GONE);
            return;
        }
        cardProgresso.setVisibility(View.VISIBLE);

        List<GeoPoint> pontos = rotaAtual.pontos;
        double restante = rotaTotalM;   // sem posicao ainda: falta a rota inteira

        if (aqui != null) {
            // 1) o ponto da rota mais perto da pessoa
            int maisPerto = 0;
            double menorDistancia = Double.MAX_VALUE;
            GeoPoint pessoa = new GeoPoint(aqui);
            for (int i = 0; i < pontos.size(); i++) {
                double distancia = pessoa.distanceToAsDouble(pontos.get(i));
                if (distancia < menorDistancia) {
                    menorDistancia = distancia;
                    maisPerto = i;
                }
            }

            // 2) o que falta dali ate o fim da rota
            restante = 0;
            for (int i = maisPerto; i < pontos.size() - 1; i++) {
                restante += pontos.get(i).distanceToAsDouble(pontos.get(i + 1));
            }
        }
        if (restante > rotaTotalM) {
            restante = rotaTotalM;
        }

        barraProgresso.setProgress((int) (1000 * (1 - restante / rotaTotalM)));

        if (restante < 30) {
            txtProgressoFaltam.setText("Você chegou ao destino");
            txtProgressoTempo.setText("");
            barraProgresso.setProgress(1000);
        } else {
            // o tempo restante e proporcional ao que falta da rota
            long minutos = Math.round(rotaAtual.duracaoS * (restante / rotaTotalM) / 60);
            txtProgressoFaltam.setText(String.format(Locale.getDefault(), "Faltam %.1f km", restante / 1000));
            txtProgressoTempo.setText("cerca de " + minutos + " min");
        }
    }

    // ============================================================
    //  MEIO DE TRANSPORTE (a pe, bicicleta ou moto)
    // ============================================================

    private void criarBotoesMeio() {
        float densidade = getResources().getDisplayMetrics().density;
        int altura = (int) (9 * densidade);
        int margem = (int) (6 * densidade);

        for (int i = 0; i < NOMES_MEIOS.length; i++) {
            int meio = i;

            TextView botao = new TextView(requireContext());
            botao.setText(NOMES_MEIOS[i]);
            botao.setTextSize(14);
            botao.setGravity(android.view.Gravity.CENTER);
            botao.setBackgroundResource(R.drawable.bg_chip);
            botao.setPadding(0, altura, 0, altura);
            botao.setCompoundDrawablesWithIntrinsicBounds(ICONES_MEIOS[i], 0, 0, 0);
            botao.setCompoundDrawablePadding((int) (6 * densidade));
            botao.setClickable(true);
            botao.setOnClickListener(v -> escolherMeio(meio));

            // peso 1: os tres botoes dividem a largura igualmente
            LinearLayout.LayoutParams medidas = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                medidas.setMarginStart(margem);
            }
            containerMeios.addView(botao, medidas);
            botoesMeio[i] = botao;
        }
        pintarBotoesMeio();
    }

    private void escolherMeio(int meio) {
        if (meio == meioAtual) {
            return;
        }
        meioAtual = meio;
        Config.salvarMeioTransporte(requireContext(), meio);
        pintarBotoesMeio();

        // ja tinha uma rota na tela: refaz para o meio novo (o caminho e o tempo mudam)
        if (destino != null && !linhaRota.getPoints().isEmpty()) {
            tracarRota();
        }
    }

    /** O meio escolhido fica verde-azulado com texto branco; os outros ficam cinza. */
    private void pintarBotoesMeio() {
        for (int i = 0; i < botoesMeio.length; i++) {
            if (i == meioAtual) {
                botoesMeio[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0E7C8B")));
                botoesMeio[i].setTextColor(Color.WHITE);
                botoesMeio[i].setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            } else {
                botoesMeio[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E6EAEF")));
                botoesMeio[i].setTextColor(Color.parseColor("#5A6572"));
                botoesMeio[i].setCompoundDrawableTintList(ColorStateList.valueOf(Color.parseColor("#5A6572")));
            }
        }
    }

    // ============================================================
    //  TRAJETO + TELEMETRIA
    // ============================================================

    /** Antes de sair, olha o clima: com risco de atencao ou perigo, avisa e a pessoa decide. */
    private void iniciarTrajeto() {
        // se o clima foi consultado ha pouco (ex.: na aba Clima), nao precisa consultar de novo
        boolean climaRecente = ClimaDados.ultimo != null
                && System.currentTimeMillis() - ClimaDados.ultimoEmMs < 30 * 60000L;
        if (climaRecente) {
            avisarClimaOuComecar(ClimaDados.ultimo);
            return;
        }

        Location local = Leituras.localizacao;
        if (local == null) {
            comecarTrajeto();   // sem posicao nao tem como ver o clima
            return;
        }

        btnTrajeto.setEnabled(false);
        txtTrajetoStatus.setText("Verificando o clima antes de sair...");

        // rede nunca na thread principal
        executor.execute(() -> {
            try {
                ClimaDados clima = ClimaApi.consultar(local);
                naUi(() -> avisarClimaOuComecar(clima));
            } catch (Exception e) {
                naUi(() -> comecarTrajeto());   // sem internet: sai mesmo assim
            }
        });
    }

    private void avisarClimaOuComecar(ClimaDados clima) {
        btnTrajeto.setEnabled(true);
        if (clima.nivelRisco == 0) {
            comecarTrajeto();
            return;
        }

        String nivel = "atenção";
        if (clima.nivelRisco == 2) {
            nivel = "perigo";
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Clima: risco de " + nivel)
                .setMessage(clima.motivosRisco)
                .setPositiveButton("Iniciar mesmo assim", (dialogo, botao) -> comecarTrajeto())
                .setNegativeButton("Cancelar", (dialogo, botao) ->
                        txtTrajetoStatus.setText("Nenhuma rota em andamento"))
                .show();
    }

    /** Iniciar rota = verde-azulado com texto branco; Encerrar rota = cinza claro com texto escuro. */
    private void pintarBotaoPrincipal(boolean emAndamento) {
        if (emAndamento) {
            btnTrajeto.setText("Encerrar rota");
            btnTrajeto.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E6EAEF")));
            btnTrajeto.setTextColor(Color.parseColor("#1A1F24"));
        } else {
            btnTrajeto.setText("Iniciar rota");
            btnTrajeto.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0E7C8B")));
            btnTrajeto.setTextColor(Color.WHITE);
        }
        Tema.aplicarEm(requireActivity());   // no tema escuro, o Tema troca as cores claras
    }

    private void comecarTrajeto() {
        btnTrajeto.setEnabled(true);
        trajetoAtivo = true;
        seguirMapa = true;
        inicioTrajeto = System.currentTimeMillis();
        distancia = 0;
        posicaoAnterior = null;
        statusTelemetria = "";
        telemetriasOk = 0;
        telemetriasFalha = 0;
        linhaPercurso.setPoints(new ArrayList<>());   // comeca uma linha azul nova
        mapa.invalidate();
        pintarBotaoPrincipal(true);
        mostrarTrajeto();
        atualizarRelogio();

        atualizarProgresso(Leituras.localizacao);   // mostra o card de progresso se ja tem rota tracada

        handler.post(timerTelemetria);   // manda a primeira agora e depois a cada 20 s
        agendarCheckin();                // o check-in so conta durante a rota
    }

    private void encerrarTrajeto() {
        trajetoAtivo = false;
        handler.removeCallbacks(timerTelemetria);

        long minutos = (System.currentTimeMillis() - inicioTrajeto) / 60000;
        String km = String.format(Locale.getDefault(), "%.2f km", distancia / 1000);

        pintarBotaoPrincipal(false);
        cardProgresso.setVisibility(View.GONE);
        txtTrajetoKm.setText(km);
        txtTrajetoMin.setText(formatarTempo((System.currentTimeMillis() - inicioTrajeto) / 1000));
        velocidadeAtual = 0;
        txtTrajetoVel.setText("0,0 km/h");
        txtTrajetoStatus.setText("Rota encerrada");

        // sem trajeto nao tem check-in
        handler.removeCallbacks(timerCheckin);
        handler.removeCallbacks(semRespostaCheckin);
        proximoCheckinMs = 0;
        if (dialogCheckin != null && dialogCheckin.isShowing()) {
            dialogCheckin.dismiss();
        }
        atualizarRelogio();

        // o "extra" aparece na janela de detalhes do historico
        long fimMs = System.currentTimeMillis();
        long duracaoS = (fimMs - inicioTrajeto) / 1000;
        double velocidadeMedia = 0;
        if (duracaoS > 0) {
            velocidadeMedia = distancia / (double) duracaoS * 3.6;   // m/s para km/h
        }
        SimpleDateFormat formatoHora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String extra = "Meio de transporte: " + NOMES_MEIOS[meioAtual] + "\n" + String.format(Locale.getDefault(),
                "Distância: %.0f m\nDuração: %d min %02d s\nVelocidade média: %.1f km/h\nInício: %s\nFim: %s\n"
                        + "Pontos no mapa: %d\nTelemetrias enviadas: %d (%d falharam)",
                distancia, duracaoS / 60, duracaoS % 60, velocidadeMedia,
                formatoHora.format(new Date(inicioTrajeto)), formatoHora.format(new Date(fimMs)),
                linhaPercurso.getPoints().size(), telemetriasOk, telemetriasFalha);

        banco.registrarEvento(Sessao.id(requireContext()), "Rota de " + km, minutos + " min", "concluído", extra);
    }

    private final Runnable timerTelemetria = new Runnable() {
        @Override
        public void run() {
            // com a tela fechada o GPS esta desligado, entao nao manda dado velho
            if (telaAberta) {
                enviarTelemetria();
            }
            mostrarTrajeto();
            handler.postDelayed(this, INTERVALO_TELEMETRIA_MS);
        }
    };

    private void enviarTelemetria() {
        String dados = Api.montarDados(requireContext(), 1, 0);   // 1 = telemetria normal

        executor.execute(() -> {
            boolean enviado = Api.enviar(dados);
            String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

            naUi(() -> {
                if (enviado) {
                    statusTelemetria = "enviada às " + hora;
                    telemetriasOk++;
                } else {
                    statusTelemetria = "falhou às " + hora;
                    telemetriasFalha++;
                }
                if (trajetoAtivo) {
                    mostrarTrajeto();
                }
            });
        });
    }

    private void mostrarTrajeto() {
        txtTrajetoKm.setText(String.format(Locale.getDefault(), "%.2f km", distancia / 1000));
        txtTrajetoVel.setText(String.format(Locale.getDefault(), "%.1f km/h", velocidadeAtual));

        if (statusTelemetria.isEmpty()) {
            txtTrajetoStatus.setText("Enviando a primeira telemetria...");
        } else {
            txtTrajetoStatus.setText("Telemetria " + statusTelemetria + " (a cada 20 s)");
        }
    }

    // ============================================================
    //  CRONOMETRO (1 em 1 segundo)
    // ============================================================

    private final Runnable timerRelogio = new Runnable() {
        @Override
        public void run() {
            atualizarRelogio();
            handler.postDelayed(this, 1000);
        }
    };

    /** Atualiza o tempo do trajeto e o texto do "proximo check-in". */
    private void atualizarRelogio() {
        long agora = System.currentTimeMillis();

        if (trajetoAtivo) {
            txtTrajetoMin.setText(formatarTempo((agora - inicioTrajeto) / 1000));
        }

        // o Perfil pode ligar ou desligar o check-in no meio do trajeto: acompanha aqui, de segundo em segundo
        boolean ligado = Config.checkinLigado(requireContext());
        if (trajetoAtivo && ligado && proximoCheckinMs == 0) {
            agendarCheckin();
        } else if (!ligado && proximoCheckinMs > 0) {
            handler.removeCallbacks(timerCheckin);
            proximoCheckinMs = 0;
        }

        if (!ligado) {
            txtProximoCheckin.setText("Check-in desligado (ative no Perfil)");
        } else if (!trajetoAtivo) {
            txtProximoCheckin.setText("O check-in começa quando você iniciar a rota");
        } else if (proximoCheckinMs > 0) {
            long faltam = (proximoCheckinMs - agora) / 1000;
            if (faltam < 0) {
                faltam = 0;
            }
            txtProximoCheckin.setText("Próximo check-in em " + formatarTempo(faltam));
        }
    }

    /** 75 segundos viram "01:15"; 3700 segundos viram "1:01:40". */
    private String formatarTempo(long segundos) {
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long resto = segundos % 60;
        if (horas > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", horas, minutos, resto);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", minutos, resto);
    }

    // ============================================================
    //  CHECK-IN DE SEGURANCA
    //  Pergunta "esta tudo bem?". Sem resposta em 30 s, abre a contagem de emergencia.
    // ============================================================

    /** O intervalo do check-in e ajustado na tela Perfil. */
    private int minutosCheckin() {
        return Config.checkinMinutos(requireContext());
    }

    private void agendarCheckin() {
        handler.removeCallbacks(timerCheckin);
        if (Config.checkinLigado(requireContext()) && trajetoAtivo) {
            long espera = minutosCheckin() * 60000L;
            proximoCheckinMs = System.currentTimeMillis() + espera;
            handler.postDelayed(timerCheckin, espera);
        } else {
            proximoCheckinMs = 0;
        }
    }

    private final Runnable timerCheckin = new Runnable() {
        @Override
        public void run() {
            mostrarCheckin();
        }
    };

    private void mostrarCheckin() {
        // o trajeto acabou enquanto esperava: nao pergunta
        if (!trajetoAtivo) {
            return;
        }
        // com a tela fechada nao da para perguntar: tenta de novo no proximo ciclo
        if (!telaAberta) {
            agendarCheckin();
            return;
        }

        dialogCheckin = new AlertDialog.Builder(requireContext())
                .setTitle("Check-in de segurança")
                .setMessage("Está tudo bem?\n\nSem resposta em 30 segundos, a contagem de emergência começa.")
                .setCancelable(false)
                .setPositiveButton("Estou bem", (dialogo, botao) -> {
                    handler.removeCallbacks(semRespostaCheckin);
                    String extra = "Resposta: Estou bem\nPergunta a cada: " + minutosCheckin() + " min\n"
                            + Api.resumoDosDados(Api.montarDados(requireContext(), 1, 0));
                    banco.registrarEvento(Sessao.id(requireContext()), "Check-in de segurança", "respondido", "tudo bem", extra);
                    agendarCheckin();
                })
                .setNegativeButton("Preciso de ajuda", (dialogo, botao) -> {
                    handler.removeCallbacks(semRespostaCheckin);
                    AlertaActivity.abrir(requireContext(), "CHECKIN", "A pessoa pediu ajuda no check-in", 5, 0);
                    agendarCheckin();
                })
                .show();

        handler.postDelayed(semRespostaCheckin, TEMPO_RESPOSTA_CHECKIN_MS);
    }

    private final Runnable semRespostaCheckin = new Runnable() {
        @Override
        public void run() {
            if (dialogCheckin != null && dialogCheckin.isShowing()) {
                dialogCheckin.dismiss();
            }
            AlertaActivity.abrir(requireContext(), "CHECKIN", "Sem resposta ao check-in de segurança", Config.contagemQueda(requireContext()), 0);
            agendarCheckin();
        }
    };

    /** Roda na thread principal, mas so se o fragment ainda estiver preso a uma tela. */
    private void naUi(Runnable acao) {
        Activity tela = getActivity();
        if (tela != null) {
            tela.runOnUiThread(acao);
        }
    }
}
