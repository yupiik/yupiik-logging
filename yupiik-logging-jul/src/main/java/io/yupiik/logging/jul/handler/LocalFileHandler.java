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
package io.yupiik.logging.jul.handler;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.function.Function.identity;

// from https://github.com/apache/tomee/blob/master/tomee/tomee-juli/src/main/java/org/apache/tomee/jul/handler/rotating/LocalFileHandler.java
public class LocalFileHandler extends Handler {
    private final Clock clock;

    private long limit = 0;
    private int bufferSize = -1;
    private Pattern filenameRegex;
    private Pattern archiveFilenameRegex;
    private String filenamePattern = "${application.base}/logs/logs.%s.%03d.log";
    private String archiveFormat = "gzip";
    private long dateCheckInterval;
    private long archiveExpiryDuration;
    private int maxArchives = -1;
    private int compressionLevel;
    private long purgeExpiryDuration;
    private File archiveDir;

    private volatile int currentIndex;
    private volatile long lastTimestamp;
    private volatile String date;
    private volatile PrintWriter writer;
    private volatile int written;
    private volatile File currentFile;

    private final ReadWriteLock writerLock = new ReentrantReadWriteLock();
    private final Lock backgroundTaskLock = new ReentrantLock();
    private volatile boolean closed;
    private boolean noRotation;
    private boolean overwrite;
    private boolean truncateIfExists;
    private String zeroDate;
    private Supplier<String> currentDate;

    public LocalFileHandler() {
        this(Clock.systemDefaultZone());
    }

    public LocalFileHandler(final Clock clock) {
        this.clock = clock;
        configure();
    }

    private void configure() {
        final String className = LocalFileHandler.class.getName(); //allow classes to override

        noRotation = getProperty(className + ".noRotation", Boolean::parseBoolean, () -> false);
        overwrite = getProperty(className + ".overwrite", Boolean::parseBoolean, () -> false);
        truncateIfExists = getProperty(className + ".truncateIfExists", Boolean::parseBoolean, () -> false);
        dateCheckInterval = noRotation ? -1 : getProperty(className + ".dateCheckInterval", Duration::parse, () -> Duration.ofSeconds(5)).toMillis();
        filenamePattern = replace(getProperty(className + ".filenamePattern", identity(), () -> filenamePattern));
        limit = getProperty(className + ".limit", Long::parseLong, () -> 10 * 1024 * 1024L) /*10m*/;

        final int lastSep = Math.max(filenamePattern.lastIndexOf('/'), filenamePattern.lastIndexOf('\\'));
        String fileNameReg = lastSep >= 0 ? filenamePattern.substring(lastSep + 1) : filenamePattern;
        if (fileNameReg.contains("%sHm")) {
            fileNameReg = fileNameReg.replace("%sHm", "\\d{4}\\-\\d{2}\\-\\d{2}-\\d{2}-\\d{2}");
            zeroDate = "0000-00-00-00-00";
            final var formatter= DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");
            currentDate = () -> LocalDateTime.ofInstant(clock.instant(), clock.getZone()).format(formatter);
        } else if (fileNameReg.contains("%sH")) {
            fileNameReg = fileNameReg.replace("%sH", "\\d{4}\\-\\d{2}\\-\\d{2}-\\d{2}");
            final var formatter= DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
            currentDate = () -> LocalDateTime.ofInstant(clock.instant(), clock.getZone()).format(formatter);
        } else /* if (fileNameReg.contains("%s")) */ {
            fileNameReg = fileNameReg.replace("%s", "\\d{4}\\-\\d{2}\\-\\d{2}");
            zeroDate = "0000-00-00";
            currentDate = () -> LocalDate.ofInstant(clock.instant(), clock.getZone()).toString();
        }
        {   // file rotation index
            final int indexIdxStart = fileNameReg.indexOf('%');
            if (indexIdxStart >= 0) {
                final int indexIdxEnd = fileNameReg.indexOf('d', indexIdxStart);
                if (indexIdxEnd >= 0) {
                    fileNameReg = fileNameReg.substring(0, indexIdxStart) + "\\d*" + fileNameReg.substring(indexIdxEnd + 1);
                }
            }
        }
        filenameRegex = Pattern.compile(fileNameReg);

        compressionLevel = getProperty(className + ".compressionLevel", Integer::parseInt, () -> Deflater.DEFAULT_COMPRESSION);
        archiveExpiryDuration = getProperty(className + ".archiveOlderThan", v -> Duration.parse(v).toMillis(), () -> -1L);
        archiveDir = new File(replace(getProperty(className + ".archiveDirectory", identity(), () -> "${application.base}/logs/archives/")));
        archiveFormat = replace(getProperty(className + ".archiveFormat", identity(), () -> archiveFormat));
        archiveFilenameRegex = Pattern.compile(fileNameReg + "\\." + archiveFormat);

        purgeExpiryDuration = getProperty(className + ".purgeOlderThan", v -> Duration.parse(v).toMillis(), () -> -1L);
        maxArchives = getProperty(className + ".maxArchives", Integer::parseInt, () -> -1);

        try {
            bufferSize = getProperty(className + ".bufferSize", Integer::parseInt, () -> -1);
        } catch (final NumberFormatException ignore) {
            // no-op
        }

        lastTimestamp = clock.instant().toEpochMilli();
        date = currentDate();

        // setErrorManager(new ErrorManager());
    }

    protected String currentDate() {
        return currentDate.get();
    }

    @Override
    public void publish(final LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }

        final long now = clock.instant().toEpochMilli();
        // just do it once / sec if we have a lot of log, can make some log appearing in the wrong file but better than doing it each time
        if (dateCheckInterval < 0 || now - lastTimestamp > dateCheckInterval) { // using as much as possible volatile to avoid to lock too much
            lastTimestamp = now;
        }

        try {
            writerLock.readLock().lock();
            rotateIfNeeded();

            final String result;
            try {
                result = getFormatter().format(record);
            } catch (final Exception e) {
                reportError(null, e, ErrorManager.FORMAT_FAILURE);
                return;
            }

            try {
                if (writer != null) {
                    writer.write(result);
                    if (bufferSize < 0) {
                        writer.flush();
                    }
                } else {
                    reportError(getClass().getSimpleName() + " is closed or not yet initialized, unable to log [" + result + "]", null, ErrorManager.WRITE_FAILURE);
                }
            } catch (final Exception e) {
                reportError(null, e, ErrorManager.WRITE_FAILURE);
            }
        } finally {
            writerLock.readLock().unlock();
        }
    }

    private void rotateIfNeeded() {
        if (!closed && writer == null) {
            try {
                writerLock.readLock().unlock();
                writerLock.writeLock().lock();

                if (!closed && writer == null) {
                    openWriter();
                }
            } finally {
                writerLock.writeLock().unlock();
                writerLock.readLock().lock();
            }
            return;
        }

        final String currentDate = currentDate();
        if (!noRotation && shouldRotate(currentDate)) {
            try {
                writerLock.readLock().unlock();
                writerLock.writeLock().lock();

                if (shouldRotate(currentDate)) {
                    close();
                    if (currentDate != null && !date.equals(currentDate)) {
                        currentIndex = 0;
                        date = currentDate;
                    }
                    openWriter();
                }
            } finally {
                writerLock.writeLock().unlock();
                writerLock.readLock().lock();
            }
        }
    }

    private boolean shouldRotate(final String currentDate) { // new day, new file or limit exceeded
        return (currentDate != null && !date.equals(currentDate)) || (limit > 0 && written >= limit);
    }

    @Override
    public void close() {
        closed = true;

        writerLock.writeLock().lock();
        try {
            if (writer == null) {
                return;
            }
            writer.write(getFormatter().getTail(this));
            writer.flush();
            writer.close();
            currentFile = null;
            writer = null;
        } catch (final Exception e) {
            reportError(null, e, ErrorManager.CLOSE_FAILURE);
        } finally {
            writerLock.writeLock().unlock();
        }

        // wait for bg tasks if running
        backgroundTaskLock.lock();
        backgroundTaskLock.unlock();
    }

    @Override
    public void flush() {
        writerLock.readLock().lock();
        try {
            writer.flush();
        } catch (final Exception e) {
            reportError(null, e, ErrorManager.FLUSH_FAILURE);
        } finally {
            writerLock.readLock().unlock();
        }
    }

    protected synchronized void openWriter() {
        final var now = clock.instant();
        final long beforeRotation = now.toEpochMilli();

        writerLock.writeLock().lock();
        OutputStream fos = null;
        try {
            File pathname;
            do {
                pathname = new File(formatFilename(filenamePattern, date, currentIndex));
                final File parent = pathname.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    reportError("Unable to create [" + parent + "]", null, ErrorManager.OPEN_FAILURE);
                    writer = null;
                    currentFile = null;
                    return;
                }
                currentIndex++;
            } while (!overwrite && pathname.isFile()); // loop to ensure we don't overwrite existing files

            fos = new FileOutputStream(pathname, !truncateIfExists);
            final var os = new CountingStream(bufferSize > 0 ? new BufferedOutputStream(fos, bufferSize) : fos);
            final var encoding = getEncoding();
            final var streamWriter = (encoding != null) ? new OutputStreamWriter(os, encoding) : new OutputStreamWriter(os);
            writer = new PrintWriter(streamWriter, false);
            writer.write(getFormatter().getHead(this));
            currentFile = pathname;
        } catch (final Exception e) {
            reportError(null, e, ErrorManager.OPEN_FAILURE);
            writer = null;
            currentFile = null;
            if (fos != null) {
                try {
                    fos.close();
                } catch (final IOException e1) {
                    // no-op
                }
            }
        } finally {
            writerLock.writeLock().unlock();
        }

        backgroundTaskLock.lock();
        try {
            evict(beforeRotation);
        } catch (final Exception e) {
            reportError("Can't do the log eviction", e, ErrorManager.GENERIC_FAILURE);
        } finally {
            backgroundTaskLock.unlock();
        }
    }

    private void evict(final long now) {
        if (purgeExpiryDuration > 0) {
            purgeArchives(now);
        }
        if (archiveExpiryDuration > 0) {
            archiveIfNeeded(now);
        }
        if (maxArchives > 0) {
            deleteUndesiredArchives();
        }
    }

    private void purgeArchives(final long now) {
        final var archives = listArchives();
        if (archives != null) {
            for (final var archive : archives) {
                try {
                    final var attr = Files.readAttributes(archive.toPath(), BasicFileAttributes.class);
                    if (now - attr.creationTime().toMillis() > purgeExpiryDuration) {
                        if (!Files.deleteIfExists(archive.toPath())) {
                            // dont try to delete on exit cause we will find it again
                            reportError("Can't delete " + archive.getAbsolutePath() + ".", null, ErrorManager.GENERIC_FAILURE);
                        }
                    }
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private void archiveIfNeeded(final long now) {
        final File[] logs = new File(formatFilename(filenamePattern, zeroDate, 0)).getParentFile()
                .listFiles((dir, name) -> filenameRegex.matcher(name).matches());

        if (logs != null) {
            for (final var file : logs) {
                try {
                    final BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                    if (!file.equals(currentFile) && shouldArchive(now, attr)) {
                        createArchive(file);
                    }
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private void deleteUndesiredArchives() {
        final var archives = listArchives();
        if (archives != null) {
            final var toDelete = archives.length - maxArchives;
            if (toDelete > 0) {
                final var sorted = Stream.of(archives)
                        .sorted((a, b) -> { // older first
                            try {
                                return Files.readAttributes(a.toPath(), BasicFileAttributes.class).creationTime()
                                        .compareTo(Files.readAttributes(b.toPath(), BasicFileAttributes.class).creationTime());
                            } catch (final IOException ie) {
                                getErrorManager().error(ie.getMessage(), ie, ErrorManager.GENERIC_FAILURE);
                                return a.getName().compareTo(b.getName());
                            }
                        }).toArray(File[]::new);
                Stream.of(sorted)
                        .limit(toDelete)
                        .forEach(File::delete);
            }
        }
    }

    private boolean shouldArchive(final long now, final BasicFileAttributes attr) {
        return attr.creationTime().toMillis() < now && now - attr.lastModifiedTime().toMillis() > archiveExpiryDuration;
    }

    private File[] listArchives() {
        return archiveDir.listFiles((dir, name) -> archiveFilenameRegex.matcher(name).matches());
    }

    private String formatFilename(final String pattern, final String date, final int index) {
        return String.format(pattern, date, index);
    }

    private void createArchive(final File source) {
        final File target = new File(archiveDir, source.getName() + "." + archiveFormat);
        if (target.isFile()) {
            return;
        }

        final File parentFile = target.getParentFile();
        if (!parentFile.isDirectory() && !parentFile.mkdirs()) {
            throw new IllegalStateException("Can't create " + parentFile.getAbsolutePath());
        }

        if (archiveFormat.equalsIgnoreCase("gzip")) {
            try (final var outputStream = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(target)))) {
                Files.copy(source.toPath(), outputStream);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        } else { // consider file defines a zip whatever extension it is
            try (final var outputStream = new ZipOutputStream(new FileOutputStream(target))) {
                outputStream.setLevel(compressionLevel);

                try {
                    final ZipEntry zipEntry = new ZipEntry(source.getName());
                    outputStream.putNextEntry(zipEntry);
                    Files.copy(source.toPath(), outputStream);
                    outputStream.closeEntry();
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        try {
            if (!Files.deleteIfExists(source.toPath())) {
                reportError("Can't delete " + source.getAbsolutePath() + ".", null, ErrorManager.GENERIC_FAILURE);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected <T> T getProperty(final String name, final Function<String, T> mapper, final Supplier<T> defaultValue) {
        final var value = LogManager.getLogManager().getProperty(name);
        if (value == null) {
            return defaultValue.get();
        }
        return mapper.apply(value);
    }

    protected static String replace(final String str) { // [lang3] would be good but no dep for these classes is better
        String result = str;
        int start = str.indexOf("${");
        if (start >= 0) {
            final StringBuilder builder = new StringBuilder();
            int end = -1;
            while (start >= 0) {
                builder.append(str, end + 1, start);
                end = str.indexOf('}', start + 2);
                if (end < 0) {
                    end = start - 1;
                    break;
                }

                final String propName = str.substring(start + 2, end);
                String replacement = !propName.isEmpty() ? System.getProperty(propName) : null;
                if (replacement == null) {
                    replacement = System.getenv(propName);
                }
                if (replacement != null) {
                    builder.append(replacement);
                } else {
                    builder.append(str, start, end + 1);
                }
                start = str.indexOf("${", end + 1);
            }
            builder.append(str, end + 1, str.length());
            result = builder.toString();
        }
        return result;
    }

    private final class CountingStream extends OutputStream {
        private final OutputStream out;

        private CountingStream(final OutputStream out) {
            this.out = out;
            written = 0;
        }

        @Override
        public void write(final int b) throws IOException {
            out.write(b);
            written++;
        }

        @Override
        public void write(final byte[] buff) throws IOException {
            out.write(buff);
            written += buff.length;
        }

        @Override
        public void write(final byte[] buff, final int off, final int len) throws IOException {
            out.write(buff, off, len);
            written += len;
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }
}
