package wfederico.pneumacare.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fails if any REST controller request-mapped method lacks {@code @PreAuthorize}
 * — a missing annotation is a privilege-escalation hole.
 */
class AuthorizationCoverageTest {

    @Test
    void everyControllerMethodIsAnnotated() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> gaps = new ArrayList<>();
        for (var beanDef : scanner.findCandidateComponents("wfederico.pneumacare")) {
            Class<?> controller = Class.forName(beanDef.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                if (!method.isAnnotationPresent(PreAuthorize.class)) {
                    gaps.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(gaps).as("controller methods missing @PreAuthorize").isEmpty();
    }
}
