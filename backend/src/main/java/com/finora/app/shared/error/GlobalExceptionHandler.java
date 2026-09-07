package com.finora.app.shared.error;
import jakarta.servlet.http.HttpServletRequest;import org.springframework.http.*;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.*;import java.time.Instant;import java.util.Map;
@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e,HttpServletRequest r){return ResponseEntity.status(e.status).body(body(e.status,e.code,e.getMessage(),r));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e,HttpServletRequest r){String m=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getField()+": "+x.getDefaultMessage()).orElse("Datos inválidos");return ResponseEntity.badRequest().body(body(400,"VALIDATION_ERROR",m,r));}
 @ExceptionHandler(Exception.class) ResponseEntity<?> unknown(Exception e,HttpServletRequest r){return ResponseEntity.status(500).body(body(500,"INTERNAL_ERROR","Ocurrió un error inesperado",r));}
 private Map<String,Object> body(int status,String code,String message,HttpServletRequest r){return Map.of("timestamp",Instant.now(),"status",status,"code",code,"message",message,"path",r.getRequestURI());}
}
