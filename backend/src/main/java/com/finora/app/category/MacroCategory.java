package com.finora.app.category;

public enum MacroCategory {
  DIA_A_DIA("Dia a dia"),
  MOVILIDAD("Movilidad"),
  HOGAR("Hogar"),
  BIENESTAR("Bienestar"),
  ESTILO_DE_VIDA("Estilo de vida"),
  FINANZAS("Finanzas"),
  OTROS("Otros");

  private final String label;

  MacroCategory(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
