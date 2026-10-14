package com.example.cluv.modelo;

public class RegistroNuvem {

    public int id;
    public String dataHora = "";
    public long dataMs = 0;
    public String usuario = "";
    public String modelo = "";
    public String deviceId = "";
    public int bateria = -1;
    public double latitude = 0;
    public double longitude = 0;
    public boolean temPosicao = false;

    public float nivelQueda = Float.NaN;
    public float velocidade = Float.NaN;
    public float temperatura = Float.NaN;
    public float pressao = Float.NaN;
}
