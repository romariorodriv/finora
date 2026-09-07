package com.finora.app.shared;
import org.springframework.web.bind.annotation.*; import java.util.Map;
@RestController public class ApiController {@GetMapping("/api/health")public Map<String,Object> health(){return Map.of("status","UP","app","Finora MVP");}@GetMapping("/api/categories")public java.util.List<String> categories(){return java.util.List.of("Comida","Transporte","Servicios","Compras","Salud","Educación","Suscripciones","Entretenimiento","Otros");}}
