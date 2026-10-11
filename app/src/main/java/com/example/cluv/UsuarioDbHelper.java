package com.example.cluv;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Banco SQLite do aparelho (arquivo "cluv.db"). Guarda os usuarios e o historico de eventos de cada um.
 * O SQLiteOpenHelper cria o banco na primeira vez e abre nas seguintes.
 */
public class UsuarioDbHelper extends SQLiteOpenHelper {

    private static final String BANCO = "cluv.db";

    // versao 1 = so usuarios | versao 2 = usuarios + historico de eventos | versao 3 = eventos com "extra" (detalhes)
    // versao 4 = contatos de confianca | versao 5 = tira os contatos (o aviso agora e pelo canal do ntfy)
    private static final int VERSAO = 5;

    private static final int MAX_EVENTOS_GUARDADOS = 200;

    private static final String SQL_CRIAR_USUARIO = "CREATE TABLE usuario ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "nome TEXT NOT NULL, "
            + "email TEXT NOT NULL UNIQUE, "
            + "salt TEXT NOT NULL, "
            + "senha_hash TEXT NOT NULL, "
            + "criado_em INTEGER NOT NULL)";

    private static final String SQL_CRIAR_EVENTO = "CREATE TABLE IF NOT EXISTS evento ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "usuario_id INTEGER NOT NULL, "
            + "titulo TEXT NOT NULL, "
            + "detalhe TEXT, "
            + "status TEXT NOT NULL, "
            + "data_ms INTEGER NOT NULL, "
            + "extra TEXT)";

    public UsuarioDbHelper(Context context) {
        super(context, BANCO, null, VERSAO);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CRIAR_USUARIO);
        db.execSQL(SQL_CRIAR_EVENTO);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int versaoAntiga, int versaoNova) {
        // Quem ja tinha o app na versao 1 ganha a tabela de eventos, SEM perder os usuarios cadastrados.
        if (versaoAntiga < 2) {
            db.execSQL(SQL_CRIAR_EVENTO);   // ja nasce com a coluna "extra"
        } else if (versaoAntiga < 3) {
            // quem estava na versao 2 ganha so a coluna nova, SEM perder os eventos que ja tem
            db.execSQL("ALTER TABLE evento ADD COLUMN extra TEXT");
        }
        // quem ja tinha a tabela de contatos (versao 4) ve ela apagada; usuarios e eventos continuam
        if (versaoAntiga < 5) {
            db.execSQL("DROP TABLE IF EXISTS contato");
        }
    }

    // ============================================================
    //  USUARIOS
    // ============================================================

    /** O e-mail ja tem cadastro? */
    public boolean emailExiste(String email) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM usuario WHERE email = ?", new String[]{email})) {
            return c.moveToFirst();
        }
    }

    /** Cadastra um usuario novo. Devolve o id, ou -1 se der erro (por exemplo e-mail repetido). */
    public long cadastrar(String nome, String email, String senha) {
        String salt = SenhaUtil.gerarSalt();

        ContentValues valores = new ContentValues();
        valores.put("nome", nome);
        valores.put("email", email);
        valores.put("salt", salt);
        valores.put("senha_hash", SenhaUtil.gerarHash(senha, salt));
        valores.put("criado_em", System.currentTimeMillis());

        return getWritableDatabase().insert("usuario", null, valores);
    }

    /** Confere e-mail e senha. Devolve o usuario, ou null se estiver errado. */
    public Usuario autenticar(String email, String senha) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, nome, salt, senha_hash FROM usuario WHERE email = ?", new String[]{email})) {

            if (!c.moveToFirst()) {
                return null;
            }
            long id = c.getLong(0);
            String nome = c.getString(1);
            String salt = c.getString(2);
            String hashSalvo = c.getString(3);

            if (!SenhaUtil.conferir(senha, salt, hashSalvo)) {
                return null;
            }
            return new Usuario(id, nome, email);
        }
    }

    /** Troca o nome do usuario (o e-mail continua o mesmo). */
    public void atualizarNome(long usuarioId, String novoNome) {
        ContentValues valores = new ContentValues();
        valores.put("nome", novoNome);
        getWritableDatabase().update("usuario", valores, "id = ?", new String[]{String.valueOf(usuarioId)});
    }

    // ============================================================
    //  HISTORICO DE EVENTOS (cada usuario ve so os dele)
    // ============================================================

    /** Guarda um evento e apaga os mais antigos alem dos ultimos 200 daquele usuario. */
    public void registrarEvento(long usuarioId, String titulo, String detalhe, String status) {
        registrarEvento(usuarioId, titulo, detalhe, status, null);
    }

    /** Igual ao de cima, mas guarda tambem o "extra": linhas "Campo: valor" que aparecem na janela de detalhes. */
    public void registrarEvento(long usuarioId, String titulo, String detalhe, String status, String extra) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues valores = new ContentValues();
        valores.put("usuario_id", usuarioId);
        valores.put("titulo", titulo);
        valores.put("detalhe", detalhe);
        valores.put("status", status);
        valores.put("data_ms", System.currentTimeMillis());
        valores.put("extra", extra);
        db.insert("evento", null, valores);

        db.execSQL("DELETE FROM evento WHERE usuario_id = ? AND id NOT IN ("
                        + "SELECT id FROM evento WHERE usuario_id = ? ORDER BY data_ms DESC, id DESC LIMIT "
                        + MAX_EVENTOS_GUARDADOS + ")",
                new Object[]{usuarioId, usuarioId});
    }

    /** Os eventos mais recentes do usuario (o mais novo primeiro). */
    public List<Evento> listarEventos(long usuarioId, int limite) {
        List<Evento> lista = new ArrayList<>();

        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, titulo, detalhe, status, data_ms, extra FROM evento WHERE usuario_id = ? "
                        + "ORDER BY data_ms DESC, id DESC LIMIT " + limite,
                new String[]{String.valueOf(usuarioId)})) {

            while (c.moveToNext()) {
                lista.add(new Evento(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getString(5)));
            }
        }
        return lista;
    }

    public void limparEventos(long usuarioId) {
        getWritableDatabase().delete("evento", "usuario_id = ?", new String[]{String.valueOf(usuarioId)});
    }
}
