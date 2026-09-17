package org.wanshu.reader.core.source;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FixtureHelper {

    public static String readFixture(String name) {
        File[] candidates = new File[] {
                new File("../../test/fixtures/" + name),
                new File("../test/fixtures/" + name),
                new File("test/fixtures/" + name)
        };

        File found = null;
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i].exists()) {
                found = candidates[i];
                break;
            }
        }

        if (found == null) {
            throw new IllegalArgumentException("Fixture not found: " + name);
        }

        try {
            FileInputStream in = new FileInputStream(found);
            byte[] bytes = new byte[(int) found.length()];
            int read = 0;
            while (read < bytes.length) {
                int r = in.read(bytes, read, bytes.length - read);
                if (r == -1) break;
                read += r;
            }
            in.close();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read fixture: " + name, e);
        }
    }
}
