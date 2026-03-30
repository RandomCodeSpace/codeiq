package io.github.randomcodespace.iq.detector;

import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import java.lang.annotation.*;

/**
 * Metadata annotation for detectors. Declares the category, description,
 * parser type, supported languages, and the node/edge kinds produced.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DetectorInfo {
    String name();
    String category();
    String description();
    ParserType parser() default ParserType.REGEX;
    String[] languages();
    NodeKind[] nodeKinds();
    EdgeKind[] edgeKinds() default {};
    String[] properties() default {};
}
