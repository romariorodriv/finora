package com.finora.app.category;

public enum MacroCategory {
  ALIMENTACION("Alimentacion"),
  TRANSPORTE("Transporte"),
  SALUD("Salud"),
  SERVICIOS("Servicios"),
  SUSCRIPCIONES("Suscripciones"),
  OTROS("Otros");

  private final String label;

  MacroCategory(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
