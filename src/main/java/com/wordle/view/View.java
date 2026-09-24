package com.wordle.view;

import com.wordle.controller.Controller;
import com.wordle.model.Model;
import com.wordle.model.Observer;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;

public class View implements FXComponent, Observer {
    private static final String COLOR_CORRECT = "#6aaa64";
    private static final String COLOR_PRESENT = "#c9b458";
    private static final String COLOR_ABSENT = "#787c7e";
    private static final String COLOR_DEFAULT = "#d3d6da";
    private static final String COLOR_HARD_MODE = "#cc4d4d";
    private static final String COLOR_TYPING_BORDER = "#878a8c";

    private static final String KEY_STYLE_DEFAULT = "-fx-background-color: #d3d6da; -fx-text-fill: black;";
    private static final String KEY_STYLE_CORRECT = "-fx-background-color: #6aaa64; -fx-text-fill: white;";
    private static final String KEY_STYLE_PRESENT = "-fx-background-color: #c9b458; -fx-text-fill: white;";
    private static final String KEY_STYLE_ABSENT = "-fx-background-color: #787c7e; -fx-text-fill: white;";

    private final Controller controller;
    private final Model model;
    private final Stage stage;

    private boolean winAnimationPlayed = false;
    private final List<StackPane> winningTiles = new ArrayList<>();

    public View(Controller controller, Model model, Stage stage) {
        this.controller = controller;
        this.model = model;
        this.stage = stage;
    }

    @Override
    public Parent render() {
        StackPane root = new StackPane();
        BorderPane pane = new BorderPane();
        VBox header = renderHeader();
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(20, 0, 0, 0));
        pane.setTop(header);

        pane.getStyleClass().add("main-background");
        pane.setCenter(renderGrid());

        VBox bottom = new VBox(15);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(0, 0, 30, 0));

        if (model.canUseHint()) {
            Button hintButton = new Button("REVEAL HINT");
            hintButton.setStyle("-fx-background-color: #6aaa64; -fx-text-fill: black; -fx-font-weight: bold; -fx-padding: 10 20; -fx-cursor: hand;");
            hintButton.setOnAction(e -> controller.useHint());
            VBox.setMargin(hintButton, new Insets(0, 0, 15, 0));
            bottom.getChildren().add(hintButton);
        }
        if (model.getModeStatus() != Model.MODE.HARD) {
            VBox keyboard = renderKeyboard();
            keyboard.setAlignment(Pos.CENTER);
            keyboard.setPadding(new Insets(0));
            bottom.getChildren().add(keyboard);
        } else {
            Label noKeyboard = new Label("KEYBOARD DISABLED");
            noKeyboard.setStyle("-fx-font-weight: bold; -fx-font-size: 25px;");
            noKeyboard.setPadding(new Insets(0, 0, 125, 0));
            bottom.getChildren().add(noKeyboard);
        }
        pane.setBottom(bottom);
        root.getChildren().add(pane);

        if (model.getStatus() == Model.STATUS.END_GAME) {
            List<String> guesses = model.getGuesses();
            boolean win = !guesses.isEmpty() && guesses.get(guesses.size() - 1).equals(model.getTargetWord());
            if (win && !winAnimationPlayed) {
                Platform.runLater(() -> playVictoryAnimation(root));
            } else {
                root.getChildren().add(renderEndGameModal(false));
            }
        }
        return root;
    }

    private void playVictoryAnimation(StackPane root) {
        if (winningTiles.isEmpty()) {
            winAnimationPlayed = true;
            root.getChildren().add(renderEndGameModal(true));
            return;
        }

        Pane confettiLayer = new Pane();
        confettiLayer.setMouseTransparent(true);
        root.getChildren().add(confettiLayer);

        SequentialTransition sequence = new SequentialTransition();

        // Stagger each letter's pop and confetti burst
        for (int i = 0; i < winningTiles.size(); i++) {
            final StackPane tile = winningTiles.get(i);
            PauseTransition pause = new PauseTransition(Duration.millis(i == 0 ? 80 : 120));
            pause.setOnFinished(e -> {
                // Letter expand and bounce back
                ScaleTransition st = new ScaleTransition(Duration.millis(180), tile);
                st.setFromX(1.0);
                st.setFromY(1.0);
                st.setToX(1.26);
                st.setToY(1.26);
                st.setAutoReverse(true);
                st.setCycleCount(2);
                st.setInterpolator(Interpolator.EASE_OUT);
                st.play();

                // Small green and gold confetti burst
                spawnConfetti(tile, confettiLayer);
            });
            sequence.getChildren().add(pause);
        }

        // Brief pause after the last letter pops, then smoothly bring up the victory modal
        PauseTransition settlePause = new PauseTransition(Duration.millis(420));
        settlePause.setOnFinished(e -> {
            winAnimationPlayed = true;
            VBox overlay = renderEndGameModal(true);
            root.getChildren().add(overlay);
        });
        sequence.getChildren().add(settlePause);
        sequence.play();
    }

    private void spawnConfetti(StackPane tile, Pane confettiLayer) {
        Point2D center = tile.localToScene(tile.getWidth() / 2.0, tile.getHeight() / 2.0);
        Point2D local = (center != null) ? confettiLayer.sceneToLocal(center) : null;
        double startX = (local != null) ? local.getX() : 350;
        double startY = (local != null) ? local.getY() : 300;

        Color[] colors = {
                Color.web("#6aaa64"), Color.web("#538d4e"), Color.web("#82e078"),
                Color.web("#b4f0ac"), Color.web("#f3c23e"), Color.web("#ffd700"), Color.web("#ffffff")
        };

        for (int k = 0; k < 14; k++) {
            Rectangle p = new Rectangle(4.5 + Math.random() * 3.5, 4.5 + Math.random() * 3.5);
            p.setFill(colors[(int) (Math.random() * colors.length)]);
            p.setArcWidth(2);
            p.setArcHeight(2);
            p.setLayoutX(startX);
            p.setLayoutY(startY);

            double angle = Math.random() * 2 * Math.PI;
            double dist = 28 + Math.random() * 50;
            double targetX = Math.cos(angle) * dist;
            double targetY = Math.sin(angle) * dist + (10 + Math.random() * 15);

            TranslateTransition tt = new TranslateTransition(Duration.millis(420 + Math.random() * 220), p);
            tt.setToX(targetX);
            tt.setToY(targetY);
            tt.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition ft = new FadeTransition(Duration.millis(450 + Math.random() * 200), p);
            ft.setFromValue(1.0);
            ft.setToValue(0.0);

            RotateTransition rt = new RotateTransition(Duration.millis(450), p);
            rt.setByAngle((Math.random() - 0.5) * 360);

            ParallelTransition pt = new ParallelTransition(tt, ft, rt);
            pt.setOnFinished(ev -> confettiLayer.getChildren().remove(p));
            confettiLayer.getChildren().add(p);
            pt.play();
        }
    }

    private Node createStar(boolean filled) {
        SVGPath star = new SVGPath();
        star.setContent("M 12 1.5 L 15.2 8.2 L 22.5 9.2 L 17.2 14.4 L 18.5 21.5 L 12 18 L 5.5 21.5 L 6.8 14.4 L 1.5 9.2 L 8.8 8.2 Z");
        if (filled) {
            star.setFill(Color.web("#f3c23e"));
            star.setStroke(Color.web("#d4a017"));
            star.setStrokeWidth(1);
            DropShadow glow = new DropShadow();
            glow.setColor(Color.rgb(243, 194, 62, 0.45));
            glow.setRadius(6);
            star.setEffect(glow);
        } else {
            star.setFill(Color.web("#2c2c2e"));
            star.setStroke(Color.web("#505054"));
            star.setStrokeWidth(1.2);
        }
        return star;
    }

    private VBox createWordShowcaseCard(String word, String partOfSpeech, String definition, boolean win) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12, 16, 12, 16));
        box.setStyle("-fx-background-color: rgba(255, 255, 255, 0.04); " +
                "-fx-background-radius: 12px; " +
                "-fx-border-color: rgba(255, 255, 255, 0.08); " +
                "-fx-border-radius: 12px;");

        HBox wordLine = new HBox(10);
        wordLine.setAlignment(Pos.CENTER_LEFT);

        Label wordLabel = new Label(word);
        wordLabel.setStyle("-fx-font-size: 19px; -fx-font-weight: bold; -fx-text-fill: " + (win ? "#6aaa64;" : "#f3c23e;"));
        wordLine.getChildren().add(wordLabel);

        if ((partOfSpeech == null || partOfSpeech.isEmpty()) && definition != null) {
            String[] parts = definition.split("\t|\\\\t", 2);
            if (parts.length == 2) {
                partOfSpeech = switch (parts[0].trim().toLowerCase()) {
                    case "n" -> "noun";
                    case "v" -> "verb";
                    case "adj" -> "adjective";
                    case "adv" -> "adverb";
                    case "prep" -> "preposition";
                    case "conj" -> "conjunction";
                    case "pron" -> "pronoun";
                    case "interj" -> "interjection";
                    case "u" -> "";
                    default -> parts[0].trim();
                };
                definition = parts[1].replace("\\\"", "\"").replace("\\\\", "\\").trim();
            }
        }

        if (partOfSpeech != null && !partOfSpeech.isEmpty()) {
            Label posLabel = new Label(partOfSpeech);
            posLabel.setStyle("-fx-font-size: 11px; -fx-font-style: italic; -fx-text-fill: #9ea4b0; " +
                    "-fx-background-color: rgba(255, 255, 255, 0.07); -fx-padding: 2 8; -fx-background-radius: 8px;");
            wordLine.getChildren().add(posLabel);
        }

        box.getChildren().add(wordLine);

        String defText = (definition != null && !definition.isEmpty())
                ? definition
                : "Definition unavailable.";

        Label defLabel = new Label(defText);
        defLabel.setWrapText(true);
        defLabel.setMaxWidth(390);
        defLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #c4cbd4; -fx-line-spacing: 2px;");
        box.getChildren().add(defLabel);

        return box;
    }

    private VBox renderEndGameModal(boolean animate) {
        List<String> guesses = model.getGuesses();
        boolean win = !guesses.isEmpty() && guesses.get(guesses.size() - 1).equals(model.getTargetWord());

        VBox card = new VBox(16);
        card.setMaxWidth(460);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(26, 30, 26, 30));

        String borderColor = win ? "rgba(106, 170, 100, 0.45)" : "rgba(204, 77, 77, 0.45)";
        card.setStyle("-fx-background-color: linear-gradient(to bottom, #23272e 0%, #16191e 100%); " +
                "-fx-background-radius: 22px; " +
                "-fx-border-color: " + borderColor + "; " +
                "-fx-border-radius: 22px; " +
                "-fx-border-width: 1.5px;");

        DropShadow ambientShadow = new DropShadow();
        ambientShadow.setColor(win ? Color.rgb(106, 170, 100, 0.28) : Color.rgb(204, 77, 77, 0.28));
        ambientShadow.setRadius(35);
        ambientShadow.setSpread(0.1);
        card.setEffect(ambientShadow);

        // 1. Status Badge
        String badgeText = win ? "✦ VICTORY ✦" : "✦ ROUND COMPLETE ✦";
        Label badgeLabel = new Label(badgeText);
        String badgeBg = win ? "rgba(106, 170, 100, 0.15)" : "rgba(204, 77, 77, 0.15)";
        String badgeBorder = win ? "rgba(106, 170, 100, 0.35)" : "rgba(204, 77, 77, 0.35)";
        String badgeTextFill = win ? "#6aaa64" : "#cc4d4d";
        badgeLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + badgeTextFill + "; " +
                "-fx-background-color: " + badgeBg + "; -fx-border-color: " + badgeBorder + "; " +
                "-fx-background-radius: 20px; -fx-border-radius: 20px; -fx-padding: 4 16;");

        // 2. Headline & Stars
        int stars = model.getRoundStarsEarned();
        String ratingText = win ? switch (stars) {
            case 5 -> "Flawless!";
            case 4 -> "Magnificent!";
            case 3 -> "Well Done!";
            case 2 -> "Good Job!";
            default -> "Cleared!";
        } : "Game Over";

        Label titleLabel = new Label(ratingText);
        titleLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        HBox starsBox = new HBox(8);
        starsBox.setAlignment(Pos.CENTER);
        for (int i = 1; i <= 5; i++) {
            starsBox.getChildren().add(createStar(win && i <= stars));
        }
        starsBox.setPadding(new Insets(4, 16, 4, 16));
        starsBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.03); -fx-background-radius: 20px;");

        card.getChildren().addAll(badgeLabel, titleLabel, starsBox);

        // 3. Word Spotlight with Definition
        VBox wordShowcase = createWordShowcaseCard(model.getTargetWord(), model.getPartOfSpeech(), model.getDefinition(), win);
        card.getChildren().add(wordShowcase);

        // 4. Points Breakdown (if win) or Consolation (if loss)
        if (win) {
            VBox breakdownBox = new VBox(6);
            breakdownBox.setPadding(new Insets(10, 16, 10, 16));
            breakdownBox.setStyle("-fx-background-color: rgba(0, 0, 0, 0.25); -fx-background-radius: 12px; -fx-border-color: rgba(255, 255, 255, 0.05); -fx-border-radius: 12px;");

            int numGuesses = guesses.size();
            int remainingGuesses = Math.max(0, model.getMaxAttempts() - numGuesses);
            double multiplier = switch (model.getModeStatus()) {
                case EASY -> 1.0;
                case MEDIUM -> 1.2;
                case HARD -> 1.5;
            };

            breakdownBox.getChildren().add(createReceiptLine("Base Victory:", "+500 pts"));
            breakdownBox.getChildren().add(createReceiptLine("Unused Attempts (" + remainingGuesses + " left):", "+" + (remainingGuesses * 100) + " pts"));
            if (!model.didUseHintThisRound()) {
                breakdownBox.getChildren().add(createReceiptLine("No Hints Bonus:", "+100 pts"));
            }
            if (multiplier > 1.0) {
                breakdownBox.getChildren().add(createReceiptLine(model.getModeStatus() + " Multiplier:", multiplier + "x"));
            }

            Label line = new Label();
            line.setMaxWidth(Double.MAX_VALUE);
            line.setStyle("-fx-border-color: rgba(255, 255, 255, 0.1); -fx-border-width: 0.5 0 0 0;");
            breakdownBox.getChildren().add(line);

            breakdownBox.getChildren().add(createReceiptLine("Round Total:", "+" + model.getRoundPointsEarned() + " pts", true));
            card.getChildren().add(breakdownBox);
        } else {
            if (model.getRoundPointsEarned() > 0) {
                HBox consolationBox = new HBox();
                consolationBox.setAlignment(Pos.CENTER);
                consolationBox.setPadding(new Insets(8, 14, 8, 14));
                consolationBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.04); -fx-background-radius: 10px;");
                Label consolation = new Label("Consolation: +" + model.getRoundPointsEarned() + " pts (" + (model.getRoundPointsEarned() / 25) + " correct green letters)");
                consolation.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #9ea4b0;");
                consolationBox.getChildren().add(consolation);
                card.getChildren().add(consolationBox);
            }
        }

        // 5. Twin Bank Chips
        HBox bankSummary = new HBox(12);
        bankSummary.setAlignment(Pos.CENTER);

        String starsDelta = model.getRoundStarsEarned() > 0 ? " (+" + model.getRoundStarsEarned() + ")" : "";
        Label bankStars = new Label("⭐ " + model.getTotalStars() + " Stars" + starsDelta);
        bankStars.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #82e078; -fx-background-color: rgba(106, 170, 100, 0.15); -fx-padding: 6 14; -fx-background-radius: 12px;");

        String pointsDelta = model.getRoundPointsEarned() > 0 ? String.format(" (+%,d)", model.getRoundPointsEarned()) : "";
        Label bankPoints = new Label(String.format("🪙 %,d Pts%s", model.getTotalPoints(), pointsDelta));
        bankPoints.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f3c23e; -fx-background-color: rgba(243, 194, 62, 0.15); -fx-padding: 6 14; -fx-background-radius: 12px;");

        bankSummary.getChildren().addAll(bankStars, bankPoints);
        card.getChildren().add(bankSummary);

        // 6. Action Button
        Button restart = new Button(win ? "Play Next Round ➔" : "Try Again ➔");
        restart.setOnAction(e -> controller.getTargetWord());
        restart.setStyle("-fx-background-color: linear-gradient(to right, #6aaa64, #538d4e); " +
                "-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; " +
                "-fx-padding: 11 32; -fx-background-radius: 30px; -fx-cursor: hand;");
        restart.setOnMouseEntered(e -> restart.setStyle("-fx-background-color: linear-gradient(to right, #79b872, #5fa359); " +
                "-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; " +
                "-fx-padding: 11 32; -fx-background-radius: 30px; -fx-cursor: hand;"));
        restart.setOnMouseExited(e -> restart.setStyle("-fx-background-color: linear-gradient(to right, #6aaa64, #538d4e); " +
                "-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; " +
                "-fx-padding: 11 32; -fx-background-radius: 30px; -fx-cursor: hand;"));

        DropShadow btnGlow = new DropShadow();
        btnGlow.setColor(Color.rgb(106, 170, 100, 0.4));
        btnGlow.setRadius(12);
        restart.setEffect(btnGlow);

        card.getChildren().add(restart);

        VBox overlay = new VBox(card);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(15, 15, 15, 0.78);");

        if (animate) {
            overlay.setOpacity(0);
            FadeTransition bgFade = new FadeTransition(Duration.millis(320), overlay);
            bgFade.setFromValue(0);
            bgFade.setToValue(1);
            bgFade.play();

            card.setScaleX(0.78);
            card.setScaleY(0.78);
            card.setOpacity(0);

            ScaleTransition cardScale = new ScaleTransition(Duration.millis(380), card);
            cardScale.setFromX(0.78);
            cardScale.setFromY(0.78);
            cardScale.setToX(1.0);
            cardScale.setToY(1.0);
            cardScale.setInterpolator(Interpolator.EASE_OUT);

            FadeTransition cardFade = new FadeTransition(Duration.millis(300), card);
            cardFade.setFromValue(0);
            cardFade.setToValue(1);

            ParallelTransition cardEntrance = new ParallelTransition(cardScale, cardFade);
            cardEntrance.play();
        }

        return overlay;
    }

    private HBox createReceiptLine(String left, String right) {
        return createReceiptLine(left, right, false);
    }

    private HBox createReceiptLine(String left, String right, boolean isBold) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);

        Label l = new Label(left);
        String baseStyle = "-fx-font-size: 13px; -fx-text-fill: " + (isBold ? "#ffffff;" : "#b0b0b0;");
        if (isBold) {
            baseStyle += " -fx-font-weight: bold;";
        }
        l.setStyle(baseStyle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label r = new Label(right);
        String rightStyle = "-fx-font-size: 13px; -fx-text-fill: " + (isBold ? "#6aaa64;" : "#d0d0d0;");
        if (isBold) {
            rightStyle += " -fx-font-weight: bold;";
        }
        r.setStyle(rightStyle);

        row.getChildren().addAll(l, spacer, r);
        return row;
    }

    private void buttonStyle(Button b, Model.MODE m, boolean isActive) {
        String baseStyle = "-fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 6 14; -fx-font-size: 13px; ";
        if (isActive) {
            String activeColor = switch (m) {
                case EASY -> COLOR_CORRECT;
                case MEDIUM -> COLOR_PRESENT;
                case HARD -> COLOR_HARD_MODE;
            };
            b.setStyle(baseStyle + "-fx-background-color: " + activeColor + "; -fx-text-fill: white; -fx-border-color: " + activeColor + "; -fx-background-radius: 4px; -fx-border-radius: 4px;");
        } else {
            b.setStyle(baseStyle + "-fx-background-color: #ffffff; -fx-text-fill: #787c7e; -fx-border-color: #d3d6da; -fx-border-width: 1; -fx-background-radius: 4px; -fx-border-radius: 4px;");
        }
        b.setOnMouseEntered(e -> {
            if (!isActive) b.setStyle(b.getStyle() + "-fx-background-color: #f8f8f8;");
        });
        b.setOnMouseExited(e -> {
            if (!isActive) b.setStyle(b.getStyle() + "-fx-background-color: #ffffff;");
        });
    }

    private VBox renderHeader() {
        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);

        // HUD Bar with Stars and Points
        HBox hud = new HBox();
        hud.setMaxWidth(460);
        hud.setAlignment(Pos.CENTER);
        hud.setPadding(new Insets(6, 14, 6, 14));
        hud.setStyle("-fx-background-color: #f4f5f7; -fx-background-radius: 8px; -fx-border-color: #d3d6da; -fx-border-radius: 8px; -fx-border-width: 1px;");

        Label starsBadge = new Label(String.format("⭐ %d Stars", model.getTotalStars()));
        starsBadge.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #333333;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label pointsBadge = new Label(String.format("🪙 %,d Pts", model.getTotalPoints()));
        pointsBadge.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        hud.getChildren().addAll(starsBadge, spacer, pointsBadge);
        header.getChildren().add(hud);

        // Difficulty Selector
        HBox difficulty = new HBox(8);
        difficulty.setAlignment(Pos.CENTER);
        for (Model.MODE m : Model.MODE.values()) {
            Button b = new Button(m.toString());
            boolean activeButton = (model.getModeStatus() == m);
            buttonStyle(b, m, activeButton);
            b.setOnAction(e -> {
                model.setModeStatus(m);
                controller.getTargetWord();
            });
            difficulty.getChildren().add(b);
        }
        header.getChildren().add(difficulty);
        return header;
    }

    private GridPane renderGrid() {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(8);
        grid.setVgap(8);

        winningTiles.clear();

        int hintIndex = model.getHintIndex();
        List<String> guesses = model.getGuesses();
        String input = controller.getInput();
        int rows = model.getMaxAttempts();

        int lastGuessRow = guesses.size() - 1;
        boolean isWin = (model.getStatus() == Model.STATUS.END_GAME)
                && !guesses.isEmpty()
                && guesses.get(lastGuessRow).equals(model.getTargetWord());

        for (int r = 0; r < rows; r++) {
            String[] rowStyles = (r < guesses.size()) ? getRowStyles(guesses.get(r)) : null;
            for (int c = 0; c < 5; c++) {
                StackPane tile = new StackPane();
                tile.setMinSize(60, 60);
                tile.setPrefSize(60, 60);
                tile.setMaxSize(60, 60);

                Label l = new Label();
                String text = "";
                String bgColor = "white";
                String borderColor = COLOR_DEFAULT;
                String textColor = "black";

                if (r < guesses.size()) {
                    String guess = guesses.get(r);
                    if (c < guess.length()) {
                        text = String.valueOf(guess.charAt(c));
                    }
                    textColor = "white";

                    if (rowStyles != null && "tile-correct".equals(rowStyles[c])) {
                        bgColor = COLOR_CORRECT;
                        borderColor = COLOR_CORRECT;
                    } else if (rowStyles != null && "tile-present".equals(rowStyles[c])) {
                        bgColor = COLOR_PRESENT;
                        borderColor = COLOR_PRESENT;
                    } else {
                        bgColor = COLOR_ABSENT;
                        borderColor = COLOR_ABSENT;
                    }
                } else if (r == guesses.size() && model.getStatus() == Model.STATUS.IN_PROGRESS) {
                    if (c < input.length()) {
                        text = String.valueOf(input.charAt(c));
                        borderColor = COLOR_TYPING_BORDER;
                    } else if (c == hintIndex && c < model.getTargetWord().length()) {
                        text = String.valueOf(model.getTargetWord().charAt(c));
                        borderColor = COLOR_CORRECT;
                        textColor = COLOR_CORRECT;
                        tile.setOpacity(0.7);
                    }
                }

                l.setText(text);
                l.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";");
                tile.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: " + borderColor + "; -fx-border-width: 2;");
                tile.getChildren().add(l);
                grid.add(tile, c, r);

                if (r == lastGuessRow && isWin) {
                    winningTiles.add(tile);
                }
            }
        }
        return grid;
    }

    private String[] getRowStyles(String guess) {
        String target = model.getTargetWord();
        String[] styles = new String[5];
        if (target == null || target.isEmpty()) {
            return styles;
        }

        List<Character> letters = new ArrayList<>();
        for (int i = 0; i < 5 && i < target.length(); i++) {
            letters.add(target.charAt(i));
        }

        for (int i = 0; i < 5 && i < guess.length(); i++) {
            if (guess.charAt(i) == target.charAt(i)) {
                styles[i] = "tile-correct";
                letters.remove(Character.valueOf(guess.charAt(i)));
            }
        }

        for (int i = 0; i < 5 && i < guess.length(); i++) {
            if (styles[i] == null) {
                if (letters.remove(Character.valueOf(guess.charAt(i)))) {
                    styles[i] = "tile-present";
                } else {
                    styles[i] = "tile-absent";
                }
            }
        }
        return styles;
    }

    private VBox renderKeyboard() {
        VBox keyboard = new VBox(8);
        keyboard.setAlignment(Pos.CENTER);
        String[] keyRows = {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"};
        String baseStyle = "-fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand; -fx-font-size: 14px;";

        Map<Character, String> keyColors = computeKeyboardColors();

        for (int i = 0; i < keyRows.length; i++) {
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER);
            String rowLetters = keyRows[i];

            for (int j = 0; j < rowLetters.length(); j++) {
                char c = rowLetters.charAt(j);
                Button key = new Button(String.valueOf(c));
                key.setPrefSize(45, 55);
                key.setStyle(baseStyle + keyColors.getOrDefault(c, KEY_STYLE_DEFAULT));
                key.setOnAction(e -> controller.processKeyPress(String.valueOf(c)));
                row.getChildren().add(key);
            }

            if (i == 0) {
                Button enter = new Button("⟵");
                enter.setPrefSize(65, 58);
                enter.setStyle(baseStyle + KEY_STYLE_DEFAULT);
                enter.setOnAction(e -> controller.processKeyPress("BACK_SPACE"));
                row.getChildren().add(enter);
            }

            if (i == 2) {
                Button back = new Button("ENTER");
                back.setPrefSize(65, 58);
                back.setStyle(baseStyle + KEY_STYLE_DEFAULT);
                back.setOnAction(e -> controller.processKeyPress("ENTER"));
                row.getChildren().add(back);
            }

            keyboard.getChildren().add(row);
        }
        return keyboard;
    }

    private Map<Character, String> computeKeyboardColors() {
        Map<Character, String> colorMap = new HashMap<>();
        String word = model.getTargetWord();
        if (word == null || word.isEmpty()) {
            return colorMap;
        }

        List<String> guesses = model.getGuesses();
        for (String guess : guesses) {
            int len = Math.min(guess.length(), word.length());
            for (int i = 0; i < len; i++) {
                char c = guess.charAt(i);
                if (word.charAt(i) == c) {
                    colorMap.put(c, KEY_STYLE_CORRECT);
                } else if (word.indexOf(c) >= 0) {
                    if (!KEY_STYLE_CORRECT.equals(colorMap.get(c))) {
                        colorMap.put(c, KEY_STYLE_PRESENT);
                    }
                } else {
                    if (!colorMap.containsKey(c)) {
                        colorMap.put(c, KEY_STYLE_ABSENT);
                    }
                }
            }
        }
        return colorMap;
    }

    public void shakeRow() {
        Parent root = stage.getScene().getRoot();
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), root);

        shake.setFromX(0);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setInterpolator(Interpolator.LINEAR);
        shake.play();
    }

    @Override
    public void update() {
        Platform.runLater(() -> {
            if (stage == null || stage.getScene() == null) {
                return;
            }
            if (model.getStatus() != Model.STATUS.END_GAME) {
                winAnimationPlayed = false;
            }
            Parent root = render();
            stage.getScene().setRoot(root);
            if (model.getAndClearInvalidWord()) {
                shakeRow();
            }
        });
    }
}
