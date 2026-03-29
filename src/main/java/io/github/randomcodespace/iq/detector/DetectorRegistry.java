package io.github.randomcodespace.iq.detector;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of all available detectors.
 * Spring auto-injects all beans implementing {@link Detector}.
 */
@Service
public class DetectorRegistry {

    private final List<Detector> allDetectors;
    private final Map<String, List<Detector>> byLanguage;

    public DetectorRegistry(List<Detector> detectors) {
        this.allDetectors = detectors.stream()
                .sorted(Comparator.comparing(Detector::getName))
                .toList();

        Map<String, List<Detector>> index = new HashMap<>();
        for (Detector d : this.allDetectors) {
            for (String lang : d.getSupportedLanguages()) {
                index.computeIfAbsent(lang, k -> new ArrayList<>()).add(d);
            }
        }
        this.byLanguage = Map.copyOf(index);
    }

    public List<Detector> detectorsForLanguage(String language) {
        return byLanguage.getOrDefault(language, List.of());
    }

    public List<Detector> allDetectors() {
        return allDetectors;
    }

    public Optional<Detector> get(String name) {
        return allDetectors.stream()
                .filter(d -> d.getName().equals(name))
                .findFirst();
    }

    public int count() {
        return allDetectors.size();
    }
}
