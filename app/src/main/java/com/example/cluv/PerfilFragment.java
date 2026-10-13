package com.example.cluv;

import com.example.cluv.api.NtfyApi;
import com.example.cluv.dados.Config;
import com.example.cluv.dados.Sessao;
import com.example.cluv.dados.UsuarioDbHelper;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;


import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela PERFIL: o usuario mexe nos dados dele e nos ajustes de seguranca.
 *  - nome (botao Editar: mudar o nome apaga o historico, e a janela avisa isso)
 *  - check-in de seguranca: liga/desliga e de quanto em quanto tempo pergunta (botoes - e +)
 *  - contagem da queda (botoes - e +)
 *  - avisos aos contatos pelo ntfy (canal, convite, teste)
 *  - teste de sensores (abre uma janela) e simulacao de queda
 *  - sair da conta
 * Tudo salva na hora, nao tem botao "Salvar".
 */
public class PerfilFragment extends Fragment {

    private TextView txtAvatarPerfil, txtNomePerfil, txtEmailPerfil, txtCanalNtfy;
    private TextView txtCheckinValor, txtContagemValor;
    private MaterialSwitch switchCheckinPerfil, switchAvisarContatos;
    private Button btnTesteAviso;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private UsuarioDbHelper banco;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_perfil, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtAvatarPerfil = view.findViewById(R.id.txtAvatarPerfil);
        txtNomePerfil = view.findViewById(R.id.txtNomePerfil);
        txtEmailPerfil = view.findViewById(R.id.txtEmailPerfil);
        txtCanalNtfy = view.findViewById(R.id.txtCanalNtfy);
        txtCheckinValor = view.findViewById(R.id.txtCheckinValor);
        txtContagemValor = view.findViewById(R.id.txtContagemValor);
        switchCheckinPerfil = view.findViewById(R.id.switchCheckinPerfil);
        switchAvisarContatos = view.findViewById(R.id.switchAvisarContatos);
        btnTesteAviso = view.findViewById(R.id.btnTesteAviso);

        banco = new UsuarioDbHelper(requireContext());

        // nome
        view.findViewById(R.id.btnEditarNome).setOnClickListener(v -> abrirJanelaNome());

        // check-in e contagem: os botoes - e + salvam na hora
        switchCheckinPerfil.setOnClickListener(v -> {
            boolean ligado = switchCheckinPerfil.isChecked();
            Config.salvarCheckinLigado(requireContext(), ligado);
            if (ligado) {
                Toast.makeText(requireContext(), "Check-in ligado", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Check-in desligado", Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.btnCheckinMenos).setOnClickListener(v -> mudarCheckin(false));
        view.findViewById(R.id.btnCheckinMais).setOnClickListener(v -> mudarCheckin(true));
        view.findViewById(R.id.btnContagemMenos).setOnClickListener(v -> mudarContagem(false));
        view.findViewById(R.id.btnContagemMais).setOnClickListener(v -> mudarContagem(true));

        // avisos aos contatos (ntfy)
        switchAvisarContatos.setOnClickListener(v -> {
            Config.salvarAvisarContatos(requireContext(), switchAvisarContatos.isChecked());
            if (switchAvisarContatos.isChecked()) {
                Toast.makeText(requireContext(), "Os contatos serão avisados", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Os contatos não serão avisados", Toast.LENGTH_SHORT).show();
            }
        });
        view.findViewById(R.id.btnCopiarCanal).setOnClickListener(v -> copiarCanal());
        view.findViewById(R.id.btnConvidar).setOnClickListener(v -> convidarContato());
        btnTesteAviso.setOnClickListener(v -> enviarTesteAviso());
        view.findViewById(R.id.btnNovoCanal).setOnClickListener(v -> perguntarNovoCanal());

        // ferramentas
        view.findViewById(R.id.btnTestarSensores).setOnClickListener(v ->
                ((MainActivity) requireActivity()).abrirTesteSensores());
        view.findViewById(R.id.btnSimularQueda).setOnClickListener(v ->
                ((MainActivity) requireActivity()).simularQueda());
        view.findViewById(R.id.btnSairPerfil).setOnClickListener(v ->
                ((MainActivity) requireActivity()).pedirSaida());
    }

    @Override
    public void onResume() {
        super.onResume();
        carregarDados();
    }

    /** As abas so escondem/mostram o fragment: ao voltar para o Perfil mostra os dados de novo. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            carregarDados();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        banco.close();
    }

    private void carregarDados() {
        String nome = Sessao.nome(requireContext());
        txtNomePerfil.setText(nome);
        txtEmailPerfil.setText(Sessao.email(requireContext()));
        if (nome.isEmpty()) {
            txtAvatarPerfil.setText("?");
        } else {
            txtAvatarPerfil.setText(nome.substring(0, 1).toUpperCase());
        }

        switchCheckinPerfil.setChecked(Config.checkinLigado(requireContext()));
        txtCheckinValor.setText(Config.checkinMinutos(requireContext()) + " min");
        txtContagemValor.setText(Config.contagemQueda(requireContext()) + " s");

        switchAvisarContatos.setChecked(Config.avisarContatos(requireContext()));
        txtCanalNtfy.setText(Config.topicoNtfy(requireContext()));
    }

    // ============================================================
    //  NOME
    // ============================================================

    /** Janela com o campo do nome e o aviso de que mudar o nome apaga o historico. */
    private void abrirJanelaNome() {
        View conteudo = getLayoutInflater().inflate(R.layout.dialog_nome, null);
        EditText campo = conteudo.findViewById(R.id.editNomeDialogo);
        campo.setText(Sessao.nome(requireContext()));
        campo.setSelection(campo.getText().length());

        AlertDialog janela = new AlertDialog.Builder(requireContext())
                .setTitle("Mudar o nome")
                .setView(conteudo)
                .setPositiveButton("Mudar e apagar", null)   // o clique e definido abaixo, para nao fechar com erro
                .setNegativeButton("Cancelar", null)
                .create();
        janela.show();

        // o botao que apaga fica vermelho, para chamar a atencao
        janela.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#C62828"));
        janela.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String novoNome = campo.getText().toString().trim();

            if (novoNome.length() < 2) {
                campo.setError("Informe seu nome");
                campo.requestFocus();
                return;
            }
            if (novoNome.equals(Sessao.nome(requireContext()))) {
                Toast.makeText(requireContext(), "O nome não mudou", Toast.LENGTH_SHORT).show();
                janela.dismiss();
                return;
            }
            janela.dismiss();
            aplicarNovoNome(novoNome);
        });
    }

    private void aplicarNovoNome(String novoNome) {
        long id = Sessao.id(requireContext());
        banco.atualizarNome(id, novoNome);
        banco.limparEventos(id);                       // apaga o historico deste aparelho
        Sessao.atualizarNome(requireContext(), novoNome);

        Toast.makeText(requireContext(), "Nome alterado e histórico apagado", Toast.LENGTH_LONG).show();
        carregarDados();
    }

    // ============================================================
    //  CHECK-IN E CONTAGEM DA QUEDA (botoes - e +)
    // ============================================================

    /** De 1 a 4 minutos anda de 1 em 1; dai para cima, de 5 em 5. */
    private void mudarCheckin(boolean aumentar) {
        int minutos = Config.checkinMinutos(requireContext());
        if (aumentar) {
            if (minutos < 5) {
                minutos = minutos + 1;
            } else {
                minutos = minutos + 5;
            }
        } else {
            if (minutos <= 5) {
                minutos = minutos - 1;
            } else {
                minutos = minutos - 5;
            }
        }
        // os limites: 1 a 180
        if (minutos < 1) {
            minutos = 1;
        }
        if (minutos > Config.CHECKIN_MAX_MIN) {
            minutos = Config.CHECKIN_MAX_MIN;
        }
        Config.salvarCheckinMinutos(requireContext(), minutos);
        txtCheckinValor.setText(minutos + " min");
    }

    /** Anda de 5 em 5 segundos, de 10 a 60. */
    private void mudarContagem(boolean aumentar) {
        int segundos = Config.contagemQueda(requireContext());
        if (aumentar) {
            segundos = segundos + 5;
        } else {
            segundos = segundos - 5;
        }
        if (segundos < Config.CONTAGEM_MIN_S) {
            segundos = Config.CONTAGEM_MIN_S;
        }
        if (segundos > Config.CONTAGEM_MAX_S) {
            segundos = Config.CONTAGEM_MAX_S;
        }
        Config.salvarContagemQueda(requireContext(), segundos);
        txtContagemValor.setText(segundos + " s");
    }

    // ============================================================
    //  AVISOS AOS CONTATOS (ntfy)
    // ============================================================

    private void copiarCanal() {
        ClipboardManager area = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        area.setPrimaryClip(ClipData.newPlainText("Canal do Cluv", Config.topicoNtfy(requireContext())));
        Toast.makeText(requireContext(), "Canal copiado", Toast.LENGTH_SHORT).show();
    }

    /** Abre a folha de compartilhar (WhatsApp, SMS, e-mail...) com as instrucoes para o contato assinar o canal. */
    private void convidarContato() {
        String canal = Config.topicoNtfy(requireContext());
        String convite = "Oi! Quero que você receba meus alertas de segurança do app Cluv.\n\n"
                + "1) Instale o app ntfy (grátis): Google Play ou App Store\n"
                + "2) Abra o app, toque em + e assine este canal: " + canal + "\n"
                + "   (use o servidor padrão, ntfy.sh)\n\n"
                + "Se preferir pelo navegador: https://ntfy.sh/" + canal + "\n\n"
                + "Quando eu pedir ajuda ou o app detectar uma queda, você recebe uma notificação com a minha localização.";

        Intent compartilhar = new Intent(Intent.ACTION_SEND);
        compartilhar.setType("text/plain");
        compartilhar.putExtra(Intent.EXTRA_TEXT, convite);
        startActivity(Intent.createChooser(compartilhar, "Convidar contato"));
    }

    /** Manda uma notificacao de teste para o canal: quem ja assinou deve receber na hora. */
    private void enviarTesteAviso() {
        String canal = Config.topicoNtfy(requireContext());
        String nome = Sessao.nome(requireContext());

        btnTesteAviso.setEnabled(false);
        Toast.makeText(requireContext(), "Enviando o teste...", Toast.LENGTH_SHORT).show();

        // rede nunca na thread principal
        executor.execute(() -> {
            boolean enviado = NtfyApi.enviar(canal, "TESTE do Cluv",
                    nome + " está testando o aviso aos contatos. Nenhuma emergência.", 3, "white_check_mark", "");

            naUi(() -> {
                btnTesteAviso.setEnabled(true);
                if (enviado) {
                    Toast.makeText(requireContext(), "Teste enviado ao canal", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(), "Não foi possível enviar (sem internet?)", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void perguntarNovoCanal() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Gerar um novo canal?")
                .setMessage("Quem assinou o canal atual deixa de receber os seus alertas. "
                        + "Você precisará convidar os contatos de novo.\n\nUse se achar que o nome do canal vazou.")
                .setPositiveButton("Gerar novo", (dialogo, botao) -> {
                    Config.trocarTopicoNtfy(requireContext());
                    txtCanalNtfy.setText(Config.topicoNtfy(requireContext()));
                    Toast.makeText(requireContext(), "Novo canal criado", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Roda na thread principal, mas so se o fragment ainda estiver preso a uma tela. */
    private void naUi(Runnable acao) {
        Activity tela = getActivity();
        if (tela != null) {
            tela.runOnUiThread(acao);
        }
    }
}
