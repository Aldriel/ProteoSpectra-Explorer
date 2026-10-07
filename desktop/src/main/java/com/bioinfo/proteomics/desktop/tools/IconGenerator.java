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

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Draws the application icon (a stick mass spectrum with annotated b and y peaks) as PNG files for
 * the window and as a Windows {@code .ico} for the executable. Run with {@code mvn -pl desktop
 * javafx:run@icons}.
 */
public final class IconGenerator extends Application {

    private static final int[] SIZES = {16, 32, 48, 64, 256};

    /**
     * Relative x position, relative height and colour (0 grey, 1 blue b ion, 2 red y ion) of each
     * peak.
     */
    private static final double[][] PEAKS = {
        {0.20, 0.35, 0},
        {0.30, 0.62, 1},
        {0.40, 0.28, 0},
        {0.50, 0.85, 2},
        {0.60, 0.45, 1},
        {0.70, 0.70, 2},
        {0.80, 0.30, 0}
    };

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Path module = Files.isDirectory(Path.of("desktop")) ? Path.of("desktop") : Path.of(".");
        Path view = module.resolve("src/main/resources/com/bioinfo/proteomics/desktop/view");
        Path packaging = module.resolve("packaging");
        Files.createDirectories(packaging);

        List<byte[]> pngs = new ArrayList<>();
        for (int size : SIZES) {
            byte[] png = png(draw(size));
            Files.write(view.resolve("icon-" + size + ".png"), png);
            pngs.add(png);
        }
        Files.write(packaging.resolve("ProteoSpectraExplorer.ico"), ico(pngs));
        System.out.println(
                "Icons written to " + view.toAbsolutePath().normalize() + " and " + packaging);
        Platform.exit();
    }

    private static WritableImage draw(int size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        double arc = size * 0.22;
        g.setFill(
                new LinearGradient(
                        0,
                        0,
                        0,
                        1,
                        true,
                        CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#2b7cc4")),
                        new Stop(1, Color.web("#0d3b66"))));
        g.fillRoundRect(0, 0, size, size, arc, arc);

        double baseline = size * 0.80;
        double top = size * 0.14;
        double stroke = Math.max(1.5, size * 0.065);
        g.setStroke(Color.WHITE);
        g.setLineWidth(Math.max(1, size * 0.03));
        g.strokeLine(size * 0.12, baseline, size * 0.88, baseline);
        for (double[] peak : PEAKS) {
            double x = Math.round(size * peak[0]) + 0.5;
            double y = baseline - (baseline - top) * peak[1];
            g.setStroke(
                    switch ((int) peak[2]) {
                        case 1 -> Color.web("#9fd3ff");
                        case 2 -> Color.web("#ff8a80");
                        default -> Color.web("#ffffff", 0.85);
                    });
            g.setLineWidth(stroke);
            g.strokeLine(x, baseline, x, y);
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private static byte[] png(WritableImage image) throws IOException {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader()
                .getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, w, h, pixels, 0, w);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(buffered, "png", out);
        return out.toByteArray();
    }

    /**
     * Packs PNG images into an ICO file (PNG-compressed entries, supported since Windows Vista).
     */
    private static byte[] ico(List<byte[]> pngs) {
        int headerSize = 6 + 16 * pngs.size();
        int total = headerSize + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) pngs.size());
        int offset = headerSize;
        for (int i = 0; i < pngs.size(); i++) {
            int size = SIZES[i];
            buffer.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
            buffer.put((byte) 0).put((byte) 0);
            buffer.putShort((short) 1).putShort((short) 32);
            buffer.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        pngs.forEach(buffer::put);
        return buffer.array();
    }
}
