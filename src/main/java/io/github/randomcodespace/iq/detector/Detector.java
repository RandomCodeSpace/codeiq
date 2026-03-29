package io.github.randomcodespace.iq.detector;

import java.util.Set;

public interface Detector {
    String getName();
    Set<String> getSupportedLanguages();
    DetectorResult detect(DetectorContext ctx);
}
