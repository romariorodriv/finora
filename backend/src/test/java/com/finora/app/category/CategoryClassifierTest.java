package com.finora.app.category;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.finora.app.merchant.MerchantNormalizer;
import org.junit.jupiter.api.Test;

class CategoryClassifierTest {
  private final CategoryClassifier classifier = new CategoryClassifier();

  @Test
  void classifiesKnownMerchants() {
    assertCategory("Tambo 123", MacroCategory.ALIMENTACION, "Alimentacion");
    assertCategory("OXXO Peru", MacroCategory.ALIMENTACION, "Alimentacion");
    assertCategory("Plaza Vea", MacroCategory.ALIMENTACION, "Alimentacion");
    assertCategory("PedidosYa", MacroCategory.ALIMENTACION, "Alimentacion");
    assertCategory("Rappi", MacroCategory.ALIMENTACION, "Alimentacion");
    assertCategory("SUPERM METRO SN FELIPE", MacroCategory.ALIMENTACION, "Alimentacion");
    assertCategory("Uber *Trip", MacroCategory.TRANSPORTE, "Transporte");
    assertCategory("Cabify", MacroCategory.TRANSPORTE, "Transporte");
    assertCategory("Inkafarma", MacroCategory.SALUD, "Salud");
    assertCategory("Mifarma", MacroCategory.SALUD, "Salud");
    assertCategory("Claro", MacroCategory.SERVICIOS, "Servicios");
    assertCategory("Movistar", MacroCategory.SERVICIOS, "Servicios");
  }

  @Test
  void unknownMerchantFallsBackToOthers() {
    CategoryClassifier.Classification result = classifier.classify("Comercio Nuevo", "Compra");
    assertEquals(MacroCategory.OTROS, result.macroCategory());
    assertEquals("Otros", result.subcategory());
  }

  @Test
  void classifiesNormalizedMerchants() {
    assertNormalizedCategory("TAMBO 123", MacroCategory.ALIMENTACION);
    assertNormalizedCategory("OXXO PERU", MacroCategory.ALIMENTACION);
    assertNormalizedCategory("PEDIDOSYA*LIMA", MacroCategory.ALIMENTACION);
    assertNormalizedCategory("UBER *TRIP", MacroCategory.TRANSPORTE);
    assertNormalizedCategory("INKAFARMA MIRAFLORES", MacroCategory.SALUD);
    assertNormalizedCategory("CLARO POSTPAGO", MacroCategory.SERVICIOS);
  }

  @Test
  void classifiesFoodAndTransportRulesKeptByTheTaxonomy() {
    assertNormalizedCategory("ROKYS SAN MIGUEL", MacroCategory.ALIMENTACION);
    assertNormalizedCategory("SERVICENTRO PANAMERICANA", MacroCategory.TRANSPORTE);
    assertNormalizedCategory("PEAJE VILLA", MacroCategory.TRANSPORTE);
  }

  @Test
  void normalizesAndClassifiesSubscriptionPlatforms() {
    assertNormalizedMerchantCategory("DLC*SPOTIFY", "Spotify");
    assertNormalizedMerchantCategory("NETFLIX.COM", "Netflix");
    assertNormalizedMerchantCategory("PARAMOUNT PLUS", "Paramount+");
    assertNormalizedMerchantCategory("EBN*PRIME VIDEO", "Prime Video");
    assertNormalizedMerchantCategory("DISNEY PLUS", "Disney+");
    assertNormalizedMerchantCategory("HBO MAX", "Max");
    assertNormalizedMerchantCategory("MAX.COM", "Max");
    assertNormalizedMerchantCategory("YOUTUBE PREMIUM", "YouTube Premium");
    assertNormalizedMerchantCategory("APPLE MUSIC", "Apple Music");
    assertNormalizedMerchantCategory("ICLOUD", "iCloud");
    assertNormalizedMerchantCategory("GOOGLE ONE", "Google One");
  }

  @Test
  void doesNotClassifyAmbiguousGoogleDescriptorsAsSubscriptions() {
    assertNormalizedCategory("GOOGLE CHATGPT", MacroCategory.OTROS);
    assertNormalizedCategory("GOOGLE STORE", MacroCategory.OTROS);
  }

  private void assertCategory(String merchant, MacroCategory macroCategory, String subcategory) {
    CategoryClassifier.Classification result = classifier.classify(merchant, "Compra");
    assertEquals(macroCategory, result.macroCategory());
    assertEquals(subcategory, result.subcategory());
  }

  private void assertNormalizedCategory(String rawMerchant, MacroCategory macroCategory) {
    String normalized = MerchantNormalizer.normalize(rawMerchant);
    CategoryClassifier.Classification result = classifier.classify(normalized, "Compra");
    assertEquals(macroCategory, result.macroCategory());
  }

  private void assertNormalizedMerchantCategory(String rawMerchant, String expectedMerchant) {
    String normalized = MerchantNormalizer.normalize(rawMerchant);
    assertEquals(expectedMerchant, normalized);
    CategoryClassifier.Classification result = classifier.classify(normalized, "Compra");
    assertEquals(MacroCategory.SUSCRIPCIONES, result.macroCategory());
    assertEquals("Suscripciones", result.subcategory());
  }
}
