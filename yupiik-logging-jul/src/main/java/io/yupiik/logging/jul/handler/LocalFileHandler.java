package io.yupiik.logging.jul.handler;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.time.Duration;
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
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.function.Function.identity;

// from https://github.com/apache/tomee/blob/master/tomee/tomee-juli/src/main/java/org/apache/tomee/jul/handler/rotating/LocalFileHandler.java
public class LocalFileHandler extends Handler {
    private static final int BUFFER_SIZE = 8102;

    private long limit = 0;
    private int bufferSize = -1;
    private Pattern filenameRegex;
    private Pattern archiveFilenameRegex;
    private String filenamePattern = "${application.base}/logs/logs.%s.%03d.log";
    private String archiveFormat = "gzip";
    private long dateCheckInterval;
    private long archiveExpiryDuration;
    private int compressionLevel;
    private long purgeExpiryDuration;
    private File archiveDir;

    private volatile int currentIndex;
    private volatile long lastTimestamp;
    private volatile String date;
    private volatile PrintWriter writer;
    private volatile int written;
    private final ReadWriteLock writerLock = new ReentrantReadWriteLock();
    private final Lock backgroundTaskLock = new ReentrantLock();
    private volatile boolean closed;

    public LocalFileHandler() {
        configure();
    }

    private void configure() {
        date = currentDate();

        final String className = LocalFileHandler.class.getName(); //allow classes to override

        dateCheckInterval = getProperty(className + ".dateCheckInterval", Duration::parse, () -> Duration.ofSeconds(5)).toMillis();
        filenamePattern = replace(getProperty(className + ".filenamePattern", identity(), () -> filenamePattern));
        limit = getProperty(className + ".limit", Long::parseLong, () -> 10 * 1024 * 1024L) /*10m*/;

        final int lastSep = Math.max(filenamePattern.lastIndexOf('/'), filenamePattern.lastIndexOf('\\'));
        String fileNameReg = lastSep >= 0 ? filenamePattern.substring(lastSep + 1) : filenamePattern;
        fileNameReg = fileNameReg.replace("%s", "\\d{4}\\-\\d{2}\\-\\d{2}"); // date.
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
        ;
        archiveExpiryDuration = getProperty(className + ".archiveOlderThan", v -> Duration.parse(v).toMillis(), () -> -1L);
        archiveDir = new File(replace(getProperty(className + ".archiveDirectory", identity(), () -> "${application.base}/logs/archives/")));
        archiveFormat = replace(getProperty(className + ".archiveFormat", identity(), () -> archiveFormat));
        archiveFilenameRegex = Pattern.compile(fileNameReg + "\\." + archiveFormat);

        purgeExpiryDuration = getProperty(className + ".purgeOlderThan", v -> Duration.parse(v).toMillis(), () -> -1L);

        try {
            bufferSize = getProperty(className + ".bufferSize", Integer::parseInt, () -> -1);
        } catch (final NumberFormatException ignore) {
            // no-op
        }

        //setErrorManager(new ErrorManager());
        lastTimestamp = System.currentTimeMillis();
    }

    protected String currentDate() {
        return new Timestamp(System.currentTimeMillis()).toString().substring(0, 10);
    }

    @Override
    public void publish(final LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }

        final long now = System.currentTimeMillis();
        final String tsDate;
        // just do it once / sec if we have a lot of log, can make some log appearing in the wrong file but better than doing it each time
        if (now - lastTimestamp > dateCheckInterval) { // using as much as possible volatile to avoid to lock too much
            lastTimestamp = now;
            tsDate = currentDate();
        } else {
            tsDate = null;
        }

        try {
            writerLock.readLock().lock();
            rotateIfNeeded(tsDate);

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

    private void rotateIfNeeded(final String currentDate) {
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
        } else if (shouldRotate(currentDate)) {
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

    protected void openWriter() {
        final long beforeRotation = System.currentTimeMillis();

        writerLock.writeLock().lock();
        OutputStream fos = null;
        OutputStream os = null;
        try {
            File pathname;
            do {
                pathname = new File(formatFilename(filenamePattern, date, currentIndex));
                final File parent = pathname.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    reportError("Unable to create [" + parent + "]", null, ErrorManager.OPEN_FAILURE);
                    writer = null;
                    return;
                }
                currentIndex++;
            } while (pathname.isFile()); // loop to ensure we don't overwrite existing files

            final String encoding = getEncoding();
            fos = new FileOutputStream(pathname, true);
            os = new CountingStream(bufferSize > 0 ? new BufferedOutputStream(fos, bufferSize) : fos);
            writer = new PrintWriter((encoding != null) ? new OutputStreamWriter(os, encoding) : new OutputStreamWriter(os), false);
            writer.write(getFormatter().getHead(this));
        } catch (final Exception e) {
            reportError(null, e, ErrorManager.OPEN_FAILURE);
            writer = null;
            if (os != null) {
                try {
                    os.close();
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
        if (purgeExpiryDuration > 0) { // purging archives
            final File[] archives = archiveDir.listFiles((dir, name) -> archiveFilenameRegex.matcher(name).matches());

            if (archives != null) {
                for (final File archive : archives) {
                    try {
                        final BasicFileAttributes attr = Files.readAttributes(archive.toPath(), BasicFileAttributes.class);
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
        if (archiveExpiryDuration > 0) { // archiving log files
            final File[] logs = new File(formatFilename(filenamePattern, "0000-00-00", 0)).getParentFile()
                    .listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(final File dir, final String name) {
                            return filenameRegex.matcher(name).matches();
                        }
                    });

            if (logs != null) {
                for (final File file : logs) {
                    try {
                        final BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                        if (attr.creationTime().toMillis() < now && now - attr.lastModifiedTime().toMillis() > archiveExpiryDuration) {
                            createArchive(file);
                        }
                    } catch (final IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
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
            try (final OutputStream outputStream = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(target)))) {
                final byte[] buffer = new byte[BUFFER_SIZE];
                try (final FileInputStream inputStream = new FileInputStream(source)) {
                    copyStream(inputStream, outputStream, buffer);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        } else { // consider file defines a zip whatever extension it is
            try (final ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(target))) {
                outputStream.setLevel(compressionLevel);

                final byte[] buffer = new byte[BUFFER_SIZE];
                try (final FileInputStream inputStream = new FileInputStream(source)) {
                    final ZipEntry zipEntry = new ZipEntry(source.getName());
                    outputStream.putNextEntry(zipEntry);
                    copyStream(inputStream, outputStream, buffer);
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

    private static void copyStream(final InputStream inputStream, final OutputStream outputStream, final byte[] buffer) throws IOException {
        int n;
        while ((n = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, n);
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
