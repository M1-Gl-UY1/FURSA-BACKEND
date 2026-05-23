package com.fursa.fursa_backend.model.enumeration;

import java.util.List;
import java.util.Map;

/**
 * P1 (reunion Hugh 22/05/2026) : pays cibles de FURSA pour MVP.
 *
 * Liste figee de 10 pays africains prioritaires + leur devise locale.
 * Pour V2 : etendre via une table SQL `pays` + integration API externe
 * (countrystatecity.in ou GeoNames) pour les villes precises.
 */
public enum Pays {
    TZ("Tanzanie", "TZS", List.of("Zanzibar", "Dar es Salaam", "Arusha", "Mwanza", "Dodoma")),
    KE("Kenya", "KES", List.of("Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret")),
    CI("Cote d'Ivoire", "XOF", List.of("Abidjan", "Yamoussoukro", "Bouake", "San Pedro", "Korhogo")),
    CM("Cameroun", "XAF", List.of("Yaounde", "Douala", "Bafoussam", "Bamenda", "Garoua")),
    SN("Senegal", "XOF", List.of("Dakar", "Thies", "Saint-Louis", "Mbour", "Ziguinchor")),
    NG("Nigeria", "NGN", List.of("Lagos", "Abuja", "Kano", "Ibadan", "Port Harcourt")),
    GH("Ghana", "GHS", List.of("Accra", "Kumasi", "Tamale", "Sekondi-Takoradi", "Cape Coast")),
    RW("Rwanda", "RWF", List.of("Kigali", "Butare", "Gisenyi", "Ruhengeri", "Cyangugu")),
    UG("Ouganda", "UGX", List.of("Kampala", "Entebbe", "Jinja", "Mbarara", "Gulu")),
    EG("Egypte", "EGP", List.of("Le Caire", "Alexandrie", "Hurghada", "Charm el-Cheikh", "Louxor"));

    private final String nomFR;
    private final String deviseISO;
    private final List<String> villesPrincipales;

    Pays(String nomFR, String deviseISO, List<String> villesPrincipales) {
        this.nomFR = nomFR;
        this.deviseISO = deviseISO;
        this.villesPrincipales = villesPrincipales;
    }

    public String getNomFR() { return nomFR; }
    public String getDeviseISO() { return deviseISO; }
    public List<String> getVillesPrincipales() { return villesPrincipales; }
    public String getCode() { return name(); }

    /** Lookup statique pour les endpoints REST. */
    public static Map<String, String> codesVersNoms() {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (Pays p : values()) m.put(p.name(), p.nomFR);
        return m;
    }
}
