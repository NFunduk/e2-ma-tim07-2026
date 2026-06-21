package com.example.sabona.region;

/**
 * Jedna tačka na mapi koja predstavlja jednog registrovanog igrača
 * (specifikacija, tačka a.: "za svakog igrača koji se prilikom
 * registracije prijavi za određeni region, dodati nasumičnu tačku
 * unutar tog regiona na mapi").
 *
 * GPS pozicija (lat, lon) se generiše JEDNOM po igraču i čuva se u
 * Firestore-u (users/{uid}.mapLat, users/{uid}.mapLon) kako bi tačka
 * bila stabilna između pokretanja aplikacije, umesto da "skače" na
 * novu nasumičnu lokaciju svaki put kad se mapa otvori.
 */
public class RegionMapPoint {

    public final String uid;
    public final String username;
    public final SerbianRegion region;
    public final double lat;
    public final double lon;

    public RegionMapPoint(String uid, String username, SerbianRegion region, double lat, double lon) {
        this.uid = uid;
        this.username = username;
        this.region = region;
        this.lat = lat;
        this.lon = lon;
    }
}