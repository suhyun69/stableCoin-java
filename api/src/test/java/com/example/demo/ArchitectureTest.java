package com.example.demo;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * architecture.md 에 적힌 구조 규칙을 실행 가능한 테스트로 옮긴 것.
 *
 * <p>클래스 파일의 의존 그래프를 읽어 검사하므로 스프링 컨텍스트를 띄우지 않는다.
 * 규칙을 어기면 빌드가 실패한다.
 */
@AnalyzeClasses(
        packages = "com.example.demo",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    private static final String ROOT = "com.example.demo";

    /** 도메인이 아닌 기반 패키지. 새 기반 패키지를 만들면 여기에 등록한다. */
    private static final String[] NON_DOMAIN_PACKAGES = {
            ROOT + ".controller..",
            ROOT + ".usecase..",
            ROOT + ".common..",
            ROOT + ".config.."
    };

    // ------------------------------------------------------------------
    // 1. 계층 의존 방향
    // ------------------------------------------------------------------

    @ArchTest
    static final ArchRule 계층_의존_방향 = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy(ROOT + ".controller..")
            .layer("UseCase").definedBy(ROOT + ".usecase..")
            .layer("DomainService").definedBy(ROOT + "..application..")
            .layer("Domain").definedBy(ROOT + "..domain..")
            .layer("Persistence").definedBy(ROOT + "..adapter..")

            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("UseCase").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("DomainService").mayOnlyBeAccessedByLayers("UseCase", "Persistence")
            .whereLayer("Persistence").mayNotBeAccessedByAnyLayer();

    // ------------------------------------------------------------------
    // 2. 도메인 격리
    // ------------------------------------------------------------------

    @ArchTest
    static final ArchRule 도메인은_프레임워크를_모른다 =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta..", "org.springframework..", "org.hibernate..")
                    .because("도메인 모델은 순수 자바여야 한다");

    @ArchTest
    static final ArchRule 도메인서비스는_JPA를_모른다 =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "org.hibernate..",
                            "org.springframework.data..")
                    .because("영속성 기술은 어댑터 안에 갇혀 있어야 한다");

    @ArchTest
    static final ArchRule 컨트롤러는_도메인모델을_모른다 =
            noClasses().that().resideInAPackage(ROOT + ".controller..")
                    .should().dependOnClassesThat().resideInAPackage("..domain..")
                    .because("웹 계층은 AppResponse 까지만 본다 (WebRequest → AppRequest → 도메인 → AppResponse → WebResponse)");

    @ArchTest
    static final ArchRule common은_도메인을_모른다 =
            noClasses().that().resideInAPackage(ROOT + ".common..")
                    .should().dependOnClassesThat().resideInAPackage("..domain..")
                    .because("공통 기반은 개별 도메인에 종속되면 안 된다");

    @ArchTest
    static final ArchRule 컨트롤러는_유스케이스_구현체를_모른다 =
            noClasses().that().resideInAPackage(ROOT + ".controller..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + ".usecase.impl..")
                    .because("컨트롤러는 유스케이스 인터페이스에만 의존한다");

    @ArchTest
    static final ArchRule 도메인서비스는_다른_도메인을_모른다 =
            noClasses().that().resideInAPackage("..application..")
                    .should(dependOnAnotherDomain())
                    .because("도메인 간 조율은 유스케이스 구현체의 몫이다");

    // ------------------------------------------------------------------
    // 3. 네이밍 규칙
    // ------------------------------------------------------------------

    @ArchTest
    static final ArchRule 웹_DTO_네이밍 =
            classes().that().resideInAPackage(ROOT + ".controller.dto..")
                    .should().haveSimpleNameEndingWith("WebRequest")
                    .orShould().haveSimpleNameEndingWith("WebResponse");

    @ArchTest
    static final ArchRule 유스케이스_DTO_네이밍 =
            classes().that().resideInAPackage(ROOT + ".usecase.dto..")
                    .should().haveSimpleNameEndingWith("AppRequest")
                    .orShould().haveSimpleNameEndingWith("AppResponse");

    @ArchTest
    static final ArchRule 유스케이스_인터페이스_네이밍 =
            classes().that().resideInAPackage(ROOT + ".usecase")
                    .should().haveSimpleNameEndingWith("UseCase")
                    .andShould().beInterfaces();

    @ArchTest
    static final ArchRule 유스케이스_구현체_네이밍 =
            classes().that().resideInAPackage(ROOT + ".usecase.impl..")
                    .should().haveSimpleNameEndingWith("UseCaseImpl");

    @ArchTest
    static final ArchRule 아웃바운드_포트_네이밍 =
            classes().that().resideInAPackage("..port.out..")
                    .should().haveSimpleNameEndingWith("Port")
                    .andShould().beInterfaces();

    // ------------------------------------------------------------------
    // 4. 트랜잭션 경계
    // ------------------------------------------------------------------

    @ArchTest
    static final ArchRule 도메인서비스는_트랜잭션을_열지_않는다 =
            classes().that().resideInAPackage("..application..")
                    .and().haveSimpleNameEndingWith("Service")
                    .should(beTransactionalWith(Propagation.MANDATORY))
                    .because("트랜잭션 경계는 유스케이스 구현체에 있다");

    @ArchTest
    static final ArchRule 유스케이스_구현체는_트랜잭션_경계다 =
            classes().that().resideInAPackage(ROOT + ".usecase.impl..")
                    .should(openTransactionBoundary())
                    .because("여러 도메인 서비스 호출을 한 트랜잭션으로 묶기 위해서다");

    // ------------------------------------------------------------------
    // 커스텀 조건
    // ------------------------------------------------------------------

    /** com.example.demo.todo.application.TodoService → "todo". 기반 패키지면 null. */
    private static String domainOf(JavaClass clazz) {
        String pkg = clazz.getPackageName();
        if (!pkg.startsWith(ROOT + ".")) {
            return null;
        }
        String rest = pkg.substring(ROOT.length() + 1);
        String first = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
        for (String nonDomain : NON_DOMAIN_PACKAGES) {
            if (nonDomain.equals(ROOT + "." + first + "..")) {
                return null;
            }
        }
        return first;
    }

    /**
     * 자기 도메인이 아닌 다른 도메인 패키지에 의존하는지 본다.
     * 도메인이 늘어도 규칙을 고칠 필요가 없다.
     */
    private static ArchCondition<JavaClass> dependOnAnotherDomain() {
        return new ArchCondition<>("다른 도메인에 직접 의존") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String ownDomain = domainOf(item);
                if (ownDomain == null) {
                    return;
                }
                item.getDirectDependenciesFromSelf().forEach(dependency -> {
                    String targetDomain = domainOf(dependency.getTargetClass());
                    if (targetDomain != null && !targetDomain.equals(ownDomain)) {
                        events.add(SimpleConditionEvent.satisfied(item,
                                item.getName() + " 가 다른 도메인 " + targetDomain
                                        + " 의 " + dependency.getTargetClass().getSimpleName() + " 에 의존"));
                    }
                });
            }
        };
    }

    private static ArchCondition<JavaClass> beTransactionalWith(Propagation propagation) {
        return new ArchCondition<>("@Transactional(propagation = " + propagation + ") 이어야 함") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!item.isAnnotatedWith(Transactional.class)) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getName() + " 에 @Transactional 이 없다"));
                    return;
                }
                Propagation actual = item.getAnnotationOfType(Transactional.class).propagation();
                if (actual != propagation) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getName() + " 의 propagation 이 " + actual + " 이다 (기대: " + propagation + ")"));
                }
            }
        };
    }

    /** 클래스 또는 메서드 어느 쪽에든 @Transactional 이 있으면 경계로 본다. */
    private static ArchCondition<JavaClass> openTransactionBoundary() {
        return new ArchCondition<>("@Transactional 로 경계를 열어야 함") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean onClass = item.isAnnotatedWith(Transactional.class);
                boolean onMethod = item.getMethods().stream()
                        .anyMatch(method -> method.isAnnotatedWith(Transactional.class));
                if (!onClass && !onMethod) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getName() + " 에 @Transactional 이 없다"));
                }
            }
        };
    }
}
