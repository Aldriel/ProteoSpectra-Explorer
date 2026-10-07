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

package com.bioinfo.proteomics.desktop.tools;

import com.bioinfo.proteomics.desktop.DesktopApp;
import com.bioinfo.proteomics.desktop.controller.MainController;
import com.bioinfo.proteomics.desktop.help.HelpWindow;
import com.bioinfo.proteomics.desktop.model.DesktopModel;
import com.bioinfo.proteomics.desktop.model.UiSettings;
import com.bioinfo.proteomics.desktop.prefs.UserPreferences;
import com.bioinfo.proteomics.parser.ParseMode;
import com.bioinfo.proteomics.service.ProteomicsService;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

/**
 * Regenerates the annotated screenshots of the user guide, in English and French, by driving the
 * real view. Images are written to {@code help/images/<lang>} in the resources of the desktop
 * module, where the in-app guide and the Markdown guides find them. Run with {@code mvn -pl desktop
 * javafx:run@screenshots}; the UniProt, PRIDE and USI steps need network access.
 */
public final class ScreenshotGenerator extends Application {

    private static final double SCALE = 1.5;
    private static final Color CALLOUT = new Color(0xE5, 0x39, 0x35);
    private static final String USI =
            "mzspec:PXD000561:Adult_Frontalcortex_bRP_Elite_85_f09:scan:17555:VLHPLEGAVVIIFK/2";
    private static final List<Locale> LOCALES = List.of(Locale.FRENCH, Locale.ENGLISH);

    /**
     * A numbered box drawn around the union of the nodes matching the selectors ({@code #id} or
     * {@code .class}).
     */
    private record Callout(int number, String... selectors) {}

    private record Step(
            String file, int tab, boolean helpWindow, Runnable action, List<Callout> callouts) {}

    private Stage stage;
    private Scene scene;
    private DesktopModel model;
    private UiSettings settings;
    private Preferences preferencesNode;
    private Path imagesRoot;
    private Path samplesDir;
    private Path outputDir;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        System.setProperty("java.awt.headless", "true");
        this.stage = stage;
        Path cwd = Path.of("").toAbsolutePath();
        Path projectRoot = Files.isDirectory(cwd.resolve("desktop")) ? cwd : cwd.getParent();
        imagesRoot =
                projectRoot
                        .resolve("desktop/src/main/resources")
                        .resolve("com/bioinfo/proteomics/desktop/help/images");
        samplesDir = projectRoot.resolve("samples");
        preferencesNode = Preferences.userRoot().node("com/bioinfo/proteomics/desktop/screenshots");
        runLocale(0);
    }

    private void runLocale(int index) throws IOException {
        if (index >= LOCALES.size()) {
            try {
                preferencesNode.removeNode();
            } catch (BackingStoreException e) {
                System.err.println("Could not remove temporary preferences: " + e);
            }
            System.out.println("Screenshots written to " + imagesRoot);
            Platform.exit();
            return;
        }
        Locale locale = LOCALES.get(index);
        outputDir = imagesRoot.resolve(locale.getLanguage());
        Files.createDirectories(outputDir);

        // Synchronous executors: every step finishes before its screenshot is taken.
        settings = new UiSettings(new UserPreferences(preferencesNode));
        settings.setLocale(locale);
        settings.resetFontSize();
        model = new DesktopModel(ProteomicsService.createDefault(), Runnable::run, Runnable::run);
        scene = DesktopApp.createScene(model, settings, url -> {}, 1280, 860);
        stage.setTitle(DesktopApp.TITLE);
        stage.setScene(scene);
        stage.show();
        System.out.println(locale.getDisplayLanguage(Locale.ENGLISH) + ":");
        run(
                steps(locale),
                0,
                () -> {
                    try {
                        runLocale(index + 1);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private List<Step> steps(Locale locale) {
        boolean french = locale.getLanguage().equals("fr");
        return List.of(
                new Step(
                        "00-overview.png",
                        0,
                        false,
                        () -> {
                            text("sequenceField", "PEPTIDE");
                            fire("calculateButton");
                        },
                        List.of(
                                new Callout(1, "#menuBar"),
                                new Callout(2, ".tab-header-area"),
                                new Callout(3, ".help-button"),
                                new Callout(4, ".status-bar"))),
                new Step(
                        "01-peptide.png",
                        0,
                        false,
                        () -> {
                            text("sequenceField", "PEPTIDE");
                            fire("calculateButton");
                        },
                        List.of(
                                new Callout(1, "#sequenceField"),
                                new Callout(
                                        2,
                                        "#maxChargeSpinner",
                                        "#peptideMassTypeBox",
                                        "#aIonsBox",
                                        "#bIonsBox",
                                        "#yIonsBox"),
                                new Callout(3, "#calculateButton"),
                                new Callout(4, "#peptideSummaryLabel"),
                                new Callout(5, "#chargeTable"),
                                new Callout(6, "#fragmentTable"))),
                new Step(
                        "02-peptide-modifications.png",
                        0,
                        false,
                        () -> {
                            text("sequenceField", "[Acetyl]-PEPMTIDEK");
                            check("oxidationBox", true);
                            check("phosphoBox", true);
                            check("aIonsBox", true);
                            fire("calculateButton");
                        },
                        List.of(
                                new Callout(1, "#sequenceField"),
                                new Callout(
                                        2,
                                        "#carbamidomethylBox",
                                        "#oxidationBox",
                                        "#phosphoBox",
                                        "#deamidationBox"),
                                new Callout(3, "#aIonsBox"),
                                new Callout(4, "#peptideSummaryLabel"),
                                new Callout(5, "#fragmentTable"))),
                new Step(
                        "02b-peptide-decoy.png",
                        0,
                        false,
                        () -> {
                            check("oxidationBox", false);
                            check("phosphoBox", false);
                            check("aIonsBox", false);
                            text("sequenceField", "PEPTIDEK");
                            fire("reverseButton");
                        },
                        List.of(
                                new Callout(1, "#reverseButton", "#shuffleButton"),
                                new Callout(2, "#sequenceField"),
                                new Callout(3, "#peptideSummaryLabel"),
                                new Callout(4, "#statusLabel"))),
                new Step(
                        "03-peptide-error.png",
                        0,
                        false,
                        () -> {
                            text("sequenceField", "PEPXIDE");
                            fire("calculateButton");
                        },
                        List.of(new Callout(1, "#sequenceField"), new Callout(2, "#statusLabel"))),
                new Step(
                        "04-spectra-loading.png",
                        1,
                        false,
                        () -> {
                            model.loadSpectra(
                                    samplesDir.resolve("02-hemoglobin-alpha-tryptic.json"),
                                    ParseMode.LENIENT);
                            this.<ListView<?>>node("spectrumList").getSelectionModel().select(1);
                        },
                        List.of(
                                new Callout(1, "#openButton"),
                                new Callout(2, "#parseModeBox"),
                                new Callout(3, "#spectrumList"),
                                new Callout(4, "#spectrumDetailsLabel"),
                                new Callout(5, "#spectrumChart"))),
                new Step(
                        "05-spectra-annotation.png",
                        1,
                        false,
                        () -> fire("annotateButton"),
                        List.of(
                                new Callout(1, "#annotationPeptideField"),
                                new Callout(2, "#toleranceField", "#toleranceUnitBox"),
                                new Callout(3, "#annotateButton"),
                                new Callout(4, "#spectrumChart"),
                                new Callout(5, "#annotationTable"),
                                new Callout(6, "#statusLabel"))),
                new Step(
                        "05b-spectra-comparison.png",
                        1,
                        false,
                        () -> fire("theoreticalButton"),
                        List.of(
                                new Callout(1, "#openReferenceButton", "#theoreticalButton"),
                                new Callout(2, "#referenceBox", "#comparisonModeBox"),
                                new Callout(3, "#similarityLabel"),
                                new Callout(4, "#spectrumChart"))),
                new Step(
                        "06-spectra-issues.png",
                        1,
                        false,
                        () -> {
                            fire("clearReferenceButton");
                            model.loadSpectra(
                                    samplesDir.resolve("07-parsing-errors.json"),
                                    ParseMode.LENIENT);
                        },
                        List.of(
                                new Callout(1, "#parseModeBox"),
                                new Callout(2, "#spectrumList"),
                                new Callout(3, "#issuesPane"),
                                new Callout(4, "#statusLabel"))),
                new Step(
                        "07-spectrum-pride-usi.png",
                        1,
                        false,
                        () -> {
                            text("usiField", USI);
                            fire("downloadButton");
                            fire("annotateButton");
                        },
                        List.of(
                                new Callout(1, "#usiField"),
                                new Callout(2, "#downloadButton"),
                                new Callout(3, "#spectrumDetailsLabel"),
                                new Callout(4, "#spectrumChart"),
                                new Callout(5, "#annotationTable"))),
                new Step(
                        "08-uniprot.png",
                        2,
                        false,
                        () -> fire("uniprotButton"),
                        List.of(
                                new Callout(1, "#accessionField"),
                                new Callout(
                                        2,
                                        "#proteaseBox",
                                        "#missedCleavageSpinner",
                                        "#digestMassTypeBox"),
                                new Callout(3, "#uniprotButton"),
                                new Callout(4, "#proteinSummaryLabel"),
                                new Callout(5, "#digestTable"))),
                new Step(
                        "09-pride.png",
                        3,
                        false,
                        () -> {
                            text("queryField", "PXD000001");
                            model.searchPrideProjects("PXD000001", 0);
                            this.<TableView<?>>node("projectTable").getSelectionModel().select(0);
                            this.<TableView<?>>node("fileTable").getSelectionModel().select(1);
                        },
                        List.of(
                                new Callout(1, "#queryField", "#prideSearchButton"),
                                new Callout(2, "#projectTable"),
                                new Callout(3, "#projectSheet"),
                                new Callout(4, "#fileTable"),
                                new Callout(5, "#prideDownloadButton"))),
                new Step(
                        "10-help-window.png",
                        0,
                        true,
                        () -> {
                            HelpWindow help = controller().helpWindow();
                            help.show("spectra");
                            help.search(french ? "tolérance" : "tolerance");
                        },
                        List.of(
                                new Callout(1, "#helpSearchField"),
                                new Callout(2, "#helpSectionList"),
                                new Callout(3, ".help-highlight"))));
    }

    private void run(List<Step> steps, int index, Runnable done) {
        if (index >= steps.size()) {
            controller().helpWindow().stage().hide();
            done.run();
            return;
        }
        Step step = steps.get(index);
        this.<TabPane>node("mainTabs").getSelectionModel().select(step.tab());
        try {
            step.action().run();
        } catch (RuntimeException e) {
            System.err.println(step.file() + ": " + e);
        }
        PauseTransition pause =
                new PauseTransition(Duration.millis(step.helpWindow() ? 1500 : 800));
        pause.setOnFinished(
                e -> {
                    try {
                        capture(
                                step,
                                step.helpWindow()
                                        ? controller().helpWindow().stage().getScene()
                                        : scene);
                    } catch (IOException ex) {
                        System.err.println(step.file() + ": " + ex);
                    }
                    run(steps, index + 1, done);
                });
        pause.play();
    }

    private void capture(Step step, Scene target) throws IOException {
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(Transform.scale(SCALE, SCALE));
        WritableImage image = target.getRoot().snapshot(params, null);
        BufferedImage buffered = toBufferedImage(image);

        Graphics2D g = buffered.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (13 * SCALE)));
        for (Callout callout : step.callouts()) {
            Bounds bounds = unionBounds(target, callout.selectors());
            if (bounds != null) {
                drawCallout(g, callout.number(), bounds, buffered.getWidth(), buffered.getHeight());
            } else {
                System.err.println(
                        "  " + step.file() + ": callout " + callout.number() + " not found");
            }
        }
        g.dispose();

        Path file = outputDir.resolve(step.file());
        ImageIO.write(buffered, "png", file.toFile());
        System.out.println(
                "  "
                        + file.getFileName()
                        + "  —  "
                        + model.statusProperty().get().render(settings.i18n().messages()));
    }

    /**
     * Returns the bounds enclosing the first visible node matching each selector, or {@code null}
     * if none.
     */
    private static Bounds unionBounds(Scene target, String... selectors) {
        List<Bounds> found = new ArrayList<>();
        for (String selector : selectors) {
            target.getRoot().lookupAll(selector).stream()
                    .filter(ScreenshotGenerator::treeVisible)
                    .map(n -> n.localToScene(n.getBoundsInLocal()))
                    .filter(b -> b.getWidth() > 0 && b.getHeight() > 0)
                    .findFirst()
                    .ifPresent(found::add);
        }
        if (found.isEmpty()) {
            return null;
        }
        double minX = found.stream().mapToDouble(Bounds::getMinX).min().orElseThrow();
        double minY = found.stream().mapToDouble(Bounds::getMinY).min().orElseThrow();
        double maxX = found.stream().mapToDouble(Bounds::getMaxX).max().orElseThrow();
        double maxY = found.stream().mapToDouble(Bounds::getMaxY).max().orElseThrow();
        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    private static boolean treeVisible(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }

    private static void drawCallout(Graphics2D g, int number, Bounds b, int width, int height) {
        int pad = (int) (3 * SCALE);
        int x = (int) Math.max(1, b.getMinX() * SCALE - pad);
        int y = (int) Math.max(1, b.getMinY() * SCALE - pad);
        int w = (int) Math.min(width - x - 2, b.getWidth() * SCALE + 2 * pad);
        int h = (int) Math.min(height - y - 2, b.getHeight() * SCALE + 2 * pad);
        g.setColor(CALLOUT);
        g.setStroke(new BasicStroke((float) (2.5 * SCALE)));
        g.drawRoundRect(x, y, w, h, 10, 10);

        int d = (int) (22 * SCALE);
        int cx = Math.max(1, x - d / 2);
        int cy = Math.max(1, y - d / 2);
        g.fillOval(cx, cy, d, d);
        g.setColor(Color.WHITE);
        String label = String.valueOf(number);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(
                label,
                cx + (d - fm.stringWidth(label)) / 2,
                cy + (d + fm.getAscent() - fm.getDescent()) / 2);
    }

    private static BufferedImage toBufferedImage(WritableImage image) {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader()
                .getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, w, h, pixels, 0, w);
        return buffered;
    }

    private MainController controller() {
        return (MainController) scene.getUserData();
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T node(String id) {
        Node n = scene.lookup("#" + id);
        if (n == null) {
            throw new IllegalStateException("Node #" + id + " not found");
        }
        return (T) n;
    }

    private void text(String id, String value) {
        this.<TextField>node(id).setText(value);
    }

    private void fire(String id) {
        this.<ButtonBase>node(id).fire();
    }

    private void check(String id, boolean selected) {
        this.<CheckBox>node(id).setSelected(selected);
    }
}
