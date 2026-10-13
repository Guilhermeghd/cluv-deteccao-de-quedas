package com.example.cluv.modelo;

/** Uma linha do painel do servidor (um alerta que o app mandou para a API do professor). */
public class RegistroNuvem {

    public int id;                 // o numero que o servidor deu (#433)
    public String dataHora = "";   // "09/10/2026 19:08:59"
    public long dataMs = 0;        // a mesma hora em milissegundos (0 = nao deu para ler)
    public String usuario = "";
    public String modelo = "";     // "Xiaomi MI 9"
    public String deviceId = "";   // so o comeco (o painel mostra cortado)
    public int bateria = -1;       // -1 = nao veio
    public double latitude = 0;
    public double longitude = 0;
    public boolean temPosicao = false;

    // opcionais: se nao vieram, ficam NaN
    public float nivelQueda = Float.NaN;   // em G
    public float velocidade = Float.NaN;   // km/h
    public float temperatura = Float.NaN;
    public float pressao = Float.NaN;
}
