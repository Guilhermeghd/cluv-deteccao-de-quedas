package com.example.cluv;

/** Uma linha do historico: queda, panico, trajeto, check-in... */
public class Evento {

    private final long id;
    private final String titulo;
    private final String detalhe;
    private final String status;
    private final long dataMs;
    private final String extra;   // varias linhas "Campo: valor" para a janela de detalhes (pode ser null)

    public Evento(long id, String titulo, String detalhe, String status, long dataMs) {
        this(id, titulo, detalhe, status, dataMs, null);
    }

    public Evento(long id, String titulo, String detalhe, String status, long dataMs, String extra) {
        this.id = id;
        this.titulo = titulo;
        this.detalhe = detalhe;
        this.status = status;
        this.dataMs = dataMs;
        this.extra = extra;
    }

    public long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public String getStatus() {
        return status;
    }

    public long getDataMs() {
        return dataMs;
    }

    public String getExtra() {
        return extra;
    }
}
