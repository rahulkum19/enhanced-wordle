# Enhanced Wordle (JavaFX)

### A Desktop Word-Guessing Game with Extended Mechanics in Java & JavaFX
**Tech Stack:** Java 23 | JavaFX 21 | Apache Maven | MVC & Observer Architecture | OkHttp 4 | Datamuse REST API | Java Preferences API

---

## Overview

**Enhanced Wordle** is an extended version of the desktop Wordle application built in Java and JavaFX. While preserving the core rules and MVC structure of the original game, this project incorporates several key additions including a persistent scoring and star economy, real-time vocabulary definitions, optimized batch networking, and expanded visual feedback.

The project strictly follows the **Model-View-Controller (MVC)** and **Observer** design patterns, maintaining clean separation between game state, asynchronous dictionary communication, and reactive JavaFX rendering.

---

## What’s New: Additions & Enhancements Over Original Wordle

| Feature Category | Original Wordle | Enhanced Wordle |
| :--- | :--- | :--- |
| **Economy & Gamification** | Round-by-round only; no scoring or star system | **Persistent 5-Star Rating & Points System** with difficulty multipliers and clean-play bonuses |
| **Progress Persistence** | None (resets upon window close) | **Cross-Session Persistence** via Java Preferences API (`Preferences.userNodeForPackage`) |
| **Educational Features** | Word reveal on round end | **Real-Time Word Showcase** with part of speech and dictionary definition via Datamuse API |
| **Network & Performance** | Repeated individual network requests for each word and guess | **Batch Pre-fetching (500 words)** + **Thread-Safe Guess Cache** (`ConcurrentHashMap`) |
| **Visual Feedback** | Standard shake animation on invalid input | **Victory Tile Scaling** + **Procedural Particle Confetti** |
| **End-Game Screen** | Basic text alert | **Modal Overlay** with SVG star ratings, itemized score receipt, and bank summaries |
| **Player HUD** | Difficulty selector buttons | **Header HUD Bar** displaying lifetime Stars and Points alongside difficulty controls |

---

### 1. Persistent Gamification & Star Economy

- **Dynamic 5-Star Rating System**:
  - Stars are calculated based on difficulty mode and guess efficiency:
    - **Hard Mode**: Solved in $\le 3$ attempts = 5★, 4 attempts = 4★, 5 attempts = 3★ (clearing Hard Mode guarantees a minimum of 3 stars).
    - **Medium Mode**: $\le 3$ attempts = 5★, 4 attempts = 4★, 5 attempts = 3★, 6 attempts = 2★.
    - **Easy Mode**: $\le 3$ attempts = 5★, 4 attempts = 4★, 5 attempts = 3★, 6 attempts = 2★, 7 attempts = 1★.
  - **Hint Trade-Off Penalty**: Using an in-game hint incurs a 1-star penalty (with a 1-star floor on victory) to reward unassisted solves.
- **Points Scoring Model**:
  - **Base Win**: $+500$ points.
  - **Efficiency Bonus**: $+100$ points for each remaining unused attempt.
  - **Clean Play Bonus**: $+100$ points for clearing the puzzle without hints.
  - **Difficulty Multipliers**: Final round points are scaled by **1.0x** (Easy), **1.2x** (Medium), and **1.5x** (Hard).
  - **Consolation Reward**: On a loss, players receive $+25$ points per correctly identified green letter to reward partial progress.
- **Cross-Session Persistence**:
  - Total stars and points persist across application launches using the native Java Preferences API.
- **Header HUD Bar**:
  - Displays lifetime Stars (⭐) and Points (🪙) above the game grid.

---

### 2. Educational Integration & Word Definition Showcase

- **Live Dictionary Definition Retrieval**:
  - Upon selecting a target word, the controller asynchronously queries the Datamuse API (`sp=<word>&md=d&max=1`) to retrieve the word's definition and grammatical role.
- **Part-of-Speech Parser**:
  - Parses raw Datamuse definitions and maps grammatical abbreviations (`n`, `v`, `adj`, `adv`) into readable labels (*noun*, *verb*, *adjective*, *adverb*).
- **Post-Game Vocabulary Showcase**:
  - The end-game modal features an embedded word card displaying the target word, an italicized part-of-speech badge, and the dictionary definition.

---

### 3. Caching & Network Optimization

- **500-Word Batch Pre-fetching**:
  - Instead of making individual HTTP requests each time a mode changes or a new round starts, the controller pre-fetches a pool of 500 5-letter words with frequency scores (`sp=?????&md=f&max=500`).
- **Single-Pass In-Memory Indexing**:
  - Frequencies are parsed once into a typed `WordFrequency` list and sorted via primitive comparisons (`Double.compare`), enabling fast in-memory word selection across difficulty bands.
- **Multi-Tier Guess Fast-Path & Concurrent Caching**:
  - Guesses matching the target word bypass network validation immediately.
  - Validated words are memoized in a thread-safe `ConcurrentHashMap.newKeySet()`. Repeated guesses execute without additional network latency.

---

### 4. UI/UX Refinements & Visual Feedback

- **Staggered Victory Tile Bounce**:
  - Winning row tiles execute a sequenced `ScaleTransition` pop animation across the solved letters.
- **Procedural Particle Confetti**:
  - When a round is won, a particle system generates green, gold, and white confetti rectangles from each winning letter tile with randomized trajectories, rotation, and fade transitions.
- **End-Game Modal Overlay**:
  - Replaces standard alert dialogs with a styled dark overlay featuring status glows (green on victory, red on loss).
  - SVG star rating display with drop-shadow effects.
  - Itemized receipt breakdown showing base score, remaining attempt bonuses, difficulty multipliers, and total round earnings.
  - Animated primary action button for starting the next round.

---

## How Agentic AI Was Utilized

This project leveraged **Agentic AI** as an engineering tool throughout the development process—from architectural planning and feature extension to performance optimization and UI refinements.

### 1. Architectural Planning & Refactoring
- **Preserving MVC Integrity**: The agent analyzed the existing codebase and designed extensions for scoring, star progression, and dictionary lookups that preserved the decoupling of the **MVC** and **Observer** patterns.
- **Contract Expansion**: The `Model` and `ModelImpl` contracts were extended with dedicated getters and state mutators without introducing breaking changes to existing observer subscribers.

### 2. Performance Bottleneck Analysis & Caching
- **Network Profiling**: Agentic analysis identified latency bottlenecks caused by individual HTTP calls per word selection and guess submission.
- **Caching Pipeline**: The agent architected an in-memory batch pre-fetcher for 500 words, designed a typed `WordFrequency` container with primitive sorting, and integrated a `ConcurrentHashMap` set for non-blocking guess validation caching.

### 3. Game Economy & Scoring Balance
- **Formula Design**: Formulated balanced scoring equations combining base points, attempt bonuses, clean-play incentives, and difficulty multipliers ($1.0\times$ to $1.5\times$).
- **Star Allocation**: Developed star allocation thresholds tailored specifically to the attempt caps of each difficulty tier, factoring in hint penalties while ensuring a minimum reward on victory.

### 4. JavaFX Animations & UI Engineering
- **Particle Animation**: The agent formulated and implemented the confetti particle system, calculating trajectory vectors, gravity drift, rotation, and fade interpolations using native JavaFX animation classes (`TranslateTransition`, `RotateTransition`, `FadeTransition`, `ParallelTransition`).
- **UI Styling**: Designed dark-mode CSS styling, dynamic SVG star paths, and modal card layouts with responsive alignments.

---

## Architecture & Design Patterns

### Model-View-Controller (MVC)

- **Model (`com.wordle.model`)**:
  - [`Model`](file:///Users/kumaresan/IdeaProjects/enhanced-wordle/src/main/java/com/wordle/model/Model.java): Complete contract exposing game lifecycle, guesses, grayed letters, difficulty modes, hint states, points/stars economy, and word definition data.
  - [`ModelImpl`](file:///Users/kumaresan/IdeaProjects/enhanced-wordle/src/main/java/com/wordle/model/ModelImpl.java): Core business logic handling attempt evaluation, letter matching, scoring formulas, star ratings, and local state persistence via the Java Preferences API.
  - [`Subject`](file:///Users/kumaresan/IdeaProjects/enhanced-wordle/src/main/java/com/wordle/model/Subject.java) & [`Observer`](file:///Users/kumaresan/IdeaProjects/enhanced-wordle/src/main/java/com/wordle/model/Observer.java): Observer pattern implementation decoupling state mutations from view updates.

- **Controller (`com.wordle.controller`)**:
  - [`Controller`](file:///Users/kumaresan/IdeaProjects/enhanced-wordle/src/main/java/com/wordle/controller/Controller.java): Input management interface handling keystrokes, difficulty shifts, and game restarts.
  - [`ControllerImpl`](file:///Users/kumaresan/IdeaProjects/enhanced-wordle/src/main/java/com/wordle/controller/ControllerImpl.java): Coordinates user input, manages the OkHttp client, executes the 500-word batch pre-fetch, queries word definitions asynchronously, and maintains the concurrent guess validation cache.

- **View (`com.wordle.view`)**:
  - [`AppLauncher`](file:///Users/kumaresan/IdeaProjects/enhanced-wordle/src/main/java/com/wordle/view/AppLauncher.java): Application bootstrapper initializing MVC components, scene hierarchy, and keyboard event handlers.
  - [`View`](file:///Users/kumaresan/IdeaProjects/enhanced-wordle/src/main/java/com/wordle/view/View.java): Master reactive UI component managing the top HUD bar, grid layout, virtual keyboard, tile animations, particle confetti, and end-game modal.
  - [`FXComponent`](file:///Users/kumaresan/IdeaProjects/enhanced-wordle/src/main/java/com/wordle/view/FXComponent.java): Functional interface defining renderable JavaFX UI nodes.

---

## Project Structure

```text
enhanced-wordle/
|-- pom.xml                                  # Maven dependencies & build configuration
|-- src/
|   |-- main/
|   |   |-- java/com/wordle/
|   |   |   |-- Main.java                    # Application launch entry point
|   |   |   |-- controller/
|   |   |   |   |-- Controller.java          # Controller contract
|   |   |   |   `-- ControllerImpl.java      # Async networking, caching & input handling
|   |   |   |-- model/
|   |   |   |   |-- Model.java               # Core model interface (game & economy state)
|   |   |   |   |-- ModelImpl.java           # Game logic, scoring, stars & persistence
|   |   |   |   |-- Observer.java            # Observer notification interface
|   |   |   |   `-- Subject.java             # Subject interface for event dispatch
|   |   |   `-- view/
|   |   |       |-- AppLauncher.java         # JavaFX application stage setup
|   |   |       |-- FXComponent.java         # Functional UI component interface
|   |   |       `-- View.java                # Master view, animations & modal
|   |   `-- resources/
|   |       `-- style/
|   |           `-- wordle.css               # Base CSS stylesheet
```

---

## Getting Started

### Prerequisites

- **Java Development Kit (JDK)**: Version 21 or higher (JDK 23 recommended).
- **Apache Maven**: Version 3.8+ installed and available on `PATH`.
- **Internet Connection**: Required for initial Datamuse API word retrieval and definition queries.

### Build and Run Directly

Compile and execute the application using the JavaFX Maven plugin:

```bash
mvn clean javafx:run
```

### Packaging an Executable JAR

Build a standalone executable JAR bundling all required dependencies:

```bash
mvn clean package
```

Run the compiled JAR:

```bash
java -jar target/enhanced.wordle-1.0-SNAPSHOT.jar
```

---

## How to Play

### Rules & Tile Feedback

The objective is to guess a hidden 5-letter English word within a limited number of attempts:
- Each guess must be a valid 5-letter word recognized by the dictionary.
- After submitting a guess, tiles provide immediate visual feedback:
  - 🟩 **Green**: Letter is correct and placed in the exact position.
  - 🟨 **Yellow**: Letter exists in the target word, but is in a different position.
  - ⬜ **Gray**: Letter is not in the target word.

### Controls

| Action | Physical Keyboard | On-Screen Keyboard |
| :--- | :---: | :---: |
| Enter Letter | `A`–`Z` | Click letter button |
| Delete Letter | `Backspace` | Click `⟵` key |
| Submit Guess | `Enter` | Click `ENTER` key |
| Request Hint | — | Click `REVEAL HINT` |
| Change Difficulty | — | Click `EASY`, `MEDIUM`, or `HARD` |

### Difficulty Tiers

| Setting | Max Attempts | Vocabulary Band | On-Screen Keyboard | Hints Available | Score Multiplier |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Easy** | 7 attempts | Common words (Top 20% frequency) | Enabled | Enabled | **1.0x** |
| **Medium** | 6 attempts | Moderate words (15%–35% frequency) | Enabled | Enabled | **1.2x** |
| **Hard** | 5 attempts | Rare / Obscure (Bottom 20% frequency) | **Disabled** (Physical typing only) | **Disabled** | **1.5x** |

---

## Scoring & Rating System Reference

### Points Formula

$$\text{Round Score} = \Big(\text{Base Win (500)} + (\text{Remaining Attempts} \times 100) + \text{No-Hint Bonus (100)}\Big) \times \text{Difficulty Multiplier}$$

- **Defeat Consolation**: $\text{Correctly Placed Green Letters} \times 25\text{ pts}$.

### Star Rating Criteria

- **5 Stars (★★★★★)**: Solved in 3 or fewer attempts without hints.
- **4 Stars (★★★★☆)**: Solved in 4 attempts.
- **3 Stars (★★★☆☆)**: Solved in 5 attempts (or clearing Hard Mode).
- **2 Stars (★★☆☆☆)**: Solved in 6 attempts (Medium / Easy).
- **1 Star (★☆☆☆☆)**: Solved on the final attempt (Easy).
- *Note: Using a hint deducts 1 star (minimum 1 star guaranteed on any victory).*
