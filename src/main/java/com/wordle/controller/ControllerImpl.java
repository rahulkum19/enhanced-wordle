package com.wordle.controller;

import com.wordle.model.Model;
import javafx.application.Platform;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ControllerImpl implements Controller {
    private final Model model;
    private String input;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private String loadEasy = "";
    private String loadMedium = "";
    private String loadHard = "";

    private List<WordFrequency> cachedWords = new ArrayList<>();
    private final Set<String> validatedWords = ConcurrentHashMap.newKeySet();

    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{[^}]*\\}");
    private static final Pattern WORD_PATTERN = Pattern.compile("\"word\":\"([^\"]+)\"");
    private static final Pattern FREQ_PATTERN = Pattern.compile("f:([0-9.]+)");
    private static final Pattern DEFS_ENTRY_PATTERN = Pattern.compile("\"([a-zA-Z]+)(?:\\\\t|\t)((?:\\\\.|[^\"])*)\"");
    private static final Pattern LEADING_TAGS_PATTERN = Pattern.compile("^(\\s*\\([a-zA-Z0-9,._/\\-\\s]+\\)\\s*)+");
    private static final Pattern CROSS_REF_BRACKET_PATTERN = Pattern.compile("^(?:Alternative form of|Synonym of|Ellipsis of|Variant of|Misspelling of)\\s+[^.\\[]+\\.\\s*\\[(.*?)\\]\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CROSS_REF_QUOTE_PATTERN = Pattern.compile("^(?:Alternative form of|Synonym of|Ellipsis of|Variant of|Misspelling of)\\s+[^“\"(]+[“\"(](.*?)[)”\"]\\.?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIRST_SENTENCE_PATTERN = Pattern.compile("^(.*?\\.)\\s+[A-Z]");

    private static class WordFrequency {
        final String word;
        final double frequency;

        WordFrequency(String word, double frequency) {
            this.word = word;
            this.frequency = frequency;
        }
    }

    public ControllerImpl(Model model) {
        this.model = model;
        this.input = "";
    }

    @Override
    public void setEasyMode() {
        model.setModeStatus(Model.MODE.EASY);
    }

    @Override
    public void setMediumMode() {
        model.setModeStatus(Model.MODE.MEDIUM);
    }

    @Override
    public void setHardMode() {
        model.setModeStatus(Model.MODE.HARD);
    }

    @Override
    public void startGame() {
        input = "";
        model.startGame();
    }

    @Override
    public void processKeyPress(String key) {
        if (key == null || model.getTargetWord().isEmpty()) {
            return;
        }
        if (model.getStatus() != Model.STATUS.IN_PROGRESS) {
            return;
        }
        switch (key) {
            case "ENTER" -> inputIsEnter();
            case "BACK_SPACE" -> inputIsBackspace();
            default -> {
                if (key.length() == 1 && Character.isLetter(key.charAt(0))) {
                    inputIsLetter(key.toUpperCase());
                }
            }
        }
    }

    private void inputIsLetter(String key) {
        if (input.length() < 5) {
            input += key;
            model.update();
        }
    }

    private void inputIsBackspace() {
        if (!input.isEmpty()) {
            input = input.substring(0, input.length() - 1);
            model.update();
        }
    }

    private void inputIsEnter() {
        if (input.length() == 5) {
            validateGuess(input);
        }
    }

    private void validateGuess(String guess) {
        // Fast-path: if guess is the target word or already verified valid, accept immediately
        if (guess.equalsIgnoreCase(model.getTargetWord()) || validatedWords.contains(guess.toLowerCase())) {
            model.makeGuess(guess);
            input = "";
            return;
        }

        HttpUrl url = HttpUrl.parse("https://api.datamuse.com/words")
                .newBuilder()
                .addQueryParameter("sp", guess.toLowerCase())
                .addQueryParameter("max", "1")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Platform.runLater(model::triggerInvalidWord);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String body = response.body().string();
                        Matcher m = WORD_PATTERN.matcher(body);
                        boolean valid = m.find() && m.group(1).equalsIgnoreCase(guess);
                        Platform.runLater(() -> {
                            if (valid) {
                                validatedWords.add(guess.toLowerCase());
                                model.makeGuess(guess);
                                input = "";
                            } else {
                                model.triggerInvalidWord();
                            }
                        });
                    } else {
                        Platform.runLater(model::triggerInvalidWord);
                    }
                }
            }
        });
    }

    @Override
    public void initializeWords() {
        fetchWordsAndInitialize();
    }

    private void fetchWordsAndInitialize() {
        HttpUrl httpUrl = HttpUrl.parse("https://api.datamuse.com/words")
                .newBuilder()
                .addQueryParameter("sp", "?????")
                .addQueryParameter("md", "f")
                .addQueryParameter("max", "500")
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    List<WordFrequency> words = parseWordFrequencies(body);
                    if (!words.isEmpty()) {
                        cachedWords = words;
                        refreshLoadedWords();
                        Platform.runLater(() -> {
                            if (model.getStatus() == Model.STATUS.START_GAME || model.getTargetWord().isEmpty()) {
                                getTargetWord();
                            }
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }
        });
    }

    private List<WordFrequency> parseWordFrequencies(String json) {
        List<WordFrequency> wordFreqPairs = new ArrayList<>();
        Matcher objMatcher = OBJECT_PATTERN.matcher(json);
        while (objMatcher.find()) {
            String obj = objMatcher.group();
            Matcher wordMatcher = WORD_PATTERN.matcher(obj);
            Matcher freqMatcher = FREQ_PATTERN.matcher(obj);
            if (wordMatcher.find() && freqMatcher.find()) {
                String word = wordMatcher.group(1);
                if (word.length() == 5) {
                    try {
                        double freq = Double.parseDouble(freqMatcher.group(1));
                        wordFreqPairs.add(new WordFrequency(word, freq));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        // Frequency parsed once, sorting compares primitive doubles directly
        wordFreqPairs.sort((a, b) -> Double.compare(b.frequency, a.frequency));
        return wordFreqPairs;
    }

    private void refreshLoadedWords() {
        if (loadEasy.isEmpty()) {
            loadEasy = selectWordFromCache(Model.MODE.EASY);
        }
        if (loadMedium.isEmpty()) {
            loadMedium = selectWordFromCache(Model.MODE.MEDIUM);
        }
        if (loadHard.isEmpty()) {
            loadHard = selectWordFromCache(Model.MODE.HARD);
        }
    }

    private String selectWordFromCache(Model.MODE m) {
        if (cachedWords.isEmpty()) {
            return null;
        }

        int size = cachedWords.size();
        int startIdx;
        int endIdx;

        if (m == Model.MODE.EASY) {
            startIdx = 0;
            endIdx = Math.max(1, (int) (size * 0.2));
        } else if (m == Model.MODE.MEDIUM) {
            startIdx = (int) (size * 0.15);
            endIdx = Math.max(startIdx + 1, (int) (size * 0.35));
        } else {
            startIdx = (int) (size * 0.8);
            endIdx = size;
        }

        endIdx = Math.min(endIdx, size);
        if (startIdx >= endIdx) {
            startIdx = 0;
            endIdx = size;
        }

        int randomIndex = startIdx + (int) (Math.random() * (endIdx - startIdx));
        return cachedWords.get(randomIndex).word;
    }

    @Override
    public void getTargetWord() {
        model.resetGame();
        input = "";
        String targetWord = switch (model.getModeStatus()) {
            case EASY -> loadEasy;
            case MEDIUM -> loadMedium;
            case HARD -> loadHard;
        };

        // If buffered word was empty, attempt to pick from cached words
        if (targetWord == null || targetWord.isEmpty()) {
            targetWord = selectWordFromCache(model.getModeStatus());
        }

        if (targetWord != null && !targetWord.isEmpty()) {
            model.setTargetWord(targetWord);
            fetchWordDefinition(targetWord);
            switch (model.getModeStatus()) {
                case EASY -> loadEasy = selectWordFromCache(Model.MODE.EASY);
                case MEDIUM -> loadMedium = selectWordFromCache(Model.MODE.MEDIUM);
                case HARD -> loadHard = selectWordFromCache(Model.MODE.HARD);
            }
        } else if (cachedWords.isEmpty()) {
            fetchWordsAndInitialize();
        }
    }

    private void fetchWordDefinition(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        HttpUrl url = HttpUrl.parse("https://api.datamuse.com/words")
                .newBuilder()
                .addQueryParameter("sp", word.toLowerCase())
                .addQueryParameter("md", "d")
                .addQueryParameter("max", "1")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Ignore network failure; definition remains empty
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String body = response.body().string();
                        CleanedDefinition result = selectBestDefinition(body);
                        if (result != null) {
                            Platform.runLater(() -> model.setDefinition(result.pos, result.def));
                        }
                    }
                }
            }
        });
    }

    private record CleanedDefinition(String pos, String def, int score) {}

    private CleanedDefinition selectBestDefinition(String json) {
        Matcher m = DEFS_ENTRY_PATTERN.matcher(json);
        CleanedDefinition best = null;
        int count = 0;
        while (m.find() && count < 8) {
            count++;
            CleanedDefinition candidate = cleanDefinitionEntry(m.group(1), m.group(2));
            if (candidate != null && candidate.score > 0) {
                if (best == null || candidate.score > best.score) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private CleanedDefinition cleanDefinitionEntry(String rawPos, String rawDef) {
        String pos = mapPartOfSpeech(rawPos);
        String def = rawDef.replace("\\\"", "\"").replace("\\\\", "\\").trim();

        // 1. Unwrap cross-references (e.g. "Alternative form of ogle. [(transitive) To stare...]")
        Matcher mBracket = CROSS_REF_BRACKET_PATTERN.matcher(def);
        if (mBracket.find()) {
            def = mBracket.group(1).trim();
        } else {
            Matcher mQuote = CROSS_REF_QUOTE_PATTERN.matcher(def);
            if (mQuote.find()) {
                def = mQuote.group(1).trim();
            }
        }

        // 2. Strip leading parenthetical tags e.g. (countable), (transitive, intransitive), (botany)
        Matcher mTags = LEADING_TAGS_PATTERN.matcher(def);
        if (mTags.find()) {
            def = def.substring(mTags.end()).trim();
        }

        // Strip enclosing square brackets
        if (def.startsWith("[") && def.endsWith("]")) {
            def = def.substring(1, def.length() - 1).trim();
            Matcher mInnerTags = LEADING_TAGS_PATTERN.matcher(def);
            if (mInnerTags.find()) {
                def = def.substring(mInnerTags.end()).trim();
            }
        }

        // 3. Truncate long descriptions to first sentence if > 140 chars
        if (def.length() > 140) {
            Matcher sm = FIRST_SENTENCE_PATTERN.matcher(def);
            if (sm.find() && sm.group(1).length() >= 25) {
                def = sm.group(1).trim();
            }
        }

        if (def.isEmpty()) {
            return null;
        }

        // Capitalize and format
        def = Character.toUpperCase(def.charAt(0)) + def.substring(1);
        if (!def.endsWith(".")) {
            def = def + ".";
        }

        // 4. Scoring suitability for everyday players
        int score = 100;
        String lower = def.toLowerCase();
        if (lower.startsWith("a surname") || lower.startsWith("a placename") || lower.startsWith("a town")
                || lower.startsWith("a city") || lower.startsWith("an unincorporated")) {
            score -= 100;
        }
        if (rawDef.toLowerCase().contains("(obsolete)") || rawDef.toLowerCase().contains("(archaic)")) {
            score -= 50;
        }
        if (rawDef.toLowerCase().contains("(rare)") || rawDef.toLowerCase().contains("(slang)")
                || rawDef.toLowerCase().contains("(dialectal)")) {
            score -= 25;
        }
        if (def.length() >= 25 && def.length() <= 120) {
            score += 25;
        }

        return new CleanedDefinition(pos, def, score);
    }

    private static String mapPartOfSpeech(String raw) {
        if (raw == null) {
            return "";
        }
        return switch (raw.trim().toLowerCase()) {
            case "n" -> "noun";
            case "v" -> "verb";
            case "adj" -> "adjective";
            case "adv" -> "adverb";
            case "prep" -> "preposition";
            case "conj" -> "conjunction";
            case "pron" -> "pronoun";
            case "interj" -> "interjection";
            case "u" -> "";
            default -> raw.trim();
        };
    }

    @Override
    public void useHint() {
        model.useHint();
    }

    @Override
    public String getInput() {
        return input;
    }
}