package com.finora.app.category;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryClassifierTest {
  private final CategoryClassifier classifier = new CategoryClassifier();

  @ParameterizedTest
  @CsvSource({
      "OXXO MIRTOS,ALIMENTACION,Alimentacion",
      "OXXO COSTA RICA,ALIMENTACION,Alimentacion",
      "OXXO TREE,ALIMENTACION,Alimentacion",
      "TAMBO ARAMBURU-C9,ALIMENTACION,Alimentacion",
      "MOLINA ROKYS,ALIMENTACION,Alimentacion",
      "PEDIDOSYA*MCDONALDS,ALIMENTACION,Alimentacion",
      "SERVICENTRO SMILE SA,TRANSPORTE,Transporte",
      "PRIMAX AVIACION,TRANSPORTE,Transporte",
      "PEAJES LIMA,TRANSPORTE,Transporte",
      "PARAMOUNT+,SUSCRIPCIONES,Suscripciones",
      "Spotify,SUSCRIPCIONES,Suscripciones"
  })
  void classifiesObservedMerchantVariants(String merchant, MacroCategory expectedMacro, String expectedCategory) {
    CategoryClassifier.Classification result = classifier.classify(merchant, "Consumo en " + merchant, "Otros");

    assertEquals(expectedMacro, result.macroCategory());
    assertEquals(expectedCategory, result.subcategory());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "FACEBK ADS",
      "PAGOEFECTIVO*FACEBOOK",
      "PLIN JUAN PRIME",
      "APPLE STORE MIRAFLORES",
      "MAXIMA LIBRERIA"
  })
  void avoidsKnownFalsePositiveSubscriptions(String merchant) {
    CategoryClassifier.Classification result = classifier.classify(merchant, "Pago en " + merchant, "Otros");

    assertEquals(MacroCategory.OTROS, result.macroCategory());
    assertEquals("Otros", result.subcategory());
  }

  @ParameterizedTest
  @CsvSource({
      "Comida,ALIMENTACION,Alimentacion",
      "Ahorro,OTROS,Otros"
  })
  void mapsLegacyCategoriesToEffectiveCatalog(String category, MacroCategory expectedMacro, String expectedCategory) {
    CategoryClassifier.Classification result = classifier.classify("Comercio sin regla", "Compra", category);

    assertEquals(expectedMacro, result.macroCategory());
    assertEquals(expectedCategory, result.subcategory());
  }
}
