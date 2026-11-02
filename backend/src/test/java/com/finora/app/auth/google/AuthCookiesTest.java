package com.finora.app.auth.google;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
class AuthCookiesTest {
 @Test void productionCookiesAreHostOnlyHttpOnlySecureAndSameSiteLax(){
  var cookies=new AuthCookies(true);var response=new MockHttpServletResponse();
  cookies.set(response,AuthCookies.SESSION,"test-opaque-session",Duration.ofHours(8));
  String header=response.getHeader("Set-Cookie");
  assertTrue(header.startsWith("__Host-sarela_session="));assertTrue(header.contains("HttpOnly"));assertTrue(header.contains("Secure"));assertTrue(header.contains("SameSite=Lax"));assertTrue(header.contains("Path=/"));assertFalse(header.contains("Domain="));
  var request=new MockHttpServletRequest();request.setCookies(new Cookie("sarela_session","injected-domain-cookie"),new Cookie("__Host-sarela_session","test-opaque-session"));
  assertEquals("test-opaque-session",cookies.read(request,AuthCookies.SESSION));
 }
 @Test void localHttpCookiesRemainHttpOnlyWithoutProductionPrefix(){
  var response=new MockHttpServletResponse();new AuthCookies(false).set(response,AuthCookies.GOOGLE_FLOW,"test-flow",Duration.ofMinutes(10));
  String header=response.getHeader("Set-Cookie");assertTrue(header.startsWith("sarela_google_flow="));assertTrue(header.contains("HttpOnly"));assertFalse(header.contains("Secure"));
 }
}
