package com.tregy.backend.service.support.content;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Serves content that people wrote and checked instead of asking a model. It reads
 * {@code content-seed/<LEVEL>/<theme>-<kind>.json} from the classpath: one file per bucket, each a list of
 * entries with both languages side by side, so one entry serves both directions (en to it and it to en).
 * Every item still goes through {@link ContentValidator} like model output, so a typo in a seed file is
 * rejected for the same reasons a bad model answer would be. No network, no key.
 *
 * <p>Wrong options are taken from the other entries of the same bucket (same part of speech where the
 * bucket has enough of them), chosen by a fixed rule so a run is repeatable.
 */
public class FileLlmClient implements LlmClient {

    /** One line of a seed file. {@code enAlt} and {@code itAlt} are extra accepted answers in that language. */
    public record Entry(String pos, String en, String it, List<String> enAlt, List<String> itAlt) {
        String text(String lang) {
            return "it".equals(lang) ? it : en;
        }

        List<String> alternatives(String lang) {
            List<String> alt = "it".equals(lang) ? itAlt : enAlt;
            return alt == null ? List.of() : alt;
        }
    }

    private final String modelId;
    private final String resourceRoot;
    private final JsonMapper json;

    public FileLlmClient(String modelId, String resourceRoot, JsonMapper json) {
        this.modelId = modelId;
        this.resourceRoot = resourceRoot;
        this.json = json;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public String promptVersion() {
        return "file-1";
    }

    @Override
    public Result generate(GenerationRequest request) {
        List<Entry> bucket = load(request);
        Set<String> existing = new HashSet<>();
        request.existingTargets().forEach(t -> existing.add(TextRules.key(t)));
        List<GeneratedItem> items = new ArrayList<>();
        for (int i = 0; i < bucket.size() && items.size() < request.count(); i++) {
            Entry entry = bucket.get(i);
            String target = entry.text(request.targetLang());
            if (existing.contains(TextRules.key(target))) {
                continue;
            }
            Set<String> accepted = new LinkedHashSet<>();
            accepted.add(target);
            accepted.addAll(entry.alternatives(request.targetLang()));
            items.add(new GeneratedItem(entry.pos(), entry.text(request.sourceLang()), target,
                    List.copyOf(accepted), distractors(bucket, i, request.targetLang(), accepted)));
        }
        // Nothing was billed: reading a file costs no tokens.
        return new Result(items, 0L, 0L);
    }

    private List<Entry> load(GenerationRequest request) {
        String path = resourceRoot + "/" + request.level().name() + "/" + request.theme() + "-"
                + request.kind().name().toLowerCase(Locale.ROOT) + ".json";
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            return json.readValue(in, new TypeReference<List<Entry>>() {
            });
        } catch (IOException e) {
            // Name the file but never its content.
            throw new IllegalStateException("Cannot read seed file " + path, e);
        }
    }

    /**
     * Three wrong options from the same bucket: same part of speech first, then the rest, walking from the
     * entry's own position so neighbouring entries do not all get the same three.
     */
    private static List<String> distractors(List<Entry> bucket, int self, String lang, Set<String> accepted) {
        Set<String> blocked = new HashSet<>();
        accepted.forEach(a -> blocked.add(TextRules.key(a)));
        Entry me = bucket.get(self);
        List<String> chosen = new ArrayList<>();
        Set<String> chosenKeys = new HashSet<>();
        // A stride of 7 must not share a factor with the bucket size, or the walk would skip entries.
        int stride = bucket.size() % 7 == 0 ? 1 : 7;
        for (int pass = 0; pass < 2 && chosen.size() < ContentValidator.DISTRACTOR_COUNT; pass++) {
            for (int step = 1; step < bucket.size() && chosen.size() < ContentValidator.DISTRACTOR_COUNT; step++) {
                Entry other = bucket.get((self + step * stride) % bucket.size());
                boolean samePos = other.pos().equals(me.pos());
                if (other == me || (pass == 0 && !samePos)) {
                    continue;
                }
                String text = other.text(lang);
                String key = TextRules.key(text);
                if (!blocked.contains(key) && chosenKeys.add(key)
                        && other.alternatives(lang).stream().noneMatch(a -> blocked.contains(TextRules.key(a)))) {
                    chosen.add(text);
                }
            }
        }
        return chosen;
    }
}
