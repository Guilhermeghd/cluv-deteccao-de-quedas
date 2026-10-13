package com.example.cluv;

import com.example.cluv.api.ClimaApi;
import com.example.cluv.dados.Leituras;
import com.example.cluv.modelo.ClimaDados;
import com.example.cluv.util.Gps;
import com.example.cluv.util.Tema;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela CLIMA: pega a posicao do GPS e consulta o OpenWeatherMap.
 */
public class ClimaFragment extends Fragment implements LocationListener {

    private static final long DEZ_MINUTOS = 600000;

    private TextView txtClimaAtualizado, txtBannerTitulo, txtBannerTexto;
    private TextView txtClimaTemp, txtClimaDescricao, txtClimaSensacao;
    private TextView txtRiscoTitulo, txtRiscoMotivos;
    private TextView txtClimaVento, txtClimaRajada, txtClimaChuva, txtClimaUmidade, txtClimaPressao;
    private TextView txtClimaVisibilidade, txtClimaNuvens, txtClimaNascer, txtClimaPor;
    private ImageView imgClimaIcone, imgRisco;
    private View bannerClima, cardClimaHero, gradeClima, faixaRisco;
    private ImageButton btnClimaAtualizar;

    private TextView txtClimaCidade;
    private boolean temDados = false;
    private long ultimaConsulta = 0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clima, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtClimaAtualizado = view.findViewById(R.id.txtClimaAtualizado);
        bannerClima = view.findViewById(R.id.bannerClima);
        txtBannerTitulo = view.findViewById(R.id.txtBannerTitulo);
        txtBannerTexto = view.findViewById(R.id.txtBannerTexto);
        cardClimaHero = view.findViewById(R.id.cardClimaHero);
        txtClimaTemp = view.findViewById(R.id.txtClimaTemp);
        txtClimaDescricao = view.findViewById(R.id.txtClimaDescricao);
        txtClimaSensacao = view.findViewById(R.id.txtClimaSensacao);
        imgClimaIcone = view.findViewById(R.id.imgClimaIcone);
        faixaRisco = view.findViewById(R.id.faixaRisco);
        imgRisco = view.findViewById(R.id.imgRisco);
        txtRiscoTitulo = view.findViewById(R.id.txtRiscoTitulo);
        txtRiscoMotivos = view.findViewById(R.id.txtRiscoMotivos);
        gradeClima = view.findViewById(R.id.gradeClima);
        txtClimaVento = view.findViewById(R.id.txtClimaVento);
        txtClimaRajada = view.findViewById(R.id.txtClimaRajada);
        txtClimaChuva = view.findViewById(R.id.txtClimaChuva);
        txtClimaUmidade = view.findViewById(R.id.txtClimaUmidade);
        txtClimaPressao = view.findViewById(R.id.txtClimaPressao);
        txtClimaVisibilidade = view.findViewById(R.id.txtClimaVisibilidade);
        txtClimaNuvens = view.findViewById(R.id.txtClimaNuvens);
        txtClimaNascer = view.findViewById(R.id.txtClimaNascer);
        txtClimaPor = view.findViewById(R.id.txtClimaPor);
        btnClimaAtualizar = view.findViewById(R.id.btnClimaAtualizar);
        txtClimaCidade = view.findViewById(R.id.txtClimaCidade);

        btnClimaAtualizar.setOnClickListener(v -> consultarClima());

        // se outra tela ja achou a posicao, consulta na hora
        if (Leituras.localizacao != null) {
            consultarClima();
        } else {
            mostrarAviso("Aguardando a localização", "O clima aparece assim que o GPS achar a sua posição.");
        }
    }// fim do onCreate

    @Override
    public void onResume() {
        super.onResume();
        Gps.ligar(requireContext(), this);

        atualizarSeVelho();
    }

    /** Voltou para a aba e o clima ja tem mais de 10 minutos: atualiza. */
    private void atualizarSeVelho() {
        if (ultimaConsulta > 0 && System.currentTimeMillis() - ultimaConsulta > DEZ_MINUTOS) {
            consultarClima();
        }
    }

    /** As abas so escondem/mostram o fragment: aqui sabemos que o usuario voltou para o Clima. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            atualizarSeVelho();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Gps.desligar(requireContext(), this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    @Override
    public void onLocationChanged(Location location) {
        Leituras.localizacao = location;
        // primeira posicao recebida: ja consulta o clima
        if (ultimaConsulta == 0) {
            consultarClima();
        }
    }

    // ============================================================
    //  CONSULTA NA API DO CLIMA (GET)
    // ============================================================

    private void consultarClima() {
        Location local = Leituras.localizacao;
        if (local == null) {
            mostrarAviso("Aguardando a localização", "O clima aparece assim que o GPS achar a sua posição.");
            return;
        }
        ultimaConsulta = System.currentTimeMillis();
        txtClimaAtualizado.setText("Atualizando...");

        // rede nunca na thread principal
        executor.execute(() -> {
            try {
                ClimaDados dados = ClimaApi.consultar(local);
                naUi(() -> mostrarClima(dados));
            } catch (Exception e) {
                String motivo = e.getMessage();
                if (motivo != null && (motivo.startsWith("O servidor") || motivo.startsWith("Chave"))) {
                    naUi(() -> mostrarAviso("Não foi possível atualizar o clima", motivo));
                } else {
                    naUi(() -> mostrarAviso("Sem conexão para atualizar o clima",
                            "Tente de novo quando houver internet."));
                }
            }
        });
    }

    // ============================================================
    //  MOSTRAR NA TELA
    // ============================================================

    /** Mostra um aviso (sem GPS, sem internet...). Se ja tinha clima na tela, ele continua aparecendo. */
    private void mostrarAviso(String titulo, String texto) {
        bannerClima.setVisibility(View.VISIBLE);
        txtBannerTitulo.setText(titulo);
        txtBannerTexto.setText(texto);

        if (!temDados) {
            txtClimaAtualizado.setText("Sem dados ainda");
            cardClimaHero.setVisibility(View.GONE);
            gradeClima.setVisibility(View.GONE);
        }
    }

    private void mostrarClima(ClimaDados c) {
        temDados = true;
        bannerClima.setVisibility(View.GONE);
        cardClimaHero.setVisibility(View.VISIBLE);
        gradeClima.setVisibility(View.VISIBLE);

        String hora = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        txtClimaAtualizado.setText("Na sua posição · atualizado às " + hora);

        txtClimaTemp.setText(Math.round(c.temperatura) + "°");
        txtClimaDescricao.setText(c.descricao);
        txtClimaSensacao.setText("Sensação " + Math.round(c.sensacao) + "° · mín " + Math.round(c.minima)
                + "° · máx " + Math.round(c.maxima) + "°");
        imgClimaIcone.setImageResource(c.iconeDrawable());

        // grade de detalhes
        txtClimaVento.setText(Math.round(c.ventoKmh) + " km/h");
        if (c.rajadaKmh > 0) {
            txtClimaRajada.setText("rajadas de " + Math.round(c.rajadaKmh));
            txtClimaRajada.setVisibility(View.VISIBLE);
        } else {
            txtClimaRajada.setVisibility(View.GONE);
        }
        if (c.chuvaMmH > 0) {
            txtClimaChuva.setText(String.format(Locale.getDefault(), "%.1f mm/h", c.chuvaMmH));
        } else {
            txtClimaChuva.setText("--");
        }
        txtClimaUmidade.setText(c.umidade + "%");
        txtClimaPressao.setText(c.pressao + " hPa");
        if (c.visibilidadeM >= 0) {
            txtClimaVisibilidade.setText(String.format(Locale.getDefault(), "%.1f km", c.visibilidadeM / 1000.0));
        } else {
            txtClimaVisibilidade.setText("--");
        }
        txtClimaNuvens.setText(c.nuvens + "%");
        txtClimaNascer.setText(c.nascer);
        txtClimaPor.setText(c.por);

        // a cidade (se a API mandou)
        if (c.cidade.isEmpty()) {
            txtClimaCidade.setVisibility(View.GONE);
        } else {
            txtClimaCidade.setText(c.cidade);
            txtClimaCidade.setVisibility(View.VISIBLE);
        }

        // faixa de risco: verde, amarelo ou vermelho
        txtRiscoMotivos.setText(c.motivosRisco);
        if (c.nivelRisco == 2) {
            txtRiscoTitulo.setText("Risco de perigo");
            pintarFaixa("#FDECEA", "#B71C1C");
            imgRisco.setImageResource(R.drawable.ic_alerta);
        } else if (c.nivelRisco == 1) {
            txtRiscoTitulo.setText("Risco de atenção");
            pintarFaixa("#FEF3C7", "#6B2E07");
            imgRisco.setImageResource(R.drawable.ic_alerta);
        } else {
            txtRiscoTitulo.setText("Risco baixo");
            pintarFaixa("#E3F2E5", "#1B5E20");
            imgRisco.setImageResource(R.drawable.ic_escudo);
        }
        Tema.aplicarEm(requireActivity());   // as cores acima sao do tema claro; se estiver escuro, o Tema ajusta
    }

    private void pintarFaixa(String corFundo, String corTexto) {
        int texto = Color.parseColor(corTexto);
        faixaRisco.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(corFundo)));
        txtRiscoTitulo.setTextColor(texto);
        txtRiscoMotivos.setTextColor(texto);
        imgRisco.setImageTintList(ColorStateList.valueOf(texto));
    }


    /** Roda na thread principal, mas so se o fragment ainda estiver preso a uma tela. */
    private void naUi(Runnable acao) {
        Activity tela = getActivity();
        if (tela != null) {
            tela.runOnUiThread(acao);
        }
    }
}
