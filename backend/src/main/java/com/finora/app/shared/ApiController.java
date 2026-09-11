package com.finora.app.shared;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class ApiController {
  @GetMapping({"/api/health", "/api/v1/health"})
  public Map<String, Object> health() {
    return Map.of("status", "UP", "app", "Sarela");
  }

  @GetMapping({"/api/categories", "/api/v1/categories"})
  public List<String> categories() {
    return List.of("Comida", "Transporte", "Servicios", "Compras", "Salud", "Educación", "Suscripciones",
        "Entretenimiento", "Otros");
  }
}
