package com.finora.app.auth.google;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
public final class AuthSecrets {
 private static final SecureRandom RANDOM = new SecureRandom();
 private AuthSecrets() {}
 public static String random() { byte[] value=new byte[32]; RANDOM.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
 public static String hash(String value) { try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("No se pudo proteger la sesión");} }
 public static boolean matches(String hash,String value) {return hash!=null&&value!=null&&MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),hash(value).getBytes(StandardCharsets.UTF_8));}
 public static String challenge(String verifier) {try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}catch(Exception e){throw new IllegalStateException("No se pudo iniciar Google");}}
}
