package com.example.cluv.telas;

import com.example.cluv.R;
import com.example.cluv.api.Api;
import com.example.cluv.api.NuvemApi;
import com.example.cluv.dados.Sessao;
import com.example.cluv.dados.UsuarioDbHelper;
import com.example.cluv.modelo.Evento;
import com.example.cluv.modelo.GravidadeQueda;
import com.example.cluv.modelo.RegistroNuvem;
import com.example.cluv.util.Mapa;
import com.example.cluv.util.Tema;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tela HISTORICO: junta numa lista so
 *  - os eventos do aparelho (SQLite): alertas, cancelados, falhas, check-ins e trajetos
 *  - os alertas que estao no servidor (nuvem)
 * Um alerta que aparece nos dois vira uma linha so. Tocar num evento abre os detalhes (painel que sobe de baixo).
 * Os botoes de cima filtram a lista por tipo e a lista aparece de 10 em 10 (paginas).
 */
public class HistoricoFragment extends Fragment {

    // os filtros
    private static final int TODOS = 0;
    private static final int ALERTAS = 1;
    private static final int CANCELADOS = 2;
    private static final int FALHAS = 3;
    private static final int CHECKINS = 4;
    private static final int TRAJETOS = 5;
    private static final String[] NOMES_FILTROS = {"Todos", "Alertas", "Cancelados", "Falhas", "Check-ins", "Rotas"};

    private static final long UM_MINUTO = 60000;
    private static final long TOLERANCIA_MS = 180000;   // alerta do aparelho e do servidor "sao o mesmo" se a hora bate em 3 min
    private static final int POR_PAGINA = 10;   // quantos eventos aparecem por pagina

    private TextView txtServidorChip, txtNuvemStatus, txtNuvemVazio;
    private LinearLayout containerNuvem, containerFiltros;
    private View btnSincronizar;          // o icone de sincronizar, no canto de cima
    private View barraPaginas;
    private Button btnPaginaAnterior, btnPaginaProxima;
    private TextView txtPagina;
    private ScrollView rolagem;
    private final TextView[] botoesFiltro = new TextView[NOMES_FILTROS.length];

    private int filtroAtual = TODOS;
    private int paginaAtual = 0;   // comeca em 0 (a tela mostra "Página 1")
    private List<RegistroNuvem> registrosNuvem = new ArrayList<>();   // ultimo que veio do servidor
    private String nomeDaNuvem = "";   // o nome do usuario quando a lista da nuvem foi baixada
    private long ultimaSincronizacao = 0;
    private boolean sincronizando = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UsuarioDbHelper banco;

    /** Uma linha da lista: o evento pronto para mostrar, a que filtro ele pertence e (se tiver) o registro do servidor. */
    private static class Item {
        Evento evento;
        int tipo;
        RegistroNuvem nuvem;   // null = este evento nao esta no servidor

        Item(Evento evento, int tipo, RegistroNuvem nuvem) {
            this.evento = evento;
            this.tipo = tipo;
            this.nuvem = nuvem;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_historico, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtServidorChip = view.findViewById(R.id.txtServidorChip);
        txtNuvemStatus = view.findViewById(R.id.txtNuvemStatus);
        txtNuvemVazio = view.findViewById(R.id.txtNuvemVazio);
        containerNuvem = view.findViewById(R.id.containerNuvem);
        containerFiltros = view.findViewById(R.id.containerFiltros);
        btnSincronizar = view.findViewById(R.id.btnSincronizar);
        barraPaginas = view.findViewById(R.id.barraPaginas);
        btnPaginaAnterior = view.findViewById(R.id.btnPaginaAnterior);
        btnPaginaProxima = view.findViewById(R.id.btnPaginaProxima);
        txtPagina = view.findViewById(R.id.txtPagina);
        rolagem = view.findViewById(R.id.abaHistorico);

        banco = new UsuarioDbHelper(requireContext());

        btnSincronizar.setOnClickListener(v -> sincronizar());
        btnPaginaAnterior.setOnClickListener(v -> mudarPagina(paginaAtual - 1));
        btnPaginaProxima.setOnClickListener(v -> mudarPagina(paginaAtual + 1));
        criarBotoesFiltro();
    }

    @Override
    public void onResume() {
        super.onResume();
        atualizar();
    }

    /** As abas so escondem/mostram o fragment: ao voltar para o Historico atualiza a lista. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            atualizar();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        banco.close();
    }

    private void atualizar() {
        pintarChipServidor();

        // se o nome mudou no Perfil, a lista da nuvem de antes nao vale mais (ela e achada pelo nome)
        String nomeAgora = Sessao.nome(requireContext());
        if (!nomeAgora.equals(nomeDaNuvem)) {
            nomeDaNuvem = nomeAgora;
            registrosNuvem = new ArrayList<>();
            ultimaSincronizacao = 0;
            paginaAtual = 0;
        }

        montarLista();   // a parte do aparelho ja aparece na hora, sem esperar a internet

        // ao abrir a aba, sincroniza sozinho (no maximo uma vez por minuto)
        if (System.currentTimeMillis() - ultimaSincronizacao > UM_MINUTO) {
            sincronizar();
        }
    }

    /** "Servidor: online" fica verde, "offline" vermelho e "aguardando" cinza. */
    private void pintarChipServidor() {
        txtServidorChip.setText("Servidor: " + Api.statusServidor);

        String fundo = "#E6EAEF";
        String texto = "#5A6572";
        if (Api.statusServidor.equals("online")) {
            fundo = "#E3F2E5";
            texto = "#1B5E20";
        } else if (Api.statusServidor.equals("offline")) {
            fundo = "#FDECEA";
            texto = "#B71C1C";
        }
        txtServidorChip.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(fundo)));
        txtServidorChip.setTextColor(Color.parseColor(texto));
        txtServidorChip.setCompoundDrawableTintList(ColorStateList.valueOf(Color.parseColor(texto)));
    }

    // ============================================================
    //  FILTROS
    // ============================================================

    private void criarBotoesFiltro() {
        float densidade = getResources().getDisplayMetrics().density;
        int lado = (int) (14 * densidade);
        int altura = (int) (7 * densidade);
        int margem = (int) (8 * densidade);

        for (int i = 0; i < NOMES_FILTROS.length; i++) {
            int filtro = i;

            TextView botao = new TextView(requireContext());
            botao.setText(NOMES_FILTROS[i]);
            botao.setTextSize(13);
            botao.setBackgroundResource(R.drawable.bg_chip);
            botao.setPadding(lado, altura, lado, altura);
            botao.setClickable(true);
            botao.setOnClickListener(v -> {
                filtroAtual = filtro;
                paginaAtual = 0;   // filtro novo: volta para a primeira pagina
                montarLista();
            });

            LinearLayout.LayoutParams medidas = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            medidas.setMarginEnd(margem);
            containerFiltros.addView(botao, medidas);
            botoesFiltro[i] = botao;
        }
    }

    /** O filtro escolhido fica verde-azulado com texto branco; os outros ficam cinza. */
    private void pintarBotoesFiltro() {
        for (int i = 0; i < botoesFiltro.length; i++) {
            if (i == filtroAtual) {
                botoesFiltro[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0E7C8B")));
                botoesFiltro[i].setTextColor(Color.WHITE);
            } else {
                botoesFiltro[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E6EAEF")));
                botoesFiltro[i].setTextColor(Color.parseColor("#5A6572"));
            }
        }
    }

    // ============================================================
    //  MONTAR A LISTA (aparelho + nuvem)
    // ============================================================

    private void montarLista() {
        List<Item> itens = juntarAparelhoENuvem();

        // mais novo primeiro
        Collections.sort(itens, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.evento.getDataMs(), a.evento.getDataMs());
            }
        });

        // 1) fica so com o que o filtro escolhido deixa passar
        List<Item> filtrados = new ArrayList<>();
        for (Item item : itens) {
            if (filtroAtual == TODOS || item.tipo == filtroAtual) {
                filtrados.add(item);
            }
        }

        // 2) quantas paginas de 10 existem (sempre pelo menos 1)
        int totalPaginas = (filtrados.size() + POR_PAGINA - 1) / POR_PAGINA;
        if (totalPaginas < 1) {
            totalPaginas = 1;
        }
        if (paginaAtual > totalPaginas - 1) {
            paginaAtual = totalPaginas - 1;   // ex.: a lista encolheu depois de sincronizar
        }

        // 3) mostra so os 10 da pagina atual, com um cabecalho quando o dia muda ("Hoje", "Ontem"...)
        containerNuvem.removeAllViews();
        int inicio = paginaAtual * POR_PAGINA;
        int fim = Math.min(inicio + POR_PAGINA, filtrados.size());
        String diaDoUltimoCabecalho = "";
        for (int i = inicio; i < fim; i++) {
            Item item = filtrados.get(i);

            String dia = rotuloDoDia(item.evento.getDataMs());
            if (!dia.equals(diaDoUltimoCabecalho)) {
                diaDoUltimoCabecalho = dia;
                containerNuvem.addView(criarCabecalhoDoDia(dia));
            }

            View card = getLayoutInflater().inflate(R.layout.item_evento, containerNuvem, false);
            preencherCard(card, item);

            // tocar no card abre o painel de detalhes
            card.setOnClickListener(v -> mostrarDetalhes(item));

            containerNuvem.addView(card);
        }

        if (filtrados.isEmpty()) {
            txtNuvemVazio.setText("Nenhum evento por aqui\nOs alertas, check-ins e rotas aparecem nesta lista.");
            txtNuvemVazio.setVisibility(View.VISIBLE);
        } else {
            txtNuvemVazio.setVisibility(View.GONE);
        }

        // 4) os botoes Anterior / Proxima so aparecem se tiver mais de uma pagina
        if (totalPaginas > 1) {
            barraPaginas.setVisibility(View.VISIBLE);
        } else {
            barraPaginas.setVisibility(View.GONE);
        }
        txtPagina.setText("Página " + (paginaAtual + 1) + " de " + totalPaginas);
        btnPaginaAnterior.setEnabled(paginaAtual > 0);
        btnPaginaProxima.setEnabled(paginaAtual < totalPaginas - 1);

        pintarBotoesFiltro();
        Tema.aplicarEm(requireActivity());   // as views novas nascem claras
    }

    /** "Hoje", "Ontem" ou a data (11/10/2026). */
    private String rotuloDoDia(long dataMs) {
        Calendar evento = Calendar.getInstance();
        evento.setTimeInMillis(dataMs);
        Calendar hoje = Calendar.getInstance();
        Calendar ontem = Calendar.getInstance();
        ontem.add(Calendar.DAY_OF_YEAR, -1);

        if (mesmoDia(evento, hoje)) {
            return "Hoje";
        } else if (mesmoDia(evento, ontem)) {
            return "Ontem";
        }
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(dataMs));
    }

    private boolean mesmoDia(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private TextView criarCabecalhoDoDia(String texto) {
        float densidade = getResources().getDisplayMetrics().density;
        TextView cabecalho = new TextView(requireContext());
        cabecalho.setText(texto);
        cabecalho.setTextSize(13);
        cabecalho.setTypeface(null, Typeface.BOLD);
        cabecalho.setTextColor(Color.parseColor("#5A6572"));
        cabecalho.setPadding((int) (2 * densidade), (int) (8 * densidade), 0, (int) (6 * densidade));
        return cabecalho;
    }

    /** Cores e icone de cada tipo de evento: {fundo do icone, cor do icone, desenho}. */
    private String[] estiloDoTipo(int tipo) {
        if (tipo == CANCELADOS) {
            return new String[]{"#E6EAEF", "#5A6572"};
        } else if (tipo == FALHAS) {
            return new String[]{"#FEF3C7", "#6B2E07"};
        } else if (tipo == CHECKINS) {
            return new String[]{"#E3F2E5", "#1B5E20"};
        } else if (tipo == TRAJETOS) {
            return new String[]{"#D6EDF0", "#0B5560"};
        }
        return new String[]{"#FDECEA", "#B71C1C"};   // alertas (queda e panico)
    }

    private int iconeDoTipo(int tipo) {
        if (tipo == CANCELADOS) {
            return R.drawable.ic_fechar;
        } else if (tipo == CHECKINS) {
            return R.drawable.ic_relogio;
        } else if (tipo == TRAJETOS) {
            return R.drawable.ic_trajeto;
        }
        return R.drawable.ic_alerta;   // alertas e falhas
    }

    /** Fundo e texto do selo de status. */
    private String[] coresDoStatus(String status) {
        if (status.equals("enviado") || status.equals("tudo bem")) {
            return new String[]{"#E3F2E5", "#1B5E20"};
        } else if (status.equals("falha no envio")) {
            return new String[]{"#FDECEA", "#B71C1C"};
        } else if (status.equals("concluído")) {
            return new String[]{"#D6EDF0", "#0B5560"};
        }
        return new String[]{"#E6EAEF", "#5A6572"};   // cancelado
    }

    private void preencherCard(View card, Item item) {
        Evento e = item.evento;
        String[] estilo = estiloDoTipo(item.tipo);

        ImageView icone = card.findViewById(R.id.icoEvento);
        icone.setImageResource(iconeDoTipo(item.tipo));
        icone.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(estilo[0])));
        icone.setImageTintList(ColorStateList.valueOf(Color.parseColor(estilo[1])));

        ((TextView) card.findViewById(R.id.txtEventoTitulo)).setText(e.getTitulo());

        TextView detalhe = card.findViewById(R.id.txtEventoDetalhe);
        if (e.getDetalhe() == null || e.getDetalhe().isEmpty()) {
            detalhe.setVisibility(View.GONE);
        } else {
            detalhe.setText(e.getDetalhe());
        }

        ((TextView) card.findViewById(R.id.txtEventoHora)).setText(
                new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(e.getDataMs())));

        TextView status = card.findViewById(R.id.txtEventoStatus);
        String[] cores = coresDoStatus(e.getStatus());
        status.setText(e.getStatus());
        status.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(cores[0])));
        status.setTextColor(Color.parseColor(cores[1]));
    }

    private void mudarPagina(int novaPagina) {
        paginaAtual = novaPagina;
        montarLista();
        rolagem.post(() -> rolagem.smoothScrollTo(0, 0));   // volta para o topo da lista
    }

    /** Pega os eventos do SQLite e os alertas da nuvem e tira os repetidos. */
    private List<Item> juntarAparelhoENuvem() {
        List<Item> itens = new ArrayList<>();
        boolean[] nuvemJaUsado = new boolean[registrosNuvem.size()];

        // 1) o que esta no aparelho
        List<Evento> locais = banco.listarEventos(Sessao.id(requireContext()), 200);
        for (Evento local : locais) {
            int tipo = tipoDoEvento(local);
            RegistroNuvem doServidor = null;

            // alerta enviado: ve se o servidor tambem tem (hora quase igual)
            if (tipo == ALERTAS && local.getStatus().equals("enviado")) {
                int achado = acharNaNuvem(local.getDataMs(), nuvemJaUsado);
                if (achado >= 0) {
                    nuvemJaUsado[achado] = true;
                    doServidor = registrosNuvem.get(achado);
                }
            }
            itens.add(new Item(local, tipo, doServidor));
        }

        // 2) o que so o servidor tem (ex.: enviado por outra instalacao do app)
        for (int i = 0; i < registrosNuvem.size(); i++) {
            if (!nuvemJaUsado[i]) {
                itens.add(new Item(eventoSoDaNuvem(registrosNuvem.get(i)), ALERTAS, registrosNuvem.get(i)));
            }
        }
        return itens;
    }

    /** Em que filtro cada evento do aparelho entra. */
    private int tipoDoEvento(Evento e) {
        // "Trajeto de ..." e o nome antigo: eventos salvos antes da troca de nome continuam no filtro certo
        if (e.getTitulo().startsWith("Rota") || e.getTitulo().startsWith("Trajeto")) {
            return TRAJETOS;
        }
        if (e.getStatus().equals("cancelado")) {
            return CANCELADOS;
        }
        if (e.getStatus().equals("falha no envio")) {
            return FALHAS;
        }
        if (e.getStatus().equals("tudo bem")) {
            return CHECKINS;
        }
        return ALERTAS;
    }

    /** Devolve a posicao do registro da nuvem com a hora mais perto (e ainda nao usado), ou -1. */
    private int acharNaNuvem(long dataMs, boolean[] jaUsado) {
        int melhor = -1;
        long menorDiferenca = TOLERANCIA_MS + 1;
        for (int i = 0; i < registrosNuvem.size(); i++) {
            if (jaUsado[i] || registrosNuvem.get(i).dataMs <= 0) {
                continue;
            }
            long diferenca = Math.abs(registrosNuvem.get(i).dataMs - dataMs);
            if (diferenca < menorDiferenca) {
                menorDiferenca = diferenca;
                melhor = i;
            }
        }
        return melhor;
    }

    /** Alerta que so o servidor tem. */
    private Evento eventoSoDaNuvem(RegistroNuvem r) {
        String titulo;
        // so quedas de verdade mandam o nivel; panico e check-in chegam sem ele
        if (Float.isNaN(r.nivelQueda)) {
            titulo = "Alerta enviado";
        } else {
            String gravidade = GravidadeQueda.classificar(r.nivelQueda * 9.8f);
            titulo = String.format(Locale.getDefault(), "Queda %s (%.1f G)", gravidade, r.nivelQueda);
        }

        String detalhe = "#" + r.id;
        if (r.bateria >= 0) {
            detalhe += " · bateria " + r.bateria + "%";
        }
        return new Evento(r.id, titulo, detalhe, "enviado", r.dataMs);
    }

    // ============================================================
    //  JANELA DE DETALHES (o botao "+")
    // ============================================================

    private void mostrarDetalhes(Item item) {
        Evento e = item.evento;
        RegistroNuvem r = item.nuvem;
        String quando = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date(e.getDataMs()));

        String texto = "Quando: " + quando + "\nStatus: " + e.getStatus();
        if (e.getDetalhe() != null && !e.getDetalhe().isEmpty()) {
            texto += "\nDetalhe: " + e.getDetalhe();
        }

        // o que o app guardou na hora do evento (contagem, resultado, posicao, sensores, distancia...)
        boolean temExtra = e.getExtra() != null && !e.getExtra().isEmpty();
        if (temExtra) {
            texto += "\n" + e.getExtra();
        }

        if (r != null) {
            // alerta que so o servidor tem (nao tem "extra"): usa os dados que vieram de la
            if (!temExtra) {
                if (!r.modelo.isEmpty()) {
                    texto += "\nAparelho: " + r.modelo;
                }
                if (r.bateria >= 0) {
                    texto += "\nBateria: " + r.bateria + "%";
                }
                if (!Float.isNaN(r.nivelQueda)) {
                    texto += String.format(Locale.getDefault(), "\nIntensidade: %.1f G (queda %s)",
                            r.nivelQueda, GravidadeQueda.classificar(r.nivelQueda * 9.8f));
                }
                if (r.temPosicao) {
                    texto += String.format(Locale.US, "\nLocalização: %.6f, %.6f", r.latitude, r.longitude);
                }
                if (!Float.isNaN(r.velocidade)) {
                    texto += String.format(Locale.getDefault(), "\nVelocidade: %.1f km/h", r.velocidade);
                }
                if (!Float.isNaN(r.temperatura)) {
                    texto += String.format(Locale.getDefault(), "\nTemperatura: %.1f °C", r.temperatura);
                }
                if (!Float.isNaN(r.pressao)) {
                    texto += String.format(Locale.getDefault(), "\nPressão: %.1f hPa", r.pressao);
                }
            }
            texto += "\nRegistro: #" + r.id;
        } else if (item.tipo == ALERTAS && e.getStatus().equals("enviado")) {
            texto += "\nRegistro: aguardando o servidor (toque em Sincronizar)";
        }

        // Mapa.configurar() tem que vir ANTES de criar o MapView (o layout do painel tem um)
        Mapa.configurar(requireContext());
        View raiz = getLayoutInflater().inflate(R.layout.sheet_evento, null);

        // cabecalho do painel: icone do tipo, titulo e selo de status
        String[] estilo = estiloDoTipo(item.tipo);
        ImageView icone = raiz.findViewById(R.id.icoSheet);
        icone.setImageResource(iconeDoTipo(item.tipo));
        icone.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(estilo[0])));
        icone.setImageTintList(ColorStateList.valueOf(Color.parseColor(estilo[1])));
        ((TextView) raiz.findViewById(R.id.txtSheetTitulo)).setText(e.getTitulo());

        TextView selo = raiz.findViewById(R.id.txtSheetStatus);
        String[] coresStatus = coresDoStatus(e.getStatus());
        selo.setText(e.getStatus());
        selo.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(coresStatus[0])));
        selo.setTextColor(Color.parseColor(coresStatus[1]));

        // cada linha "Rotulo: valor" do texto vira uma linha do painel (o "Status" ja aparece no selo)
        LinearLayout linhas = raiz.findViewById(R.id.containerSheetLinhas);
        for (String linha : texto.split("\n")) {
            int corte = linha.indexOf(": ");
            if (corte <= 0 || linha.startsWith("Status")) {
                continue;
            }
            View celula = getLayoutInflater().inflate(R.layout.item_detalhe_linha, linhas, false);
            ((TextView) celula.findViewById(R.id.txtLinhaRotulo)).setText(linha.substring(0, corte));
            ((TextView) celula.findViewById(R.id.txtLinhaValor)).setText(linha.substring(corte + 2));
            linhas.addView(celula);
        }

        // mapinha da posicao (some se o evento nao tem posicao)
        View tituloMapa = raiz.findViewById(R.id.txtSheetTituloMapa);
        MapView mapa = raiz.findViewById(R.id.mapaSheet);
        double[] posicao = acharPosicao(item);
        if (posicao == null) {
            tituloMapa.setVisibility(View.GONE);
            mapa.setVisibility(View.GONE);
        } else {
            GeoPoint ponto = new GeoPoint(posicao[0], posicao[1]);
            mapa.setTileSource(Mapa.fonteDasRuas());
            mapa.setMultiTouchControls(true);
            mapa.setBackgroundResource(R.drawable.bg_mapa);   // cantos arredondados
            mapa.setClipToOutline(true);
            // o mapa esta dentro de uma area que rola: sem isso, arrastar o mapa rolaria o painel
            mapa.setOnTouchListener((v, evento) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
            mapa.getController().setZoom(16.0);
            mapa.getController().setCenter(ponto);

            Marker marcador = new Marker(mapa);
            marcador.setPosition(ponto);
            marcador.setTitle(e.getTitulo());
            marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapa.getOverlays().add(marcador);
        }

        BottomSheetDialog painel = new BottomSheetDialog(requireContext());
        painel.setContentView(raiz);
        ((View) raiz.getParent()).setBackgroundColor(Color.TRANSPARENT);   // deixa aparecer o fundo arredondado do layout
        painel.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        painel.getBehavior().setDraggable(false);   // arrastar e para mexer no mapa, nao para fechar (o botao Fechar fecha)
        raiz.findViewById(R.id.btnFecharSheet).setOnClickListener(v -> painel.dismiss());
        painel.setOnDismissListener(d -> mapa.onDetach());   // solta o mapa quando o painel fecha

        Tema.aplicarNaView(raiz);   // o painel e outra janela: pinta no tema claro/escuro atual
        painel.show();
    }

    /** Acha latitude e longitude do evento: primeiro no registro do servidor, depois no texto "Localização: lat, lon". */
    private double[] acharPosicao(Item item) {
        if (item.nuvem != null && item.nuvem.temPosicao) {
            return new double[]{item.nuvem.latitude, item.nuvem.longitude};
        }
        String extra = item.evento.getExtra();
        if (extra != null) {
            Matcher m = Pattern.compile("Localização: (-?[0-9.]+), (-?[0-9.]+)").matcher(extra);
            if (m.find()) {
                return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))};
            }
        }
        return null;
    }

    // ============================================================
    //  NUVEM: baixar os alertas deste usuario do servidor
    // ============================================================

    private void sincronizar() {
        if (sincronizando) {
            return;
        }
        sincronizando = true;
        btnSincronizar.setEnabled(false);
        btnSincronizar.setAlpha(0.4f);
        txtNuvemStatus.setText("Sincronizando com o servidor...");

        Context contexto = requireContext().getApplicationContext();

        // rede nunca na thread principal
        executor.execute(() -> {
            try {
                List<RegistroNuvem> registros = NuvemApi.buscarMeusAlertas(contexto);
                naUi(() -> aoSincronizar(registros));
            } catch (Exception e) {
                naUi(() -> {
                    sincronizando = false;
                    btnSincronizar.setEnabled(true);
                    btnSincronizar.setAlpha(1f);
                    // a lista do aparelho continua na tela
                    txtNuvemStatus.setText("Sem sincronizar com o servidor (" + e.getMessage() + "). Mostrando só o aparelho.");
                });
            }
        });
    }

    private void aoSincronizar(List<RegistroNuvem> registros) {
        sincronizando = false;
        ultimaSincronizacao = System.currentTimeMillis();
        btnSincronizar.setEnabled(true);
        btnSincronizar.setAlpha(1f);
        registrosNuvem = registros;

        String hora = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        txtNuvemStatus.setText("Sincronizado às " + hora + " · " + registros.size() + " alerta(s) no servidor");
        montarLista();
    }

    // ============================================================
    //  LINHA DA LISTA
    // ============================================================

    /** Preenche uma linha do historico. E "static" porque a tela Inicio tambem usa (no "Ultimo evento"). */
    public static void preencherLinha(View linha, Evento evento) {
        TextView hora = linha.findViewById(R.id.txtItemHora);
        TextView titulo = linha.findViewById(R.id.txtItemTitulo);
        TextView detalhe = linha.findViewById(R.id.txtItemDetalhe);
        TextView status = linha.findViewById(R.id.txtItemStatus);

        hora.setText(new SimpleDateFormat("dd/MM\nHH:mm", Locale.getDefault()).format(new Date(evento.getDataMs())));
        titulo.setText(evento.getTitulo());
        status.setText(evento.getStatus());

        if (evento.getDetalhe() == null || evento.getDetalhe().isEmpty()) {
            detalhe.setVisibility(View.GONE);
        } else {
            detalhe.setText(evento.getDetalhe());
        }

        // cor do selo de status
        String fundo, texto;
        if (evento.getStatus().equals("enviado") || evento.getStatus().equals("tudo bem")) {
            fundo = "#E3F2E5";   // verde
            texto = "#1B5E20";
        } else if (evento.getStatus().equals("falha no envio")) {
            fundo = "#FDECEA";   // vermelho
            texto = "#B71C1C";
        } else if (evento.getStatus().equals("concluído")) {
            fundo = "#D6EDF0";   // azul
            texto = "#0B5560";
        } else {
            fundo = "#E6EAEF";   // cinza (cancelado)
            texto = "#3A4651";
        }
        status.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(fundo)));
        status.setTextColor(Color.parseColor(texto));
    }

    /** Roda na thread principal, mas so se o fragment ainda estiver preso a uma tela. */
    private void naUi(Runnable acao) {
        Activity tela = getActivity();
        if (tela != null) {
            tela.runOnUiThread(acao);
        }
    }
}
