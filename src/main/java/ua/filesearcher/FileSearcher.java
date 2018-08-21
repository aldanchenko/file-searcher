package ua.filesearcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Search files by name.
 * <p>
 * This class use concurrency to speed up process of search (https://en.wikipedia.org/wiki/Producer%E2%80%93consumer_problem).
 * <p>
 * Source of idea - https://stackoverflow.com/questions/26902156/java-searching-computer-for-a-specific-file-a-faster-way.
 *
 * By default it skip hidden directories (Please, check {@link FileSearchProducer#isSkipDirectory(File)}.
 */
public class FileSearcher {

    /**
     * File queue capacity (size).
     */
    private static final int FILE_QUEUE_CAPACITY = 10000;

    /**
     * Name of system property for get OS type.
     */
    private static final String OS_NAME_SYSTEM_PROPERTY = "os.name";

    /**
     * Prefix for Windows OS.
     */
    private static final String WINDOWS_OS_NAME_PREFIX = "win";

    /**
     * Counter for directory queue current size.
     */
    private AtomicLong queueSizeAtomic = new AtomicLong(0);

    /**
     * Count of producers (directory producers).
     */
    private int producersCount = 6;

    /**
     * Count of consumers (file queue consumers).
     */
    private int consumersCount = 1;

    /**
     * Default POISON PILL for queue's.
     */
    private final File poisonPillFile = new File("");

    /**
     * File name to search.
     */
    private String searchingFileName;

    /**
     * Find files on computer.
     *
     * @param fileName - file name to search
     *
     * @throws ExecutionException   -
     * @throws InterruptedException -
     */
    public List<File> find(String fileName) throws ExecutionException, InterruptedException {
        this.searchingFileName = fileName;

        final ExecutorService executorService = Executors.newFixedThreadPool(producersCount + consumersCount);

        BlockingQueue<File> directoryBlockingQueue = new LinkedBlockingQueue<>();
        BlockingQueue<File> fileBlockingQueue = new LinkedBlockingQueue<>(FILE_QUEUE_CAPACITY);

        for (int i = 0; i < producersCount; i++) {
            executorService.submit(new FileSearchProducer(directoryBlockingQueue, fileBlockingQueue));
        }

        if (isWindows()) {
            final File[] systemDrives = File.listRoots();

            for (File systemDrive : systemDrives) {
                queueSizeAtomic.incrementAndGet();

                directoryBlockingQueue.add(systemDrive);
            }
        } else {
            queueSizeAtomic.incrementAndGet();

            directoryBlockingQueue.add(new File("/"));
        }

        Future<List<File>> future = executorService.submit(new FileSearchConsumer(fileBlockingQueue));

        List<File> fileList = future.get();

        executorService.shutdownNow();

        return fileList;
    }

    /**
     * Check is Windows OS.
     *
     * @return boolean
     */
    private boolean isWindows() {
        return System.getProperty(OS_NAME_SYSTEM_PROPERTY).toLowerCase().contains(WINDOWS_OS_NAME_PREFIX);
    }

    /**
     * Iterate over directories and add files to file queue.
     */
    private class FileSearchProducer implements Runnable {
        private BlockingQueue<File> directoryBlockingQueue;
        private BlockingQueue<File> fileBlockingQueue;

        FileSearchProducer(BlockingQueue<File> directoryBlockingQueue, BlockingQueue<File> fileBlockingQueue) {
            this.directoryBlockingQueue = directoryBlockingQueue;
            this.fileBlockingQueue = fileBlockingQueue;
        }

        @Override
        public void run() {
            try {
                File directory = directoryBlockingQueue.take();

                while (directory != poisonPillFile) {
                    File[] files = directory.listFiles();

                    if (Objects.nonNull(files)) {
                        for (File currentFile : files) {
                            if (currentFile.isDirectory() && !isSkipDirectory(currentFile)) {
                                queueSizeAtomic.incrementAndGet();

                                directoryBlockingQueue.put(currentFile);
                            } else {
                                fileBlockingQueue.put(currentFile);
                            }
                        }
                    }

                    long queueSizeValue = queueSizeAtomic.decrementAndGet();

                    if (queueSizeValue == 0) {
                        // End FileSearchConsumer (file queue).
                        fileBlockingQueue.put(poisonPillFile);

                        // End DirectorySearchConsumer (directory queue).
                        for (int i = 0; i < producersCount; i++) {
                            directoryBlockingQueue.put(poisonPillFile);
                        }
                    }

                    directory = directoryBlockingQueue.take();
                }
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
        }

        /**
         * Skip hidden directories and default system directories.
         *
         * @param file - source file
         *
         * @return boolean
         */
        private boolean isSkipDirectory(File file) {
            return file.getAbsolutePath().startsWith("/proc")
                    || file.getAbsolutePath().startsWith("/sys")
                    || file.getAbsolutePath().startsWith("/dev")
                    || file.getAbsolutePath().startsWith("/cdrom")
                    || file.getAbsolutePath().contains("/.");
        }
    }

    /**
     * Compare files from files queue with {@link #searchingFileName}.
     */
    private class FileSearchConsumer implements Callable<List<File>> {
        private BlockingQueue<File> fileBlockingQueue;

        FileSearchConsumer(BlockingQueue<File> fileBlockingQueue) {
            this.fileBlockingQueue = fileBlockingQueue;
        }

        @Override
        public List<File> call() throws Exception {
            List<File> resultFiles = new ArrayList<>();

            File file = fileBlockingQueue.take();

            while (file != poisonPillFile) {
                if (file.getName().equals(searchingFileName)) {
                    resultFiles.add(file);
                }

                file = fileBlockingQueue.take();
            }

            return resultFiles;
        }
    }
}
