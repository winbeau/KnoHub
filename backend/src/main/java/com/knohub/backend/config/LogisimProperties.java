package com.knohub.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for invoking Logisim CLI to render previews.
 * The command is intentionally configurable because different Logisim builds expose different flags.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "logisim")
public class LogisimProperties {

    /**
     * Toggle preview generation.
     */
    private boolean enabled = true;

    /**
     * Absolute or relative path to the Logisim jar.
     */
    private String jarPath;

    /**
     * Command template used to render an image.
     * Supported placeholders:
     *  - {{jar}}     -> resolved jarPath
     *  - {{input}}   -> path to the uploaded .circ file
     *  - {{output}}  -> target image path
     *
     * Example (Logisim-evolution image export):
     * java -jar {{jar}} -tty image {{input}} {{output}} 2
     *
     * When empty, the service will fall back to a basic
     * "java -jar <jarPath> -export <output> <input>" command.
     */
    private String commandTemplate;

    /**
     * Render timeout in seconds to avoid hanging uploads.
     */
    private long timeoutSeconds = 20;

    /**
     * Preview image suffix (png by default).
     */
    private String outputFormat = "png";
}
