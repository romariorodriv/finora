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
 public JwtFilter(JwtService jwt,UserRepository users){this.jwt=jwt;this.users=users;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  String h=req.getHeader("Authorization");
  if(h!=null&&h.startsWith("Bearer "))try{Long id=jwt.userId(h.substring(7));users.findById(id).ifPresent(u->{String role="ADMIN".equals(u.role)?"ADMIN":"USER";SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));});}catch(Exception ignored){SecurityContextHolder.clearContext();}
  chain.doFilter(req,res);
 }
}
