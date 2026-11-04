package com.finora.app.auth.google;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class SessionOriginFilter extends OncePerRequestFilter {
 private final AuthCookies cookies;private final String origin;
 public SessionOriginFilter(AuthCookies cookies,@Value("${app.frontend-url:http://localhost:4200}")String frontend){this.cookies=cookies;var uri=URI.create(frontend);this.origin=uri.getScheme()+"://"+uri.getRawAuthority();}
 public boolean allowed(HttpServletRequest request){return origin.equals(request.getHeader("Origin"));}
 protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())&&cookies.read(request,AuthCookies.SESSION)!=null&&!allowed(request)){
   response.setStatus(403);response.setContentType("application/json");response.getWriter().write("{\"code\":\"ORIGIN_NOT_ALLOWED\",\"message\":\"Origen no permitido\"}");return;
  }
  chain.doFilter(request,response);
 }
}
