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
      new Rule(MacroCategory.ALIMENTACION, "Alimentacion",
          List.of("tambo", "oxxo", "pedidosya", "pedidos ya", "rappi", "metro", "wong", "plaza vea", "tottus")),
      new Rule(MacroCategory.TRANSPORTE, "Transporte",
          List.of("uber", "cabify", "primax", "repsol", "petroperu", "petroper", "grif")),
      new Rule(MacroCategory.SALUD, "Salud",
          List.of("inkafarma", "mifarma", "clinica", "farmacia", "botica")),
      new Rule(MacroCategory.SERVICIOS, "Servicios",
          List.of("claro", "movistar", "entel", "win internet", "luz", "agua", "internet")));

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
    if (List.of("alimentacion", "supermercado", "restaurantes", "delivery", "comida", "dia a dia").contains(normalized)) {
      return MacroCategory.ALIMENTACION;
    }
    if (List.of("combustible", "taxi / apps", "taxi apps", "transporte", "estacionamiento")
        .contains(normalized)) {
      return MacroCategory.TRANSPORTE;
    }
    if (List.of("salud", "farmacia", "deporte", "cuidado personal", "bienestar").contains(normalized)) {
      return MacroCategory.SALUD;
    }
    if (List.of("servicios", "internet / celular", "internet celular", "casa", "hogar").contains(normalized)) {
      return MacroCategory.SERVICIOS;
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
