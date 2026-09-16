package com.finora.app.importer;

import com.finora.app.merchant.MerchantNormalizer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PeruBankParser implements BankEmailParser {
  private enum RecognizedOperation {
    PURCHASE, REJECTED_PURCHASE, TRANSFER, CASH_WITHDRAWAL, WARDADITO_WITHDRAWAL, WARDADITO_DEPOSIT, REFUND
  }

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
      "(?:\\ben|establecimiento|comercio|nombre\\s+del\\s+comercio)\\s*:?\\s+([^\\r\\n]{2,80}?)" + MERCHANT_END,
      Pattern.CASE_INSENSITIVE);
  private static final Pattern INVALID_MERCHANT = Pattern.compile(
      "^(?:(?:https?|www)(?:\\b|[.:/])|por\\s+tu\\s+seguridad\\b|te\\s+enviamos\\b|"
          + "datos\\s+de\\s+(?:la|tu)\\s+operaci[oó]n\\b|"
          + "tu\\s+\\S*(?:guardadito|wardadito)\\s+caja\\s+de\\s+ahorro\\b)",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern REJECTED_OPERATION = Pattern.compile(
      "(?:se\\s+rechaz[oó]\\s+tu\\s+compra|compra\\s+(?:fue\\s+)?rechazada|"
          + "operaci[oó]n\\s+rechazada|transacci[oó]n\\s+rechazada|pago\\s+rechazado|"
          + "fondos\\s+insuficientes|saldo\\s+insuficiente|no\\s+tienes\\s+saldo\\s+suficiente|"
          + "no\\s+se\\s+pudo\\s+realizar)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern BCP_WARDADITO_WITHDRAWAL = Pattern.compile(
      "realizaste\\s+un\\s+retiro\\s+de\\s+(?:S/|PEN)\\s*[0-9]+(?:[.,][0-9]{1,2})?"
          + "\\s+en\\s+tu\\s+wardadito\\s+caja\\s+de\\s+ahorro",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern BCP_WARDADITO_WITHDRAWAL_SUBJECT = Pattern.compile(
      "realizaste\\s+un\\s+retiro\\s+de\\s+tu\\s+wardadito",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern BCP_WARDADITO_DEPOSIT = Pattern.compile(
      "(?:realizaste\\s+un\\s+aporte\\s+voluntario\\s+a\\s+tu\\s+wardadito|"
          + "aporte\\s+(?:voluntario\\s+)?(?:de\\s+)?(?:S/|PEN)\\s*[0-9]+(?:[.,][0-9]{1,2})?.*?wardadito)",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
  private static final Pattern BCP_TRANSFER_OTHER_BANK = Pattern.compile(
      "constancia\\s+de\\s+transferencia\\s+a\\s+otros\\s+bancos",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern BCP_TRANSFER_OWN_ACCOUNTS = Pattern.compile(
      "constancia\\s+de\\s+transferencia\\s+entre\\s+mis\\s+cuentas",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern BCP_CASH_WITHDRAWAL = Pattern.compile(
      "realizaste\\s+un\\s+retiro\\s+en\\s+un\\s+cajero\\s+autom[aÃ¡]tico\\s+BCP|"
          + "retiro\\s+de\\s+(?:S/|PEN)\\s*[0-9]+(?:[.,][0-9]{1,2})?.*?cajero",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
  private static final Pattern BCP_REFUND = Pattern.compile(
      "realizamos\\s+una\\s+devoluci[oÃ³]n\\s+de\\s+una\\s+operaci[oÃ³]n\\s+a\\s+tu\\s+tarjeta\\s+de\\s+d[eÃ©]bito\\s+BCP|"
          + "\\bdevoluci[oÃ³]n\\b.*?tarjeta\\s+de\\s+d[eÃ©]bito\\s+BCP",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

  @Override
  public boolean supports(String sender, String content) {
    return recognize(sender, content) != null;
  }

  @Override
  public Parsed parse(String content) {
    return parse("", content);
  }

  @Override
  public Parsed parse(String sender, String content) {
    String text = content == null ? "" : content;
    RecognizedOperation operation = recognize(sender, text);
    if (operation == null) {
      throw new IllegalArgumentException("Email bancario no soportado");
    }
    Matcher amountMatcher = AMOUNT.matcher(text);
    if (!amountMatcher.find()) {
      throw new IllegalArgumentException("No encontramos un monto");
    }
    BigDecimal amount = new BigDecimal(amountMatcher.group(1).replace(',', '.'));
    return switch (operation) {
      case PURCHASE, REJECTED_PURCHASE -> parsePurchase(sender, text, amount, operation);
      case TRANSFER -> new Parsed("Transferencia BCP", amount, "PEN", LocalDate.now(), "BCP", "Transferencias",
          .95, MovementType.TRANSFER, OperationStatus.COMPLETED);
      case CASH_WITHDRAWAL -> new Parsed("Retiro en cajero BCP", amount, "PEN", LocalDate.now(), "BCP", "Efectivo",
          .95, MovementType.CASH_WITHDRAWAL, OperationStatus.COMPLETED);
      case WARDADITO_WITHDRAWAL -> new Parsed("Retiro de Wardadito", amount, "PEN", LocalDate.now(), "Wardadito",
          "Ahorro", .95, MovementType.SAVINGS_WITHDRAWAL, OperationStatus.COMPLETED);
      case WARDADITO_DEPOSIT -> new Parsed("Aporte a Wardadito", amount, "PEN", LocalDate.now(), "Wardadito",
          "Ahorro", .95, MovementType.SAVINGS_DEPOSIT, OperationStatus.COMPLETED);
      case REFUND -> new Parsed("Devolucion BCP", amount, "PEN", LocalDate.now(), "BCP", "Devoluciones",
          .95, MovementType.REFUND, OperationStatus.REFUNDED);
    };
  }

  private Parsed parsePurchase(String sender, String text, BigDecimal amount, RecognizedOperation operation) {
    String rawMerchant = extractMerchant(sender, text);
    String merchant = normalizeMerchant(rawMerchant);
    return new Parsed("Consumo en " + merchant, amount, "PEN", LocalDate.now(), merchant, categorize(merchant),
        isValidMerchant(rawMerchant) ? .95 : .80, MovementType.EXPENSE,
        operation == RecognizedOperation.REJECTED_PURCHASE ? OperationStatus.REJECTED : OperationStatus.COMPLETED);
  }

  String normalizeMerchant(String rawMerchant) {
    if (!isValidMerchant(rawMerchant)) {
      return MerchantNormalizer.UNKNOWN;
    }
    return MerchantNormalizer.normalize(rawMerchant);
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

  private RecognizedOperation recognize(String sender, String content) {
    String text = content == null ? "" : content;
    if (REJECTED_OPERATION.matcher(text).find()) {
      return RecognizedOperation.REJECTED_PURCHASE;
    }
    if (BCP_WARDADITO_WITHDRAWAL_SUBJECT.matcher(text).find() || BCP_WARDADITO_WITHDRAWAL.matcher(text).find()) {
      return RecognizedOperation.WARDADITO_WITHDRAWAL;
    }
    if (BCP_WARDADITO_DEPOSIT.matcher(text).find()) {
      return RecognizedOperation.WARDADITO_DEPOSIT;
    }
    if (BCP_TRANSFER_OTHER_BANK.matcher(text).find() || BCP_TRANSFER_OWN_ACCOUNTS.matcher(text).find()) {
      return RecognizedOperation.TRANSFER;
    }
    if (BCP_CASH_WITHDRAWAL.matcher(text).find()) {
      return RecognizedOperation.CASH_WITHDRAWAL;
    }
    if (BCP_REFUND.matcher(text).find()) {
      return RecognizedOperation.REFUND;
    }
    if (BCP_PURCHASE.matcher(text).find()) {
      return RecognizedOperation.PURCHASE;
    }
    if (isBcpSender(sender)
        && text.toLowerCase(Locale.ROOT).contains("realizaste un consumo")
        && AMOUNT.matcher(text).find()
        && extractMerchant(sender, text) != null) {
      return RecognizedOperation.PURCHASE;
    }
    return null;
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
