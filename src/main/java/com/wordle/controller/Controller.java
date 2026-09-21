package com.wordle.controller;

public interface Controller {

    void setEasyMode();
    void setMediumMode();
    void setHardMode();
    void startGame();
    void processKeyPress(String key);
    String getInput();
    void getTargetWord();
    void initializeWords();
    void useHint();
}
