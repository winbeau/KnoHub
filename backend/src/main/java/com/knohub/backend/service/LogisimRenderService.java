package com.knohub.backend.service;

import com.knohub.backend.config.LogisimProperties;
import com.knohub.backend.logisim.HeadlessLogisimRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogisimRenderService {

    private final LogisimProperties properties;
    private final HeadlessLogisimRenderer renderer;

    /**
     * Render a .circ file to an image by invoking Logisim jar.
     *
     * @param inputPath  path to .circ
     * @param outputPath expected output path (png/svg)
     * @return output path if rendered successfully
     */
    public Optional<Path> renderPreview(Path inputPath, Path outputPath) {
        if (!properties.isEnabled()) {
            log.debug("Logisim rendering skipped because it is disabled.");
            return Optional.empty();
        }

        if (inputPath == null || outputPath == null) {
            return Optional.empty();
        }

        try {
            renderer.renderToPng(inputPath, outputPath);
            return Optional.of(outputPath);
        } catch (Exception e) {
            log.warn("Headless Logisim render failed for {}: {}", inputPath, e.getMessage());
            return Optional.empty();
        }
    }
}
