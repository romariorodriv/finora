package com.finora.app.merchant;

import java.text.Normalizer;
import java.util.Locale;

public final class MerchantNormalizer {
  public static final String UNKNOWN = "Comercio no identificado";

  private MerchantNormalizer() {}

  public static String normalize(String rawMerchant) {
    if (rawMerchant == null || rawMerchant.isBlank()) {
      return UNKNOWN;
    }
    String merchant = rawMerchant.strip().replaceAll("\\s+", " ");
    String normalized = ascii(merchant).toUpperCase(Locale.ROOT);
    if (normalized.matches(".*\\bTAMBO\\+?\\b.*")) {
      return "Tambo";
    }
    if (normalized.matches(".*\\bOXXO\\b.*")) {
      return "OXXO";
    }
    if (normalized.contains("PEDIDOSYA") || normalized.contains("PEDIDOS YA")) {
      return "PedidosYa";
    }
    if (normalized.matches(".*\bROKYS\b.*")) {
      return "Rokys";
    }
    if (normalized.matches(".*\\bRAPPI\\b.*")) {
      return "Rappi";
    }
    if (normalized.matches(".*\\bUBER\\b.*")) {
      return "Uber";
    }
    if (normalized.contains("CABIFY")) {
      return "Cabify";
    }
    if (normalized.contains("PLAZA VEA")) {
      return "Plaza Vea";
    }
    if (normalized.matches(".*\\bMETRO\\b.*")) {
      return "Metro";
    }
    if (normalized.contains("WONG")) {
      return "Wong";
    }
    if (normalized.contains("TOTTUS")) {
      return "Tottus";
    }
    if (normalized.contains("INKAFARMA")) {
      return "Inkafarma";
    }
    if (normalized.contains("MIFARMA")) {
      return "Mifarma";
    }
    if (normalized.contains("CLARO")) {
      return "Claro";
    }
    if (normalized.contains("MOVISTAR")) {
      return "Movistar";
    }
    if (normalized.contains("ENTEL")) {
      return "Entel";
    }
    if (normalized.contains("PRIMAX")) {
      return "Primax";
    }
    if (normalized.contains("SERVICENTRO")) {
      return "Servicentro";
    }
    if (normalized.contains("PEAJE")) {
      return "Peaje";
    }
    if (normalized.contains("REPSOL")) {
      return "Repsol";
    }
    if (normalized.contains("SPOTIFY")) {
      return "Spotify";
    }
    if (normalized.contains("NETFLIX")) {
      return "Netflix";
    }
    if (normalized.contains("PARAMOUNT+") || normalized.contains("PARAMOUNT PLUS")
        || normalized.contains("PARAMOUNTPLUS")) {
      return "Paramount+";
    }
    if (normalized.contains("PRIME VIDEO")) {
      return "Prime Video";
    }
    if (normalized.contains("DISNEY+") || normalized.contains("DISNEY PLUS")) {
      return "Disney+";
    }
    if (normalized.contains("HBO MAX") || normalized.contains("MAX.COM")) {
      return "Max";
    }
    if (normalized.contains("YOUTUBE PREMIUM")) {
      return "YouTube Premium";
    }
    if (normalized.contains("APPLE MUSIC")) {
      return "Apple Music";
    }
    if (normalized.contains("ICLOUD")) {
      return "iCloud";
    }
    if (normalized.contains("GOOGLE ONE")) {
      return "Google One";
    }
    return merchant.isBlank() ? UNKNOWN : merchant;
  }

  private static String ascii(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
  }
}
