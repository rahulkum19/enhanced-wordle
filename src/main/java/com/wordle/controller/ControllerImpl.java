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
    private static final Pattern DEFS_PATTERN = Pattern.compile("\"defs\":\\s*\\[\\s*\"([^\"]+)\"");

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
                        Matcher m = DEFS_PATTERN.matcher(body);
                        if (m.find()) {
                            String raw = m.group(1).trim();
                            String pos = "";
                            String def = raw;
                            if (raw.contains("\t")) {
                                String[] parts = raw.split("\t", 2);
                                pos = switch (parts[0].toLowerCase()) {
                                    case "n" -> "noun";
                                    case "v" -> "verb";
                                    case "adj" -> "adjective";
                                    case "adv" -> "adverb";
                                    default -> parts[0];
                                };
                                def = parts[1].trim();
                            }
                            final String finalPos = pos;
                            final String finalDef = def;
                            Platform.runLater(() -> model.setDefinition(finalPos, finalDef));
                        }
                    }
                }
            }
        });
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