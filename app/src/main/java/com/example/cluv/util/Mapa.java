package com.example.cluv.util;

import android.content.Context;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.MapTileIndex;

import java.io.File;


public class Mapa {

    public static void configurar(Context contexto) {
        IConfigurationProvider conf = Configuration.getInstance();
        conf.load(contexto, contexto.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));

        conf.setUserAgentValue("Cluv-AppFaculdade/1.0 (" + contexto.getPackageName() + ")");
        conf.setOsmdroidBasePath(new File(contexto.getCacheDir(), "osmdroid_v2"));
        conf.setOsmdroidTileCache(new File(contexto.getCacheDir(), "osmdroid_v2/tiles"));
    }

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
