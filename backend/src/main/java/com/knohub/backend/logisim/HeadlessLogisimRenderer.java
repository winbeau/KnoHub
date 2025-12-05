package com.knohub.backend.logisim;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.knohub.backend.logisim.HeadlessProject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/**
 * Render Logisim circuits to images in headless mode using the Logisim jar classes directly.
 */
@Component
@Slf4j
public class HeadlessLogisimRenderer {

    /**
     * Render the main circuit in the given .circ file to a PNG.
     *
     * @param inputPath  path to the .circ file
     * @param outputPath target PNG path
     */
    public void renderToPng(Path inputPath, Path outputPath) throws IOException {
        System.setProperty("java.awt.headless", "true");
        // Ensure Logisim preference/board directories point to a writable location inside workspace
        String workspaceHome = Path.of("").toAbsolutePath().toString();
        System.setProperty("user.home", workspaceHome);
        System.setProperty("java.util.prefs.userRoot", Path.of(workspaceHome, ".java", "prefs").toString());
        Path boardDir = Path.of(workspaceHome, ".logisim-evolution", "boards");
        Files.createDirectories(boardDir);
        Files.createDirectories(Path.of(workspaceHome, ".java", "prefs"));
        Preferences.userRoot().node("logisim-evolution").put("BoardDir", boardDir.toString());

        Loader loader = new Loader(null);
        LogisimFile logisimFile = openFile(loader, inputPath);
        Circuit circuit = logisimFile.getMainCircuit();
        if (circuit == null) {
            throw new IOException("未找到主电路");
        }

        HeadlessProject project = new HeadlessProject(logisimFile);
        project.setCurrentCircuit(circuit);

        // Compute bounds and prepare canvas
        Bounds bounds = circuit.getBounds();
        int padding = 40;
        int width = Math.max(10, bounds.getWidth() + padding * 2);
        int height = Math.max(10, bounds.getHeight() + padding * 2);

        Canvas canvas = new Canvas(project);
        project.attachSelection(canvas);
        canvas.setSize(width, height);
        canvas.doLayout();

        // Draw to image
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            // Align circuit to visible area
            g.translate(padding - bounds.getX(), padding - bounds.getY());
            canvas.printAll(g);
        } finally {
            g.dispose();
        }

        Files.createDirectories(outputPath.getParent());
        ImageIO.write(image, "png", outputPath.toFile());
        log.info("Logisim preview rendered to {}", outputPath);
    }

    private LogisimFile openFile(Loader loader, Path inputPath) throws IOException {
        File file = inputPath.toFile();
        try {
            return loader.openLogisimFile(file);
        } catch (LoadFailedException e) {
            throw new IOException("Logisim 文件解析失败: " + e.getMessage(), e);
        }
    }
}
