package com.example.vex360.architecture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import com.example.vex360.shared.config.DataSeeder;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class ModularMonolithArchitectureTest {

    private static final String BASE_PACKAGE = "com.example.vex360";
    private static final String FEATURES_PACKAGE = BASE_PACKAGE + ".features.";
    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void featureRepositoriesShouldOnlyBeDependedOnByTheirOwningFeature() {
        List<String> violations = new ArrayList<>();
        for (String feature : featureNames(IMPORTED_CLASSES, true)) {
            String featurePackage = FEATURES_PACKAGE + feature;
            ArchRule rule = classes().that().resideInAPackage(featurePackage + "..repositories..")
                    .should().onlyHaveDependentClassesThat(
                            resideInAPackage(featurePackage + "..")
                                    .or(equivalentTo(DataSeeder.class)));

            collectViolation(violations, rule,
                    "Repository of feature '" + feature + "' is used outside its owning feature.",
                    "Do not inject or call this repository from another feature. "
                            + "Add or reuse a service in feature '" + feature + "' and call that service instead.");
        }
        assertNoViolations(violations);
    }

    @Test
    //@Disabled("Legacy feature packages still contain dependency cycles; enable after modularization")
    void featuresShouldBeFreeOfCycles() {
        assertFalse(featureNames(IMPORTED_CLASSES, false).isEmpty(),
                "No feature packages were imported from " + FEATURES_PACKAGE);

        ArchRule rule = slices().matching("..features.(*)..")
                .should().beFreeOfCycles();
        List<String> violations = new ArrayList<>();
        collectViolation(violations, rule,
                "A circular dependency exists between feature packages.",
                "Keep dependencies flowing in one direction. Move the shared workflow to one owning feature, "
                        + "or replace the reverse direct call with an application event.");
        assertNoViolations(violations);
    }

    @Test
    void boothListenersShouldNotDependOnBoothRepositories() {
        ArchRule rule = noClasses().that().resideInAPackage("..features.booth.listeners..")
                .should().dependOnClassesThat().resideInAPackage("..features.booth.repositories..");
        rule.check(IMPORTED_CLASSES);
    }

    private static void collectViolation(List<String> violations, ArchRule rule, String what, String howToFix) {
        EvaluationResult result = rule.evaluate(IMPORTED_CLASSES);
        if (!result.hasViolation()) {
            return;
        }

        violations.add("""
                ============================================================
                MODULAR MONOLITH ARCHITECTURE VIOLATION

                WHAT:
                %s

                WHERE:
                %s

                HOW TO FIX:
                %s
                ============================================================
                """.formatted(what, result.getFailureReport(), howToFix));
    }

    private static void assertNoViolations(List<String> violations) {
        if (!violations.isEmpty()) {
            fail(System.lineSeparator() + String.join(System.lineSeparator(), violations));
        }
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
