package com.finora.app.auth.google;
import com.finora.app.shared.error.ApiException;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;
@RestController @RequestMapping({"/api/auth/google","/api/v1/auth/google"})
public class GoogleAuthController {
 private final GoogleSignInService signIn;private final AccountSessions sessions;private final AuthCookies cookies;private final SessionOriginFilter origins;private final String frontend;
 public GoogleAuthController(GoogleSignInService signIn,AccountSessions sessions,AuthCookies cookies,SessionOriginFilter origins,@Value("${app.frontend-url:http://localhost:4200}")String frontend){this.signIn=signIn;this.sessions=sessions;this.cookies=cookies;this.origins=origins;this.frontend=frontend.replaceAll("/+$","");}
 @PostMapping("/start") public Map<String,String> start(HttpServletRequest request,HttpServletResponse response){
  requireOrigin(request);var result=signIn.start();cookies.set(response,AuthCookies.GOOGLE_FLOW,result.browserCookie(),Duration.ofMinutes(10));response.setHeader("Cache-Control","no-store");return Map.of("authorizationUrl",result.authorizationUrl());
 }
 @GetMapping("/callback") public void callback(@RequestParam(required=false)String code,@RequestParam(required=false)String state,@RequestParam(required=false)String error,HttpServletRequest request,HttpServletResponse response)throws java.io.IOException{
  response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");
  try {var result=signIn.callback(code,state,cookies.read(request,AuthCookies.GOOGLE_FLOW),error);
   if(result.linkRequired()){response.sendRedirect(frontend+"/?auth=link_required#acceso");return;}
   cookies.set(response,AuthCookies.SESSION,result.sessionToken(),Duration.ofHours(8));cookies.clear(response,AuthCookies.GOOGLE_FLOW);response.sendRedirect(frontend+"/?auth=connected");
  }catch(org.springframework.dao.DataIntegrityViolationException e){cookies.clear(response,AuthCookies.GOOGLE_FLOW);response.sendRedirect(frontend+"/?auth=failed#acceso");}
  catch(ApiException e){cookies.clear(response,AuthCookies.GOOGLE_FLOW);response.sendRedirect(frontend+"/?auth="+("GOOGLE_CANCELLED".equals(e.code)?"cancelled":"failed")+"#acceso");}
 }
 public record LinkRequest(String password){@Override public String toString(){return "LinkRequest[REDACTED]";}}
 @PostMapping("/link") public Map<String,String> link(@RequestBody LinkRequest body,HttpServletRequest request,HttpServletResponse response){
  requireOrigin(request);var result=signIn.link(cookies.read(request,AuthCookies.GOOGLE_FLOW),body.password());cookies.set(response,AuthCookies.SESSION,result.sessionToken(),Duration.ofHours(8));cookies.clear(response,AuthCookies.GOOGLE_FLOW);response.setHeader("Cache-Control","no-store");return Map.of("status","LINKED");
 }
 @PostMapping("/logout") public void logout(HttpServletRequest request,HttpServletResponse response){requireOrigin(request);sessions.revoke(cookies.read(request,AuthCookies.SESSION));cookies.clear(response,AuthCookies.SESSION);cookies.clear(response,AuthCookies.GMAIL_FLOW);cookies.clear(response,AuthCookies.GOOGLE_FLOW);}
 private void requireOrigin(HttpServletRequest request){if(!origins.allowed(request))throw new ApiException(403,"ORIGIN_NOT_ALLOWED","Origen no permitido");}
}
