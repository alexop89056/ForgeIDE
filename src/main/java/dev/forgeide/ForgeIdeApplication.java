package dev.forgeide;

import dev.forgeide.ui.ForgeIdeWindow;
import javafx.application.Application;
import javafx.stage.Stage;

public final class ForgeIdeApplication extends Application {
    @Override
    public void start(Stage stage) {
        new ForgeIdeWindow(stage).show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
