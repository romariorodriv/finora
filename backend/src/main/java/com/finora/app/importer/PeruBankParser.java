package com.finora.app.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PeruBankParser implements BankEmailParser {
  private static final String MERCHANT_END =
      "(?=\\.(?:\\s|$)|[\\r\\n]|\\s+(?:por tu seguridad|te enviamos|datos de (?:la|tu) operaci[oó]n|monto\\s*:|total del consumo)|$)";
  private static final Pattern AMOUNT =
      Pattern.compile("(?:S/|PEN)\\s*([0-9]+(?:[.,][0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
  private static final Pattern BCP_SENDER =
      Pattern.compile("(^|[<\\s])notificaciones@notificacionesbcp\\.com\\.pe([>\\s]|$)", Pattern.CASE_INSENSITIVE);
  private static final Pattern BCP_PURCHASE = Pattern.compile(
      "realizaste\\s+un\\s+consumo\\s+de\\s+(?:S/|PEN)\\s*[0-9]+(?:[.,][0-9]{1,2})?"
          + ".*?\\bBCP\\s+en\\s+(.{2,80}?)" + MERCHANT_END,
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern GENERIC_MERCHANT = Pattern.compile(
      "(?:\\ben|establecimiento|comercio)\\s+([^\\r\\n]{2,80}?)" + MERCHANT_END,
      Pattern.CASE_INSENSITIVE);
  private static final Pattern INVALID_MERCHANT = Pattern.compile(
      "^(?:(?:https?|www)(?:\\b|[.:/])|por\\s+tu\\s+seguridad\\b|te\\s+enviamos\\b|"
          + "datos\\s+de\\s+(?:la|tu)\\s+operaci[oó]n\\b|"
          + "tu\\s+\\S*(?:guardadito|wardadito)\\s+caja\\s+de\\s+ahorro\\b)",
      Pattern.CASE_INSENSITIVE);

  @Override
  public boolean supports(String sender, String content) {
    if (isBcpSender(sender)) {
      return true;
    }
    String text = ((sender == null ? "" : sender) + " " + (content == null ? "" : content))
        .toLowerCase(Locale.ROOT);
    return text.contains("bcp") || text.contains("interbank") || text.contains("bbva")
        || text.contains("scotiabank") || text.contains("consumo");
  }

  @Override
  public Parsed parse(String content) {
    return parse("", content);
  }

  @Override
  public Parsed parse(String sender, String content) {
    String text = content == null ? "" : content;
    Matcher amountMatcher = AMOUNT.matcher(text);
    if (!amountMatcher.find()) {
      throw new IllegalArgumentException("No encontramos un monto");
    }
    BigDecimal amount = new BigDecimal(amountMatcher.group(1).replace(',', '.'));
    String rawMerchant = extractMerchant(sender, text);
    String merchant = normalizeMerchant(rawMerchant);
    return new Parsed("Consumo en " + merchant, amount, "PEN", LocalDate.now(), merchant,
        categorize(merchant), isValidMerchant(rawMerchant) ? .95 : .80);
  }

  String normalizeMerchant(String rawMerchant) {
    if (!isValidMerchant(rawMerchant)) {
      return "Comercio no identificado";
    }
    String merchant = rawMerchant.strip().replaceAll("\\s+", " ");
    String normalized = merchant.toUpperCase(Locale.ROOT);
    if (normalized.contains("PEDIDOSYA")) {
      return "PedidosYa";
    }
    if (normalized.contains("RAPPI")) {
      return "Rappi";
    }
    if (normalized.matches(".*\\bUBER\\b.*")) {
      return "Uber";
    }
    if (normalized.contains("CABIFY")) {
      return "Cabify";
    }
    if (normalized.contains("SPOTIFY")) {
      return "Spotify";
    }
    return merchant;
  }

  boolean isValidMerchant(String candidate) {
    if (candidate == null) {
      return false;
    }
    String merchant = candidate.strip().replaceAll("\\s+", " ");
    return merchant.length() >= 2
        && merchant.length() <= 80
        && merchant.matches(".*[\\p{L}0-9].*")
        && !INVALID_MERCHANT.matcher(merchant).find();
  }

  private String extractMerchant(String sender, String content) {
    Matcher specific = BCP_PURCHASE.matcher(content);
    if (isBcpSender(sender) && specific.find()) {
      return cleanCandidate(specific.group(1));
    }
    if (specific.find(0)) {
      return cleanCandidate(specific.group(1));
    }
    Matcher generic = GENERIC_MERCHANT.matcher(content);
    return generic.find() ? cleanCandidate(generic.group(1)) : null;
  }

  private boolean isBcpSender(String sender) {
    return sender != null && BCP_SENDER.matcher(sender).find();
  }

  private String cleanCandidate(String candidate) {
    return candidate == null ? null : candidate.strip().replaceAll("[\\s,;:-]+$", "");
  }

  private String categorize(String merchant) {
    String normalized = merchant.toLowerCase(Locale.ROOT);
    if (normalized.matches(".*(pedidosya|rappi|starbucks|restaurant|tambo).*")) {
      return "Comida";
    }
    if (normalized.matches(".*(uber|cabify|grif).*")) {
      return "Transporte";
    }
    if (normalized.matches(".*(netflix|spotify|openai|canva).*")) {
      return "Suscripciones";
    }
    if (normalized.matches(".*(inkafarma|mifarma|clinica).*")) {
      return "Salud";
    }
    return "Otros";
  }
}
