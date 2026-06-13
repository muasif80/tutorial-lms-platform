package com.scholr.lms.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the modular-monolith boundaries in CI (production classes only). If these
 * fail, the build fails — which is how the bounded contexts stay bounded.
 */
@AnalyzeClasses(packages = "com.scholr.lms", importOptions = ImportOption.DoNotIncludeTests.class)
class ModularityTest {

    /** The context modules must not form dependency cycles. */
    @ArchTest
    static final ArchRule modules_are_free_of_cycles =
        slices().matching("com.scholr.lms.(*)..").should().beFreeOfCycles();

    /** A module's {@code internal} package is private to that module. */
    @ArchTest
    static final ArchRule internal_packages_stay_module_private =
        noClasses()
            .that().resideOutsideOfPackage("com.scholr.lms.enrollment..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.scholr.lms.enrollment.internal..");
}
