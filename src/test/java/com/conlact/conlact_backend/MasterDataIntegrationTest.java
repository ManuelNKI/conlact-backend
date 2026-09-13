package com.conlact.conlact_backend;

import com.conlact.conlact_backend.entity.Association;
import com.conlact.conlact_backend.entity.Category;
import com.conlact.conlact_backend.entity.Product;
import com.conlact.conlact_backend.entity.Recipe;
import com.conlact.conlact_backend.repository.AssociationRepository;
import com.conlact.conlact_backend.repository.CategoryRepository;
import com.conlact.conlact_backend.repository.ProductRepository;
import com.conlact.conlact_backend.repository.RecipeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@Sql(scripts = "/seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class MasterDataIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AssociationRepository associationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Test
    @DisplayName("Debe cargar las asociaciones maestras desde seed.sql")
    void shouldLoadMasterAssociations() {
        List<Association> associations = associationRepository.findAll();
        assertThat(associations).hasSizeGreaterThanOrEqualTo(3);

        Optional<Association> elLindero = associationRepository.findBySlug("asociacion-el-lindero");
        assertThat(elLindero).isPresent();
        assertThat(elLindero.get().getName()).isEqualTo("Asociación El Lindero");
        assertThat(elLindero.get().getArcsaRegistration()).isNotBlank();
    }

    @Test
    @DisplayName("Debe cargar las categorías maestras desde seed.sql")
    void shouldLoadMasterCategories() {
        Optional<Category> quesosFrescos = categoryRepository.findBySlug("quesos-frescos");
        assertThat(quesosFrescos).isPresent();
        assertThat(quesosFrescos.get().getName()).isEqualTo("Quesos Frescos");
    }

    @Test
    @DisplayName("Debe cargar productos con variantes y relaciones")
    void shouldLoadMasterProductsWithVariants() {
        Optional<Product> product = productRepository.findBySlug("queso-fresco-artesanal-el-lindero");
        assertThat(product).isPresent();
        assertThat(product.get().getVariants()).isNotEmpty();
        assertThat(product.get().getAssociation()).isNotNull();
        assertThat(product.get().getAssociation().getSlug()).isEqualTo("asociacion-el-lindero");
    }

    @Test
    @DisplayName("Debe cargar recetas con productos vinculados")
    void shouldLoadMasterRecipes() {
        Optional<Recipe> locro = recipeRepository.findBySlug("locro-de-papa-con-queso-fresco-de-altura");
        assertThat(locro).isPresent();
        assertThat(locro.get().getIngredients()).isNotEmpty();
        assertThat(locro.get().getSteps()).isNotEmpty();
        assertThat(locro.get().getProducts()).isNotEmpty();
    }
}
