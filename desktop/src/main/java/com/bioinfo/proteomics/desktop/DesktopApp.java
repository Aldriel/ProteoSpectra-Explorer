/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bioinfo.proteomics.desktop;

import com.bioinfo.proteomics.desktop.controller.ControllerFactory;
import com.bioinfo.proteomics.desktop.controller.MainController;
import com.bioinfo.proteomics.desktop.model.DesktopModel;
import com.bioinfo.proteomics.desktop.model.UiSettings;
import com.bioinfo.proteomics.desktop.prefs.UserPreferences;
import com.bioinfo.proteomics.service.ProteomicsService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** JavaFX application: wires Model (service + observable state), View (FXML) and Controller. */
public final class DesktopApp extends Application {

    public static final String TITLE = AppInfo.NAME;
    public static final String STYLESHEET = resource("view/styles.css");
    private static final List<Integer> ICON_SIZES = List.of(16, 32, 48, 64, 256);

    private ExecutorService background;
    private UiSettings settings;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        background = Executors.newVirtualThreadPerTaskExecutor();
        settings = new UiSettings(UserPreferences.load());
        UserPreferences prefs = settings.preferences();
        DesktopModel model =
                new DesktopModel(ProteomicsService.createDefault(), background, Platform::runLater);

        stage.setTitle(TITLE);
        ICON_SIZES.forEach(
                size -> stage.getIcons().add(new Image(resource("view/icon-" + size + ".png"))));
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(
                createScene(
                        model,
                        settings,
                        getHostServices()::showDocument,
                        prefs.windowWidth(),
                        prefs.windowHeight()));
        stage.setMaximized(prefs.windowMaximized());
        stage.show();
    }

    /**
     * Loads the FXML views and binds them to {@code model} through their controllers.
     *
     * @param model application state
     * @param settings language and text size
     * @param openUrl opens web pages, folders and e-mail links with the default application
     * @param width initial width of the scene
     * @param height initial height of the scene
     * @return the scene of the main window; its user data is the {@link MainController}
     */
    public static Scene createScene(
            DesktopModel model,
            UiSettings settings,
            Consumer<String> openUrl,
            double width,
            double height) {
        FXMLLoader loader = new FXMLLoader(DesktopApp.class.getResource("view/main-view.fxml"));
        loader.setControllerFactory(new ControllerFactory(model, settings, openUrl, STYLESHEET));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(STYLESHEET);
        scene.setUserData(loader.getController());
        return scene;
    }

    @Override
    public void stop() {
        if (stage != null && stage.getScene() != null && settings != null) {
            settings.preferences()
                    .setWindowState(
                            stage.getScene().getWidth(),
                            stage.getScene().getHeight(),
                            stage.isMaximized());
            settings.preferences().flush();
        }
        if (background != null) {
            background.shutdownNow();
        }
    }

    private static String resource(String name) {
        URL url = Objects.requireNonNull(DesktopApp.class.getResource(name), name);
        return url.toExternalForm();
    }
}
