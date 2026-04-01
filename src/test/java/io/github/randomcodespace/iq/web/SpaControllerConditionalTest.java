package io.github.randomcodespace.iq.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that SpaController is conditionally registered based on codeiq.ui.enabled.
 */
class SpaControllerConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SpaController.class)
            .withSystemProperties("spring.profiles.active=serving");

    @Test
    void spaControllerHasConditionalOnPropertyAnnotation() {
        var annotation = SpaController.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).contains("codeiq.ui.enabled");
        assertThat(annotation.havingValue()).isEqualTo("true");
        assertThat(annotation.matchIfMissing()).isTrue();
    }

    @Test
    void spaControllerRegisteredWhenUiEnabledTrue() {
        contextRunner
                .withPropertyValues("codeiq.ui.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(SpaController.class));
    }

    @Test
    void spaControllerRegisteredWhenPropertyAbsent() {
        contextRunner
                .run(context -> assertThat(context).hasSingleBean(SpaController.class));
    }

    @Test
    void spaControllerNotRegisteredWhenUiEnabledFalse() {
        contextRunner
                .withPropertyValues("codeiq.ui.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SpaController.class));
    }

    @Test
    void spaControllerHasProfileAnnotation() {
        var annotation = SpaController.class.getAnnotation(Profile.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("serving");
    }
}
