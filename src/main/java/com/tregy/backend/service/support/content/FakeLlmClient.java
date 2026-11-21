package com.tregy.backend.service.support.content;

import com.tregy.backend.model.enums.ContentKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A deterministic stand-in for the model: a small built-in stock per theme, no network, no key.
 * It returns fewer items than asked once the stock is used up, which is how a top-up run behaves
 * when a bucket is full. Used for local development and tests until a real client is configured.
 */
public class FakeLlmClient implements LlmClient {

    private record Entry(String pos, String en, String it) {
    }

    private static final Map<String, List<Entry>> WORDS = Map.of(
            "general", List.of(
                    new Entry("noun", "house", "la casa"), new Entry("noun", "water", "l'acqua"),
                    new Entry("noun", "friend", "l'amico"), new Entry("noun", "morning", "la mattina"),
                    new Entry("verb", "to read", "leggere"), new Entry("verb", "to write", "scrivere"),
                    new Entry("adj", "tired", "stanco"), new Entry("adj", "quiet", "tranquillo")),
            "travel", List.of(
                    new Entry("noun", "suitcase", "la valigia"), new Entry("noun", "passport", "il passaporto"),
                    new Entry("noun", "airport", "l'aeroporto"), new Entry("noun", "map", "la mappa"),
                    new Entry("verb", "to book", "prenotare"), new Entry("verb", "to arrive", "arrivare"),
                    new Entry("adj", "far", "lontano"), new Entry("adj", "crowded", "affollato")),
            "work", List.of(
                    new Entry("noun", "meeting", "la riunione"), new Entry("noun", "colleague", "il collega"),
                    new Entry("noun", "deadline", "la scadenza"), new Entry("noun", "salary", "lo stipendio"),
                    new Entry("verb", "to hire", "assumere"), new Entry("verb", "to send", "inviare"),
                    new Entry("adj", "busy", "occupato"), new Entry("adj", "useful", "utile")),
            "food", List.of(
                    new Entry("noun", "soup", "la zuppa"), new Entry("noun", "salad", "l'insalata"),
                    new Entry("noun", "knife", "il coltello"), new Entry("noun", "plate", "il piatto"),
                    new Entry("verb", "to cook", "cucinare"), new Entry("verb", "to taste", "assaggiare"),
                    new Entry("adj", "sweet", "dolce"), new Entry("adj", "spicy", "piccante")),
            "dating", List.of(
                    new Entry("noun", "date", "l'appuntamento"), new Entry("noun", "smile", "il sorriso"),
                    new Entry("noun", "flower", "il fiore"), new Entry("noun", "dinner", "la cena"),
                    new Entry("verb", "to invite", "invitare"), new Entry("verb", "to laugh", "ridere"),
                    new Entry("adj", "shy", "timido"), new Entry("adj", "kind", "gentile")));

    private static final Map<String, List<Entry>> PHRASES = Map.of(
            "general", List.of(new Entry("phrase", "good luck", "buona fortuna"),
                    new Entry("phrase", "take care", "stammi bene"), new Entry("phrase", "no problem", "nessun problema")),
            "travel", List.of(new Entry("phrase", "where is the exit", "dov'è l'uscita"),
                    new Entry("phrase", "one ticket please", "un biglietto per favore"),
                    new Entry("phrase", "is it far", "è lontano")),
            "work", List.of(new Entry("phrase", "see you at the meeting", "ci vediamo alla riunione"),
                    new Entry("phrase", "I am busy today", "oggi sono occupato"),
                    new Entry("phrase", "send me the file", "inviami il file")),
            "food", List.of(new Entry("phrase", "the bill please", "il conto per favore"),
                    new Entry("phrase", "it is delicious", "è delizioso"),
                    new Entry("phrase", "a table for two", "un tavolo per due")),
            "dating", List.of(new Entry("phrase", "you look nice", "stai bene"),
                    new Entry("phrase", "would you like dinner", "vuoi cenare con me"),
                    new Entry("phrase", "I had a great time", "mi sono divertito")));

    private final String modelId;

    public FakeLlmClient(String modelId) {
        this.modelId = modelId;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public String promptVersion() {
        return "fake-1";
    }

    @Override
    public Result generate(GenerationRequest request) {
        List<Entry> stock = switch (request.kind()) {
            case WORD -> WORDS.getOrDefault(request.theme(), List.of());
            case PHRASE -> PHRASES.getOrDefault(request.theme(), List.of());
            case SENTENCE -> List.of();
        };
        Set<String> existing = request.existingTargets().stream()
                .map(t -> t.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<GeneratedItem> items = new ArrayList<>();
        for (Entry entry : stock) {
            if (items.size() >= request.count()) {
                break;
            }
            String target = text(entry, request.targetLang());
            if (existing.contains(target.toLowerCase(Locale.ROOT))) {
                continue;
            }
            // Phrases are few per theme, so their wrong options come from every theme's phrases.
            List<Entry> pool = request.kind() == ContentKind.PHRASE
                    ? PHRASES.values().stream().flatMap(List::stream).toList() : stock;
            items.add(new GeneratedItem(entry.pos(), text(entry, request.sourceLang()), target,
                    List.of(target), distractors(pool, entry, request.targetLang())));
        }
        // A rough size so the budget logic has something to count; the fake costs nothing.
        return new Result(items, 200L + request.existingTargets().size() * 4L, 60L * items.size());
    }

    private static List<String> distractors(List<Entry> stock, Entry entry, String lang) {
        List<String> same = stock.stream().filter(e -> e != entry && e.pos().equals(entry.pos()))
                .map(e -> text(e, lang)).toList();
        List<String> other = stock.stream().filter(e -> e != entry && !e.pos().equals(entry.pos()))
                .map(e -> text(e, lang)).toList();
        List<String> all = new ArrayList<>(same);
        all.addAll(other);
        return all.stream().limit(3).toList();
    }

    private static String text(Entry entry, String lang) {
        return "it".equals(lang) ? entry.it() : entry.en();
    }
}
