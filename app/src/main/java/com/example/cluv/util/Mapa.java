package com.example.cluv.util;

import android.content.Context;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.MapTileIndex;

import java.io.File;

/**
 * Configuracao do mapa (osmdroid) que as telas dividem: o mapa do Trajeto e o mapinha da janela de detalhes do Historico.
 */
public class Mapa {

    /**
     * IMPORTANTE: chamar ANTES de criar (inflar) qualquer MapView. O MapView ja comeca a baixar o mapa
     * quando e criado, e sem o User-Agent certo o servidor de mapas bloqueia (erro 403).
     */
    public static void configurar(Context contexto) {
        IConfigurationProvider conf = Configuration.getInstance();
        conf.load(contexto, contexto.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));

        // o servidor de mapas exige que o app se identifique de forma propria (nao vale o nome padrao do osmdroid)
        conf.setUserAgentValue("Cluv-AppFaculdade/1.0 (" + contexto.getPackageName() + ")");

        // cache dentro da pasta do app (nao precisa de permissao de armazenamento)
        conf.setOsmdroidBasePath(new File(contexto.getCacheDir(), "osmdroid_v2"));
        conf.setOsmdroidTileCache(new File(contexto.getCacheDir(), "osmdroid_v2/tiles"));
    }

    /**
     * Mapa de ruas do Esri: gratuito e sem chave. O servidor oficial do OpenStreetMap bloqueou o app (403)
     * e o Carto pede chave. O endereco do tile do Esri e zoom/y/x (o padrao do osmdroid e zoom/x/y).
     */
    public static OnlineTileSourceBase fonteDasRuas() {
        return new OnlineTileSourceBase("EsriRuas", 0, 19, 256, ".jpg",
                new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"},
                "Esri, HERE, Garmin, OpenStreetMap contributors") {
            @Override
            public String getTileURLString(long indice) {
                return getBaseUrl() + MapTileIndex.getZoom(indice) + "/" + MapTileIndex.getY(indice)
                        + "/" + MapTileIndex.getX(indice);
            }
        };
    }
}
