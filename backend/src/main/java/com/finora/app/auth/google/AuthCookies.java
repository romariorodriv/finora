package com.finora.app.auth.google;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.Duration;
@Component
public class AuthCookies {
 public static final String SESSION="sarela_session", GOOGLE_FLOW="sarela_google_flow", GMAIL_FLOW="sarela_gmail_flow";
 private final boolean secure;
 public AuthCookies(@Value("${app.auth.secure-cookies:true}")boolean secure){this.secure=secure;}
 public String read(HttpServletRequest request,String name){if(request.getCookies()!=null)for(Cookie c:request.getCookies())if(cookieName(name).equals(c.getName()))return c.getValue();return null;}
 public void set(HttpServletResponse response,String name,String value,Duration lifetime){
  response.addHeader("Set-Cookie",ResponseCookie.from(cookieName(name),value).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(lifetime).build().toString());
 }
 private String cookieName(String name){return secure?"__Host-"+name:name;}
 public void clear(HttpServletResponse response,String name){set(response,name,"",Duration.ZERO);}
}
