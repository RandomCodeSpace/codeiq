package io.github.randomcodespace.iq.detector;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Registry of all available detectors.
 * Spring auto-injects all beans implementing {@link Detector}.
 */
@Service
public class DetectorRegistry {

    private final List<Detector> allDetectors;
    private final Map<String, List<Detector>> byLanguage;
    private final Map<String, List<Detector>> byCategoryIndex;
    private final Map<String, DetectorInfo> infoByName;

    public DetectorRegistry(List<Detector> detectors) {
        this.allDetectors = detectors.stream()
                .sorted(Comparator.comparing(Detector::getName))
                .toList();

        Map<String, List<Detector>> langIndex = new HashMap<>();
        Map<String, List<Detector>> catIndex = new TreeMap<>();
        Map<String, DetectorInfo> infoMap = new HashMap<>();
        for (Detector d : this.allDetectors) {
            for (String lang : d.getSupportedLanguages()) {
                langIndex.computeIfAbsent(lang, k -> new ArrayList<>()).add(d);
            }
            String cat = categoryOf(d);
            catIndex.computeIfAbsent(cat, k -> new ArrayList<>()).add(d);
            DetectorInfo info = d.getClass().getAnnotation(DetectorInfo.class);
            if (info != null) {
                infoMap.put(info.name(), info);
            }
        }
        this.byLanguage = Map.copyOf(langIndex);
        this.byCategoryIndex = Map.copyOf(catIndex);
        this.infoByName = Map.copyOf(infoMap);
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

    /**
     * Return all detectors belonging to the given category.
     */
    public List<Detector> detectorsForCategory(String category) {
        return byCategoryIndex.getOrDefault(category, List.of());
    }

    /**
     * Return all distinct category names, sorted alphabetically.
     */
    public List<String> allCategories() {
        List<String> cats = new ArrayList<>(byCategoryIndex.keySet());
        cats.sort(String::compareTo);
        return cats;
    }

    /**
     * Look up the {@link DetectorInfo} annotation for a detector by name.
     */
    public Optional<DetectorInfo> getInfo(String name) {
        return Optional.ofNullable(infoByName.get(name));
    }

    /**
     * Return a new registry containing only detectors whose category
     * matches one of the given categories.
     */
    public DetectorRegistry filterByCategories(Collection<String> categories) {
        Set<String> cats = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        cats.addAll(categories);
        List<Detector> filtered = allDetectors.stream()
                .filter(d -> cats.contains(categoryOf(d)))
                .toList();
        return new DetectorRegistry(filtered);
    }

    /**
     * Return a new registry containing only the named detectors.
     */
    public DetectorRegistry filterByNames(Collection<String> names) {
        Set<String> nameSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        nameSet.addAll(names);
        List<Detector> filtered = allDetectors.stream()
                .filter(d -> nameSet.contains(d.getName()))
                .toList();
        return new DetectorRegistry(filtered);
    }

    /**
     * Return all distinct language names across all detectors, sorted.
     */
    public Set<String> allLanguages() {
        Set<String> langs = new TreeSet<>();
        for (Detector d : allDetectors) {
            langs.addAll(d.getSupportedLanguages());
        }
        return langs;
    }

    /**
     * Group detectors by category.
     * Uses {@link DetectorInfo#category()} if present, otherwise derives
     * the category from the detector's package name.
     */
    public Map<String, List<Detector>> byCategory() {
        Map<String, List<Detector>> result = new TreeMap<>();
        for (Detector d : allDetectors) {
            String cat = categoryOf(d);
            result.computeIfAbsent(cat, k -> new ArrayList<>()).add(d);
        }
        return result;
    }

    /**
     * Derive the category for a detector.
     * Checks for {@link DetectorInfo} annotation first, then falls back
     * to the sub-package name under {@code detector}.
     */
    public static String categoryOf(Detector d) {
        DetectorInfo info = d.getClass().getAnnotation(DetectorInfo.class);
        if (info != null) {
            return info.category();
        }
        // Fallback: derive from package name
        String pkg = d.getClass().getPackageName();
        int idx = pkg.lastIndexOf('.');
        if (idx >= 0) {
            String last = pkg.substring(idx + 1);
            // If it's the root "detector" package, use "generic"
            if ("detector".equals(last)) {
                return "generic";
            }
            return last;
        }
        return "generic";
    }

    /**
     * Return detectors matching a query string that can be a category name
     * or a "category/name" path.
     */
    public List<Detector> findByQuery(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (query.contains("/")) {
            // category/name format
            String[] parts = query.split("/", 2);
            String cat = parts[0];
            String name = parts[1];
            return allDetectors.stream()
                    .filter(d -> categoryOf(d).equalsIgnoreCase(cat)
                            && d.getName().equalsIgnoreCase(name))
                    .toList();
        }
        // Try exact detector name first
        Optional<Detector> exact = get(query);
        if (exact.isPresent()) {
            return List.of(exact.get());
        }
        // Then try category
        return allDetectors.stream()
                .filter(d -> categoryOf(d).equalsIgnoreCase(query))
                .toList();
    }
}
