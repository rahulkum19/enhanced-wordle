package com.wordle.view;

import com.wordle.controller.Controller;
import com.wordle.controller.ControllerImpl;
import com.wordle.model.Model;
import com.wordle.model.ModelImpl;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AppLauncher extends Application {
    @Override
    public void start(Stage stage) {

        Model model = new ModelImpl("");
        Controller pc = new ControllerImpl(model);
        View view = new View(pc, model, stage);
        model.addObserver(view);

        pc.setMediumMode();
        pc.initializeWords();

        Scene scene = new Scene(view.render(), 700, 900);
        scene.setOnKeyPressed(event -> {
            String code = event.getCode().toString();
            pc.processKeyPress(code);
        });
        stage.setScene(scene);
        stage.setTitle("Wordle");
        stage.show();


    }
}
