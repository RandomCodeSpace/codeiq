package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.AbstractAntlrDetector;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import java.util.Set;

public abstract class AbstractTypeScriptDetector extends AbstractAntlrDetector {
    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("typescript", "javascript");
    }

    @Override
    public DetectorResult detect(DetectorContext ctx) {
        return detectWithRegex(ctx);
    }
}
