package com.finora.app.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PeruBankParserTest {
  private static final String BCP_SENDER = "notificaciones@notificacionesbcp.com.pe";
  private final PeruBankParser parser = new PeruBankParser();

  @Test
  void parsesRealBcpPedidosYaPurchase() {
    BankEmailParser.Parsed parsed = parse("19.00", "PedidosYa*Montao Nicola");
    assertEquals(new BigDecimal("19.00"), parsed.amount());
    assertEquals("PEN", parsed.currency());
    assertEquals("PedidosYa", parsed.merchant());
    assertEquals("Comida", parsed.category());
    assertEquals("Consumo en PedidosYa", parsed.description());
  }

  @Test
  void normalizesPedidosYaRegardlessOfFinalMerchant() {
    BankEmailParser.Parsed parsed = parse("22.60", "PEDIDOSYA*MCDONALDS");
    assertEquals("PedidosYa", parsed.merchant());
    assertEquals("Comida", parsed.category());
  }

  @Test
  void normalizesRappiPlatform() {
    BankEmailParser.Parsed parsed = parse("31.40", "RAPPI*STARBUCKS");
    assertEquals("Rappi", parsed.merchant());
    assertEquals("Comida", parsed.category());
  }

  @Test
  void removesDlcPrefixFromSpotify() {
    BankEmailParser.Parsed parsed = parse("18.90", "DLC*Spotify");
    assertEquals("Spotify", parsed.merchant());
    assertEquals("Suscripciones", parsed.category());
  }

  @Test
  void rejectsUrlAsMerchant() {
    BankEmailParser.Parsed parsed = parser.parse("", "Realizaste un consumo de S/ 10.00 en https://example.com.");
    assertNotEquals("https", parsed.merchant());
    assertEquals("Comercio no identificado", parsed.merchant());
  }

  @Test
  void rejectsSavingsAccountAndFollowingCopy() {
    BankEmailParser.Parsed parsed = parser.parse("",
        "Realizaste un consumo de S/ 10.00 en tu wardadito caja de ahorro. Te enviamos más información.");
    assertEquals("Comercio no identificado", parsed.merchant());
    assertNotEquals("Consumo en tu wardadito caja de ahorro. Te enviamos", parsed.description());
  }

  @Test
  void preservesValidUnknownMerchant() {
    BankEmailParser.Parsed parsed = parse("15.50", "Libreria El Virrey");
    assertEquals("Libreria El Virrey", parsed.merchant());
    assertEquals("Otros", parsed.category());
  }

  @Test
  void recognizesBcpFromSenderWithoutBankNameInBody() {
    assertTrue(parser.supports(BCP_SENDER, "Realizaste una compra por S/ 8.50"));
  }

  @Test
  void normalizesAndCategorizesMobilityPlatforms() {
    BankEmailParser.Parsed uber = parse("12.00", "UBER *TRIP");
    BankEmailParser.Parsed cabify = parse("14.00", "CABIFY");
    assertEquals("Uber", uber.merchant());
    assertEquals("Transporte", uber.category());
    assertEquals("Cabify", cabify.merchant());
    assertEquals("Transporte", cabify.category());
  }

  private BankEmailParser.Parsed parse(String amount, String merchant) {
    return parser.parse(BCP_SENDER,
        "Realizaste un consumo de S/ " + amount + " con tu Tarjeta de Débito BCP en " + merchant + ".");
  }
}
