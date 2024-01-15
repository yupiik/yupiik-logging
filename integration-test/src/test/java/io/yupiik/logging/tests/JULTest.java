/*
 * Copyright (c) 2021-2023 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.logging.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class JULTest {
    private final String maven = findMaven();

    @Test
    void jul(@TempDir final Path work) throws IOException {
        final var firstRun = buildAndRun(work, "jul", List.of("clean", "package", "-e"), "-DuseDefaultJulConfigForThisTest");
        final var firstOutput = assertExitCode(firstRun);
        // default setup, ie inline formatter at info level
        final var defaultOutput = firstOutput
                // no, we didn't log an EOL but due to testcontainers bug and out #testContainerStdWorkaround we get one
                .replace("\n[INFO]", "[INFO]")
                .trim();

        //
        // already built so execute it another time with different config
        //
        final var base = work.resolve("project");
        final var bin = base.resolve("target/jul.graal.bin").toString();

        // change level to FINEST for test logger
        final var finestOutput = assertExitCode(exec(
                new ProcessBuilder(bin, "-Djava.util.logging.config.file=" + base.resolve("logging.properties")), base))
                .strip();

        // use json formatting
        final var jsonOutput = assertExitCode(exec(
                new ProcessBuilder(bin, "-Djava.util.logging.config.file=" + base.resolve("logging.json.properties")), base))
                .strip();

        // since it is a bit long to native-image the main we run all tests then assert to be able to debug more easily
        // all cases at once
        if (Boolean.getBoolean("debug")) {
            System.out.println("--");
            System.out.println(defaultOutput);
            System.out.println("--");
            System.out.println(finestOutput);
            System.out.println("--");
            System.out.println(jsonOutput);
            System.out.println("--");
        }

        final var dateRegex = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.?[0-9]+)?Z ";
        assertTrue(defaultOutput.matches(
                        dateRegex + "\\[INFO]\\[test\\.Main] info entry point of the test"),
                defaultOutput);
        assertTrue(finestOutput.matches("" +
                dateRegex + "\\[FINEST]\\[test\\.Main] severe entry point of the test\n" +
                dateRegex + "\\[INFO]\\[test\\.Main] info entry point of the test"), finestOutput);
        assertTrue(jsonOutput.matches("" +
                "\\{\"timestamp\":\"" + dateRegex.trim() + "\",\"level\":\"INFO\",\"logger\":\"test\\.Main\"," +
                "\"method\":\"log\",\"message\":\"info entry point of the test\"," +
                "\"class\":\"io\\.yupiik\\.logging\\.jul\\.logger\\.YupiikLogger\"}"), jsonOutput);
    }

    private String getVersion() {
        return System.getProperty("project.version", "1.0.8-SNAPSHOT");
    }

    private String assertExitCode(final Process result) throws IOException {
        assertEquals(0, result.exitValue(), () -> {
            try {
                return "" +
                        "status=" + result.exitValue() + "\n" +
                        "stdout=\n" + new String(result.getInputStream().readAllBytes(), UTF_8) + "\n" +
                        "stderr=\n" + new String(result.getErrorStream().readAllBytes(), UTF_8);
            } catch (final IOException e) {
                return "status=" + result.exitValue();
            }
        });
        return new String(result.getInputStream().readAllBytes(), UTF_8);
    }

    private String findMaven() {
        return ofNullable(System.getProperty("maven.home"))
                .flatMap(h -> {
                    final var bin = "mvn" +
                            (System.getProperty("os.name", "").toLowerCase(ROOT).contains("win") ?
                                    ".cmd" : "");
                    try (final var list = Files.list(Path.of(h).resolve("bin"))) {
                        return list.filter(it -> Objects.equals(it.getFileName().toString(), bin)).findFirst();
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .map(Path::toString)
                .orElse("mvn");
    }

    private Process buildAndRun(final Path work, final String project, final List<String> args, final String binOpt) throws IOException {
        final var target = Files.createDirectories(work.resolve("project"));
        final var from = Path.of(System.getProperty("maven.test.projects", "projects")).resolve(project);
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                final var to = target.resolve(from.relativize(file));
                Files.createDirectories(to.getParent());
                if (!"pom.xml".equals(file.getFileName().toString())) {
                    Files.copy(file, to);
                } else {
                    Files.writeString(to, Files.readString(file)
                            .replace("<version>project.version</version>", "<version>" + getVersion() + "</version>"));
                }
                return super.visitFile(file, attrs);
            }
        });

        final var mvnProcessBuilder = new ProcessBuilder(Stream.concat(Stream.of(maven), args.stream()).collect(toList()));
        mvnProcessBuilder.inheritIO();

        final var environment = mvnProcessBuilder.environment();
        environment.put("MAVEN_HOME", System.getProperty("maven.home", ""));
        environment.put("JAVA_HOME", System.getProperty("java.home", ""));

        final var mvnResult = exec(mvnProcessBuilder, target);
        assertExitCode(mvnResult);

        return exec(new ProcessBuilder("target/" + project + ".graal.bin", binOpt), target);
    }

    private Process exec(final ProcessBuilder builder, final Path target) throws IOException {
        builder.directory(target.toFile());

        try {
            final var start = builder.start();
            start.waitFor();
            return start;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(e);
        }
    }
}
