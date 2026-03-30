package io.github.randomcodespace.iq;

import io.github.randomcodespace.iq.cli.CodeIqCli;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

import java.util.Arrays;

/**
 * Main application entry point for OSSCodeIQ.
 * <p>
 * Uses Picocli with Spring Boot integration for CLI command routing.
 * Profile selection:
 * <ul>
 *   <li>{@code serve} command → "serving" profile (web server enabled)</li>
 *   <li>All other commands → "indexing" profile (no web server)</li>
 * </ul>
 */
@SpringBootApplication
@EnableCaching
public class CodeIqApplication implements CommandLineRunner, ExitCodeGenerator {

    private final CodeIqCli codeIqCli;
    private final IFactory factory;
    private int exitCode;

    public CodeIqApplication(CodeIqCli codeIqCli, IFactory factory) {
        this.codeIqCli = codeIqCli;
        this.factory = factory;
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(codeIqCli, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    public static void main(String[] args) {
        var app = new SpringApplication(CodeIqApplication.class);
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);

        // Detect command from first non-flag argument only
        String command = Arrays.stream(args)
                .filter(arg -> !arg.startsWith("-"))
                .findFirst()
                .orElse("");
        boolean isServe = "serve".equalsIgnoreCase(command);
        boolean isIndex = "index".equalsIgnoreCase(command);
        boolean isEnrich = "enrich".equalsIgnoreCase(command);

        if (isServe) {
            app.setAdditionalProfiles("serving");

            // Extract --port flag for server port
            String portStr = extractFlag(args, "--port");
            if (portStr == null) portStr = extractFlag(args, "-p");
            if (portStr != null) {
                System.setProperty("server.port", portStr);
            }
        } else if (isIndex) {
            app.setAdditionalProfiles("indexing");
            // Index command: no web server, no Neo4j
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        } else if (isEnrich) {
            // Enrich command: no web server, Neo4j started programmatically
            app.setAdditionalProfiles("indexing");
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        } else {
            app.setAdditionalProfiles("indexing");
            // Disable web server for non-serve commands
            app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        }

        System.exit(SpringApplication.exit(app.run(args)));
    }

    /**
     * Extract the value of a named flag from the args array.
     * Supports both "--flag value" and "--flag=value" forms.
     */
    private static String extractFlag(String[] args, String flagName) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(flagName) && i + 1 < args.length) {
                return args[i + 1];
            }
            if (args[i].startsWith(flagName + "=")) {
                return args[i].substring(flagName.length() + 1);
            }
        }
        return null;
    }

    /**
     * Extract the first positional argument after the command name.
     * Skips flags (--name value pairs) to find positional args.
     */
    private static String extractPositionalArg(String[] args, String command) {
        boolean foundCommand = false;
        boolean skipNext = false;
        for (String arg : args) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (!foundCommand && arg.equalsIgnoreCase(command)) {
                foundCommand = true;
                continue;
            }
            if (foundCommand) {
                // Skip --flag value pairs
                if (arg.startsWith("--") && !arg.contains("=")) {
                    skipNext = true;
                    continue;
                }
                if (arg.startsWith("-") && arg.length() == 2) {
                    skipNext = true; // short flag like -p 8080
                    continue;
                }
                if (arg.startsWith("-")) {
                    continue; // --flag=value or -flag
                }
                return arg;
            }
        }
        return null;
    }
}
