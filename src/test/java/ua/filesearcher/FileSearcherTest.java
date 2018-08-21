package ua.filesearcher;

import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test case for {@link FileSearcher}.
 */
public class FileSearcherTest {

    @Test
    public void testFindFileSearcherJava() throws ExecutionException, InterruptedException {
        FileSearcher fileSearcher = new FileSearcher();

        List<File> resultFiles = fileSearcher.find("FileSearcher.java");

        assertNotNull(resultFiles);
        assertTrue(resultFiles.size() > 0);
    }
}
