package com.wordle.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

public class ModelImpl implements Model {
    private static final String PREF_KEY_TOTAL_POINTS = "wordle_total_points";
    private static final String PREF_KEY_TOTAL_STARS = "wordle_total_stars";
    private final Preferences prefs = Preferences.userNodeForPackage(ModelImpl.class);

    private String word = "";
    private final List<String> guesses;
    private final List<String> grayLetters;
    private final Set<String> grayLettersSet;
    private final List<Observer> observers;
    private STATUS status;
    private MODE mode;
    private boolean invalidWord = false;
    private int hintIndex = -1;

    // Points & Stars state
    private int totalPoints;
    private int totalStars;
    private int roundPointsEarned = 0;
    private int roundStarsEarned = 0;
    private boolean usedHintThisRound = false;

    // Word Definition state
    private String definition = "";
    private String partOfSpeech = "";

    public ModelImpl(String word) {
        this.word = word == null ? "" : word.toUpperCase();
        this.guesses = new ArrayList<>();
        this.grayLetters = new ArrayList<>();
        this.grayLettersSet = new HashSet<>();
        this.observers = new ArrayList<>();
        this.status = STATUS.START_GAME;
        this.mode = MODE.EASY;
        this.hintIndex = -1;

        this.totalPoints = prefs.getInt(PREF_KEY_TOTAL_POINTS, 0);
        this.totalStars = prefs.getInt(PREF_KEY_TOTAL_STARS, 0);
    }

    private void resetInternalState() {
        guesses.clear();
        grayLetters.clear();
        grayLettersSet.clear();
        status = STATUS.IN_PROGRESS;
        hintIndex = -1;
        roundPointsEarned = 0;
        roundStarsEarned = 0;
        usedHintThisRound = false;
        definition = "";
        partOfSpeech = "";
    }

    @Override
    public void startGame() {
        resetInternalState();
        notifyObservers();
    }

    @Override
    public void makeGuess(String guess) {
        if (status != STATUS.IN_PROGRESS || guess == null) {
            return;
        }
        guess = guess.toUpperCase();
        guesses.add(guess);
        updateGrayLetters(guess);

        boolean won = guess.equals(word);
        boolean lost = !won && (guesses.size() >= getMaxAttempts());

        if (won || lost) {
            status = STATUS.END_GAME;
            calculateRoundScore(won);
        }
        notifyObservers();
    }

    private void calculateRoundScore(boolean won) {
        if (won) {
            int numGuesses = guesses.size();
            int remainingGuesses = Math.max(0, getMaxAttempts() - numGuesses);

            // Calculate Stars (1 to 5) based on difficulty and guess efficiency
            int baseStars;
            if (mode == MODE.HARD) {
                // Hard Mode (5 max attempts, keyboard disabled, no hints):
                if (numGuesses <= 3) {
                    baseStars = 5;
                } else if (numGuesses == 4) {
                    baseStars = 4;
                } else {
                    baseStars = 3; // Guaranteed minimum 3 stars for clearing Hard Mode
                }
            } else if (mode == MODE.MEDIUM) {
                // Medium Mode (6 max attempts):
                if (numGuesses <= 3) {
                    baseStars = 5;
                } else if (numGuesses == 4) {
                    baseStars = 4;
                } else if (numGuesses == 5) {
                    baseStars = 3;
                } else {
                    baseStars = 2; // Solved on final 6th attempt
                }
            } else {
                // Easy Mode (7 max attempts):
                if (numGuesses <= 3) {
                    baseStars = 5;
                } else if (numGuesses == 4) {
                    baseStars = 4;
                } else if (numGuesses == 5) {
                    baseStars = 3;
                } else if (numGuesses == 6) {
                    baseStars = 2;
                } else {
                    baseStars = 1; // Solved on final 7th attempt
                }
            }

            // Using a hint applies a 1-star penalty (min 1 star on win)
            if (usedHintThisRound) {
                roundStarsEarned = Math.max(1, baseStars - 1);
            } else {
                roundStarsEarned = baseStars;
            }

            // Calculate Points
            int baseWin = 500;
            int remainingBonus = remainingGuesses * 100;
            int noHintBonus = usedHintThisRound ? 0 : 100;
            int subtotal = baseWin + remainingBonus + noHintBonus;

            double multiplier = switch (mode) {
                case EASY -> 1.0;
                case MEDIUM -> 1.2;
                case HARD -> 1.5;
            };

            roundPointsEarned = (int) Math.round(subtotal * multiplier);
        } else {
            // Consolation for game over
            roundStarsEarned = 0;
            roundPointsEarned = correctLetterCount() * 25;
        }

        totalPoints += roundPointsEarned;
        totalStars += roundStarsEarned;

        // Persist progress between app launches
        prefs.putInt(PREF_KEY_TOTAL_POINTS, totalPoints);
        prefs.putInt(PREF_KEY_TOTAL_STARS, totalStars);
    }

    @Override
    public void triggerInvalidWord() {
        this.invalidWord = true;
        notifyObservers();
    }

    @Override
    public boolean getAndClearInvalidWord() {
        boolean current = invalidWord;
        invalidWord = false;
        return current;
    }

    private void updateGrayLetters(String guess) {
        if (word == null) {
            return;
        }
        for (int i = 0; i < guess.length(); i++) {
            char c = guess.charAt(i);
            if (word.indexOf(c) < 0) {
                String letter = String.valueOf(c);
                if (grayLettersSet.add(letter)) {
                    grayLetters.add(letter);
                }
            }
        }
    }

    public int correctLetterCount() {
        if (word == null || word.isEmpty()) {
            return 0;
        }
        boolean[] discoveredLetters = new boolean[5];
        for (String guess : guesses) {
            int len = Math.min(5, Math.min(guess.length(), word.length()));
            for (int i = 0; i < len; i++) {
                if (guess.charAt(i) == word.charAt(i)) {
                    discoveredLetters[i] = true;
                }
            }
        }
        int count = 0;
        for (boolean bool : discoveredLetters) {
            if (bool) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean canUseHint() {
        if (mode == MODE.HARD || status != STATUS.IN_PROGRESS || hintIndex != -1) {
            return false;
        }
        return (guesses.size() > (getMaxAttempts() / 2)) && correctLetterCount() <= 2;
    }

    @Override
    public void useHint() {
        if (!canUseHint() || word == null) {
            return;
        }
        usedHintThisRound = true;
        for (int i = 0; i < 5; i++) {
            for (String guess : guesses) {
                if (i < guess.length() && i < word.length() && guess.charAt(i) != word.charAt(i)) {
                    hintIndex = i;
                }
            }
        }
        notifyObservers();
    }

    @Override
    public int getHintIndex() {
        return hintIndex;
    }

    @Override
    public int getMaxAttempts() {
        return switch (mode) {
            case EASY -> 7;
            case MEDIUM -> 6;
            case HARD -> 5;
        };
    }

    @Override
    public List<String> getGuesses() {
        return new ArrayList<>(guesses);
    }

    @Override
    public List<String> getGrayLetters() {
        return new ArrayList<>(grayLetters);
    }

    @Override
    public STATUS getStatus() {
        return status;
    }

    @Override
    public void setModeStatus(MODE mode) {
        this.mode = mode;
        notifyObservers();
    }

    @Override
    public MODE getModeStatus() {
        return mode;
    }

    @Override
    public String getTargetWord() {
        return word;
    }

    @Override
    public void setTargetWord(String target) {
        word = target == null ? "" : target.toUpperCase();
        status = STATUS.IN_PROGRESS;
        notifyObservers();
    }

    @Override
    public void resetGame() {
        resetInternalState();
        notifyObservers();
    }

    @Override
    public void addObserver(Observer o) {
        if (o == null) {
            throw new IllegalArgumentException();
        }
        if (!observers.contains(o)) {
            observers.add(o);
        }
    }

    private void notifyObservers() {
        for (Observer o : observers) {
            o.update();
        }
    }

    @Override
    public void update() {
        notifyObservers();
    }

    @Override
    public int getTotalPoints() {
        return totalPoints;
    }

    @Override
    public int getTotalStars() {
        return totalStars;
    }

    @Override
    public int getRoundPointsEarned() {
        return roundPointsEarned;
    }

    @Override
    public int getRoundStarsEarned() {
        return roundStarsEarned;
    }

    @Override
    public boolean didUseHintThisRound() {
        return usedHintThisRound;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    @Override
    public void setDefinition(String pos, String def) {
        this.partOfSpeech = (pos == null) ? "" : pos.trim();
        this.definition = (def == null) ? "" : def.trim();
        notifyObservers();
    }
}
