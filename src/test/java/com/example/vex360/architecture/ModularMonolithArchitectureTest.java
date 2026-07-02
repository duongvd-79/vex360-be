package com.example.vex360.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ModularMonolithArchitectureTest {

    private static final String BASE_PACKAGE = "com.example.vex360";

    @Test
    void featureRepositoriesShouldNotBeAccessedByOtherFeatures() {
        String[] features = { "auth", "booth", "company", "exhibition", "packagetemplate", "partnership", "product",
                "user", "mail" };
        for (String feature : features) {
            ArchRule rule = classes().that().resideInAPackage("..features." + feature + ".repositories..")
                    .should().onlyBeAccessed().byClassesThat().resideInAPackage("..features." + feature + "..")
                    .allowEmptyShould(true);

            rule.check(new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(BASE_PACKAGE));
        }
    }

    @Test
    void thereShouldBeNoCircularDependenciesBetweenFeatures() {
        ArchRule rule = slices().matching("..features.(*)..")
                .should().beFreeOfCycles();

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE));
    }
}
