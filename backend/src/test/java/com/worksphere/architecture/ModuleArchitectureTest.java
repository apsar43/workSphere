package com.worksphere.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {

  private static final String[] MODULES = {
    "identity", "organization", "authorization", "project", "task", "audit"
  };

  private final JavaClasses classes = new ClassFileImporter().importPackages("com.worksphere");

  @Test
  void domainCodeDoesNotDependOnFrameworks() {
    noClasses()
        .that()
        .resideInAPackage("com.worksphere..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void applicationCodeDoesNotDependOnInfrastructureOrPresentation() {
    noClasses()
        .that()
        .resideInAPackage("com.worksphere..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.worksphere..infrastructure..", "com.worksphere..presentation..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void modulesDoNotDependOnOtherModulesInternals() {
    for (String module : MODULES) {
      String[] otherModules =
          Arrays.stream(MODULES)
              .filter(candidate -> !candidate.equals(module))
              .map(candidate -> "com.worksphere." + candidate + "..")
              .toArray(String[]::new);
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage("com.worksphere." + module + "..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(otherModules);
      rule.check(classes);
    }
  }
}
