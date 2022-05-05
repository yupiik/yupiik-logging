/*
 * Copyright (c) 2021-2022 - Yupiik SAS - https://www.yupiik.com
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JULTest {
    private static GenericContainer<?> container;

    @BeforeAll
    static void start() {
        container = new GenericContainer<>("quay.io/quarkus/centos-quarkus-maven:20.3.0-java11");
        container.setWorkingDirectory("/opt/projects");
        container.withFileSystemBind(System.getProperty("maven.test.repository"), "/home/quarkus/.m2/repository");
        container.withFileSystemBind(System.getProperty("maven.test.projects", "projects"), "/opt/projects");
        container.setCommand("sleep", "infinity");
        container.start();
    }

    @AfterAll
    static void stop() {
        container.stop();
    }

    @Test
    void jul() throws IOException, InterruptedException {
        final var firstOutput = buildProject("jul", "-DignoreJulConfigForThisTest");
        // default setup, ie inline formatter at info level
        final var defaultOutput = firstOutput
                // no, we didn't log an EOL but due to testcontainers bug and out #testContainerStdWorkaround we get one
                .replace("\n[INFO]", "[INFO]")
                .trim();

        //
        // already built so execute it another time with different config
        //
        final String projectBase = "/tmp/jul/";

        // change level to FINEST for test logger
        final var finestOutput = ensureSuccessAndGetStdout(container.execInContainer(
                projectBase + "target/jul.graal.bin",
                "-Djava.util.logging.config.file=" + projectBase + "logging.properties",
                getVersion()))
                .trim();

        // use json formatting
        final var jsonOutput = ensureSuccessAndGetStdout(container.execInContainer(
                projectBase + "target/jul.graal.bin",
                "-Djava.util.logging.config.file=" + projectBase + "logging.json.properties",
                getVersion()))
                .trim();

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

        final String dateRegex = "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.?[0-9]+)?Z ";
        assertTrue(defaultOutput.matches(
                dateRegex + "\\[INFO]\\[test\\.Main] info entry point of the test"),
                defaultOutput);
        assertTrue(finestOutput.matches("" +
                dateRegex + "\\[FINEST]\\[test\\.Main] severe entry point of the test" + /*\n was replaced by our std workaround */
                dateRegex + "\n\\[INFO]\\[test\\.Main] info entry point of the test"), finestOutput);
        assertTrue(jsonOutput.matches("" +
                "\\{\"timestamp\":\"" + dateRegex.trim() + "\",\"level\":\"INFO\",\"logger\":\"test\\.Main\"," +
                "\"method\":\"log\",\"message\":\"info entry point of the test\"," +
                "\"class\":\"io\\.yupiik\\.logging\\.jul\\.logger\\.YupiikLogger\"}"), jsonOutput);
    }

    private String buildProject(final String name, final String runtimeSystemProp) throws IOException, InterruptedException {
        final var result = container.execInContainer(
                "sh", "./execute.sh", name, runtimeSystemProp, getVersion());
        return ensureSuccessAndGetStdout(result);
    }

    private String getVersion() {
        return System.getProperty("project.version", "1.0-SNAPSHOT");
    }

    private String ensureSuccessAndGetStdout(final Container.ExecResult result) {
        assertEquals(0, result.getExitCode(), () -> "" +
                "status=" + result.getExitCode() + "\n" +
                "stdout=\n" + testContainerStdWorkaround(result.getStdout()) + "\n" +
                "stderr=\n" + testContainerStdWorkaround(result.getStderr()));
        return testContainerStdWorkaround(result.getStdout());
    }

    // bug of testcontainers with maven logger output
    private String testContainerStdWorkaround(final String result) {
        return result
                .replace("\n", "")
                // try to make it more readable with previous hack
                .replace("Downloading from ", "\nDownloading from ")
                .replace("Downloaded from ", "\nDownloaded from ")
                .replace("[INFO]", "\n[INFO]")
                .replace("[ERROR]", "\n[ERROR]")
                .replace("\tat ", "\n\tat "); // exceptions
    }
}
