package com.finora.app.config;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.finora.app.auth.UserRepository;
import java.io.IOException;import java.util.List;
@Component
public class JwtFilter extends OncePerRequestFilter {
 private final JwtService jwt;private final UserRepository users;
 private final com.finora.app.auth.google.AccountSessions sessions;private final com.finora.app.auth.google.AuthCookies cookies;
 public JwtFilter(JwtService jwt,UserRepository users,com.finora.app.auth.google.AccountSessions sessions,com.finora.app.auth.google.AuthCookies cookies){this.jwt=jwt;this.users=users;this.sessions=sessions;this.cookies=cookies;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String h=req.getHeader("Authorization");
  if(h!=null&&h.startsWith("Bearer "))try{Long id=jwt.userId(h.substring(7));users.findById(id).ifPresent(u->{String role="ADMIN".equals(u.role)?"ADMIN":"USER";SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));});}catch(Exception ignored){SecurityContextHolder.clearContext();}
  if(SecurityContextHolder.getContext().getAuthentication()==null) {
   sessions.userId(cookies.read(req,com.finora.app.auth.google.AuthCookies.SESSION)).flatMap(users::findById).ifPresent(u->{
    String role="ADMIN".equals(u.role)?"ADMIN":"USER";
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.id,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
   });
  }
  chain.doFilter(req,res);
 }
}
