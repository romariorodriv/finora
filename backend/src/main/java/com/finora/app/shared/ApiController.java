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
    return List.of("Supermercado", "Restaurantes", "Delivery", "Combustible", "Taxi / apps", "Transporte",
        "Estacionamiento", "Servicios", "Internet / celular", "Casa", "Salud", "Farmacia", "Deporte",
        "Cuidado personal", "Entretenimiento", "Suscripciones", "Compras", "Viajes", "Educacion",
        "Comisiones", "Seguros", "Intereses", "Otros");
  }
}
