package com.finora.app.admin;
import com.finora.app.category.CategoryClassifier;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class CategoryCatalogInitializerTest {
  @Test void normalStartupDoesNotRecreateDeletedCategories() {
    var repository = mock(ManagedCategoryRepository.class);
    new CategoryCatalogInitializer(repository, new CategoryClassifier(), false).run();
    verifyNoInteractions(repository);
  }
  @Test void explicitDemoSeedingCanStillCreateDefaults() {
    var repository = mock(ManagedCategoryRepository.class);
    new CategoryCatalogInitializer(repository, new CategoryClassifier(), true).run();
    verify(repository, times(7)).save(any(ManagedCategory.class));
  }
}
