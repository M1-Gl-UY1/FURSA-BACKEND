package com.fursa.fursa_backend.model.enumeration;

/**
 * V2 G.5 (05/06/2026) : type d'une valeur stockee dans {@code app_setting}.
 * Permet au service de parser la string {@code valeur} dans le bon type
 * Java et au frontend d'afficher le bon input (number / text / checkbox).
 */
public enum TypeSetting {
    INTEGER,
    LONG,
    DECIMAL,
    BOOLEAN,
    STRING,
}
