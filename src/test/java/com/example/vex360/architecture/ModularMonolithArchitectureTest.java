package com.example.vex360.architecture;

import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModularMonolithArchitectureTest {

    private static final String BASE_PACKAGE = "com.example.vex360";
    private static final String FEATURES_PACKAGE = BASE_PACKAGE + ".features.";
    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void featureRepositoriesShouldOnlyBeDependedOnByTheirOwningFeature() {
        for (String feature : featureNames(IMPORTED_CLASSES, true)) {
            String featurePackage = FEATURES_PACKAGE + feature;
            ArchRule rule = classes().that().resideInAPackage(featurePackage + "..repositories..")
                    .should().onlyHaveDependentClassesThat().resideInAPackage(featurePackage + "..");

            rule.check(IMPORTED_CLASSES);
        }
    }

    @Test
    void featureInternalsShouldOnlyBeDependedOnByTheirOwningFeature() {
        Set<String> features = featureNames(IMPORTED_CLASSES, false);
        assertFalse(features.isEmpty(), "No feature packages were imported from " + FEATURES_PACKAGE);

        for (String feature : features) {
            String featurePackage = FEATURES_PACKAGE + feature;
            ArchRule rule = classes().that().resideInAPackage(featurePackage + "..")
                    .and().resideOutsideOfPackage(featurePackage + ".api..")
                    .and().resideOutsideOfPackage(featurePackage + ".events..")
                    .should().onlyHaveDependentClassesThat().resideInAPackage(featurePackage + "..");

            rule.check(IMPORTED_CLASSES);
        }
    }

    @Test
    void featuresShouldBeFreeOfCycles() {
        slices().matching("..features.(*)..")
                .should().beFreeOfCycles()
                .check(IMPORTED_CLASSES);
    }

    private static Set<String> featureNames(JavaClasses importedClasses, boolean repositoriesOnly) {
        Set<String> features = new TreeSet<>();
        for (JavaClass javaClass : importedClasses) {
            String packageName = javaClass.getPackageName();
            if (!packageName.startsWith(FEATURES_PACKAGE)) {
                continue;
            }

            String featurePackage = packageName.substring(FEATURES_PACKAGE.length());
            int packageSeparator = featurePackage.indexOf('.');
            String feature = packageSeparator < 0 ? featurePackage : featurePackage.substring(0, packageSeparator);
            String packageWithinFeature = packageSeparator < 0 ? "" : featurePackage.substring(packageSeparator + 1);
            if (!repositoriesOnly || packageWithinFeature.equals("repositories")
                    || packageWithinFeature.startsWith("repositories.")) {
                features.add(feature);
            }
        }
        return features;
    }
}
