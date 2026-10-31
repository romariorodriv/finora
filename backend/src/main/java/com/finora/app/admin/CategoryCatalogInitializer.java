package com.finora.app.admin;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.finora.app.category.CategoryClassifier;
@Component @Order(-10)
public class CategoryCatalogInitializer implements CommandLineRunner {
 private final ManagedCategoryRepository categories;
 private final CategoryClassifier classifier;
 private final boolean seedDefaults;
 public CategoryCatalogInitializer(ManagedCategoryRepository c,CategoryClassifier f,
   @org.springframework.beans.factory.annotation.Value("${app.categories.seed-defaults:false}") boolean seedDefaults){categories=c;classifier=f;this.seedDefaults=seedDefaults;}
 @Override @Transactional public void run(String... args){
  if(!seedDefaults)return;
  for(String label:new String[]{"Alimentacion","Transporte","Gasolina","Salud","Servicios","Suscripciones","Otros"}) {
   String key=ClassificationKeys.name(label);if(categories.findByNameKey(key).isPresent())continue;
   var c=new ManagedCategory();c.name=label;c.nameKey=key;c.macroGroup=classifier.macroFromSubcategory(label).name();categories.save(c);
  }
 }
}
