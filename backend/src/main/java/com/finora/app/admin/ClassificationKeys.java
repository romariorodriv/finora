package com.finora.app.admin;
import com.finora.app.merchant.MerchantNormalizer;
import com.finora.app.shared.error.ApiException;
import java.text.Normalizer;
import java.util.Locale;
public final class ClassificationKeys {
 private ClassificationKeys(){}
 public static String name(String value){return Normalizer.normalize(value==null?"":value.strip(),Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
 public static String merchant(String value){return name(MerchantNormalizer.normalize(value));}
 public static ApiException bad(String message){return new ApiException(400,"ADMIN_VALIDATION",message);}
 public static ApiException missing(){return new ApiException(404,"ADMIN_NOT_FOUND","Registro no encontrado.");}
 public static ApiException conflict(String message){return new ApiException(409,"ADMIN_CONFLICT",message);}
 public static String redact(String value){return value==null?"":value.replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[correo oculto]").replaceAll("\\b[A-Z]{2}\\d{10,}\\b|\\b\\d(?:[\\d -]{5,})\\d\\b","[dato oculto]");}
}
