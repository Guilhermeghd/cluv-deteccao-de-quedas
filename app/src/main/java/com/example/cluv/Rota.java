package com.example.cluv;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/** Uma rota de A ate B: os pontos para desenhar a linha, a distancia e o tempo. */
public class Rota {

    public List<GeoPoint> pontos = new ArrayList<>();
    public double distanciaM = 0;   // em metros
    public double duracaoS = 0;     // em segundos
}
