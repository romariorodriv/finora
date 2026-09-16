package com.finora.app.category;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CategoryClassifier {
  public record Classification(MacroCategory macroCategory, String subcategory) {}

  private record Rule(MacroCategory macroCategory, String subcategory, List<String> tokens) {}

  private static final Classification OTHER = new Classification(MacroCategory.OTROS, "Otros");
  private static final List<Rule> RULES = List.of(
      new Rule(MacroCategory.DIA_A_DIA, "Delivery", List.of("pedidosya", "rappi")),
      new Rule(MacroCategory.DIA_A_DIA, "Supermercado", List.of("metro", "wong", "plaza vea", "tottus")),
      new Rule(MacroCategory.MOVILIDAD, "Taxi / apps", List.of("uber", "cabify")),
      new Rule(MacroCategory.MOVILIDAD, "Combustible", List.of("primax", "repsol", "petroperu", "petroper")),
      new Rule(MacroCategory.ESTILO_DE_VIDA, "Suscripciones",
          List.of("spotify", "netflix", "prime video", "primevideo", "paramount")),
      new Rule(MacroCategory.HOGAR, "Internet / celular", List.of("claro", "movistar", "win internet")),
      new Rule(MacroCategory.BIENESTAR, "Farmacia", List.of("inkafarma", "mifarma")),
      new Rule(MacroCategory.FINANZAS, "Seguros", List.of("rimac", "pacifico seguros")));

  public Classification classify(String merchant, String description) {
    String text = normalize((merchant == null ? "" : merchant) + " " + (description == null ? "" : description));
    for (Rule rule : RULES) {
      if (rule.tokens().stream().anyMatch(text::contains)) {
        return new Classification(rule.macroCategory(), rule.subcategory());
      }
    }
    return OTHER;
  }

  public Classification classify(String merchant, String description, String currentSubcategory) {
    Classification classified = classify(merchant, description);
    if (classified.macroCategory() != MacroCategory.OTROS) {
      return classified;
    }
    MacroCategory fromSubcategory = macroFromSubcategory(currentSubcategory);
    return fromSubcategory == null ? OTHER : new Classification(fromSubcategory, currentSubcategory);
  }

  public MacroCategory macroFromSubcategory(String subcategory) {
    String normalized = normalize(subcategory);
    if (List.of("supermercado", "restaurantes", "delivery", "comida").contains(normalized)) {
      return MacroCategory.DIA_A_DIA;
    }
    if (List.of("combustible", "taxi / apps", "taxi apps", "transporte", "estacionamiento")
        .contains(normalized)) {
      return MacroCategory.MOVILIDAD;
    }
    if (List.of("servicios", "internet / celular", "internet celular", "casa").contains(normalized)) {
      return MacroCategory.HOGAR;
    }
    if (List.of("salud", "farmacia", "deporte", "cuidado personal").contains(normalized)) {
      return MacroCategory.BIENESTAR;
    }
    if (List.of("entretenimiento", "suscripciones", "compras", "viajes", "educacion").contains(normalized)) {
      return MacroCategory.ESTILO_DE_VIDA;
    }
    if (List.of("comisiones", "seguros", "intereses").contains(normalized)) {
      return MacroCategory.FINANZAS;
    }
    if ("otros".equals(normalized)) {
      return MacroCategory.OTROS;
    }
    return null;
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
    return ascii.toLowerCase(Locale.ROOT)
        .replaceAll("[*_-]+", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }
}
