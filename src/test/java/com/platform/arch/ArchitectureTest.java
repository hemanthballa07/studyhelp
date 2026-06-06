package com.platform.arch;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Package-by-context boundary harness. Each top-level package under {@code com.platform} is a
 * bounded context; contexts must not depend on each other's internals. Cross-context effects go
 * through domain events (outbox + dispatcher) and {@code shared}, which is the only allowed shared
 * dependency. These rules tighten automatically as later slices add classes to each context.
 */
@AnalyzeClasses(packages = "com.platform", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule contexts_are_free_of_cycles =
            slices().matching("com.platform.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule contexts_do_not_depend_on_each_other =
            slices().matching("com.platform.(*)..").should().notDependOnEachOther()
                    .ignoreDependency(alwaysTrue(), resideInAPackage("..shared.."));
}
