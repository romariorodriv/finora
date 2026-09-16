package com.finora.app.category;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CategoryClassifierTest {
  private final CategoryClassifier classifier = new CategoryClassifier();

  @Test
  void classifiesKnownMerchants() {
    assertCategory("PedidosYa", MacroCategory.DIA_A_DIA, "Delivery");
    assertCategory("Rappi", MacroCategory.DIA_A_DIA, "Delivery");
    assertCategory("SUPERM METRO SN FELIPE", MacroCategory.DIA_A_DIA, "Supermercado");
    assertCategory("Uber *Trip", MacroCategory.MOVILIDAD, "Taxi / apps");
    assertCategory("Primax", MacroCategory.MOVILIDAD, "Combustible");
    assertCategory("DLC*Spotify", MacroCategory.ESTILO_DE_VIDA, "Suscripciones");
  }

  @Test
  void unknownMerchantFallsBackToOthers() {
    CategoryClassifier.Classification result = classifier.classify("Comercio Nuevo", "Compra");
    assertEquals(MacroCategory.OTROS, result.macroCategory());
    assertEquals("Otros", result.subcategory());
  }

  private void assertCategory(String merchant, MacroCategory macroCategory, String subcategory) {
    CategoryClassifier.Classification result = classifier.classify(merchant, "Compra");
    assertEquals(macroCategory, result.macroCategory());
    assertEquals(subcategory, result.subcategory());
  }
}
