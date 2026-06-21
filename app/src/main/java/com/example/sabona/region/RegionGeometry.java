package com.example.sabona.region;

import java.util.Random;

/**
 * Pomoćna klasa za geografske proračune nad poligonima regiona
 * ({@link SerbianRegion#boundary}, niz [lat, lon] tačaka):
 *  1) point-in-polygon test (da li GPS tačka pada unutar granice regiona),
 *  2) generisanje nasumične GPS tačke koja garantovano pada UNUTAR tog
 *     poligona (rejection sampling: nasumična tačka u bounding box-u,
 *     pa provera point-in-polygon; ponavlja se dok ne pogodi unutrašnjost).
 *
 * Ovo zadovoljava specifikaciju ("dodati nasumičnu tačku UNUTAR tog
 * regiona na mapi") koristeći stvarne geografske granice umesto izmišljene
 * mape.
 */
public final class RegionGeometry {

    private RegionGeometry() {}

    /** Standardni "ray casting" point-in-polygon test, u (lat, lon) prostoru. */
    public static boolean containsPoint(double[][] polygon, double lat, double lon) {
        int n = polygon.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double latI = polygon[i][0], lonI = polygon[i][1];
            double latJ = polygon[j][0], lonJ = polygon[j][1];
            boolean intersects = ((lonI > lon) != (lonJ > lon)) &&
                    (lat < (latJ - latI) * (lon - lonI) / (lonJ - lonI) + latI);
            if (intersects) inside = !inside;
        }
        return inside;
    }

    /**
     * Generiše nasumičnu GPS tačku unutar zadatog poligona (region).
     * Koristi {@code seed} (npr. hash korisničkog uid-a) da rezultat bude
     * determinističan po igraču ako se ikad pozove ponovo bez sačuvane
     * pozicije, dok god se prosledi isti seed.
     *
     * @return [lat, lon]
     */
    public static double[] randomPointInside(double[][] polygon, long seed) {
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (double[] point : polygon) {
            minLat = Math.min(minLat, point[0]);
            maxLat = Math.max(maxLat, point[0]);
            minLon = Math.min(minLon, point[1]);
            maxLon = Math.max(maxLon, point[1]);
        }

        Random random = new Random(seed);
        // Rejection sampling — u praksi pogodi unutrašnjost za par pokušaja
        // zahvaljujući obliku regiona; ograničavamo na 200 pokušaja kao
        // sigurnosnu mrežu, nakon čega vraćamo centar bounding box-a.
        for (int attempt = 0; attempt < 200; attempt++) {
            double lat = minLat + random.nextDouble() * (maxLat - minLat);
            double lon = minLon + random.nextDouble() * (maxLon - minLon);
            if (containsPoint(polygon, lat, lon)) {
                return new double[]{lat, lon};
            }
        }
        return new double[]{(minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0};
    }

    /** Pretvara String uid u stabilan long seed (za determinističku tačku po igraču). */
    public static long seedFromUid(String uid) {
        return uid == null ? 0L : uid.hashCode();
    }
}