package ru.serce.jnrfuse.utils;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;

import ru.serce.jnrfuse.FuseException;

import static java.lang.System.getLogger;


/**
 * ad-hoc replacement
 * TODO doesn't work well from osxfuse 4.10.0 and Sequoia 15.3.2, what's the difference from ↓ ???
 * @see "https://github.com/umjammer/javafs/blob/vavi-patch/src/main/java/co/paralleluniverse/fuse/Fuse.java#L204"
 */
public class MountUtils {

    private static final Logger logger = getLogger(MountUtils.class.getName());

    /**
     * Perform/force an umount at the provided Path
     */
    public static void umount(Path mountPoint) {
        String mountPath = mountPoint.toAbsolutePath().toString();
        try {
            int r = new ProcessBuilder("fusermount", "-z", "-u", mountPath).start().waitFor();
            if (r != 0) {
logger.log(Level.INFO, "fusemount -u failed: " + r);
                forceUmount(mountPoint);
            }
        } catch (IOException e) {
logger.log(Level.INFO, "fusemount -u failed: " + e.getMessage());
            try {
                int retry = 0;
                long wait = 200;
                while (retry < 5) {
                    try {
                        forceUmount(mountPoint);
                    } catch(FuseException e2){
logger.log(Level.INFO, "umount -f failed: " + e2.getMessage());
                        retry++;
                        wait *= 2;
logger.log(Level.INFO, "retry: " + retry + ", wait: " + wait);
                        Thread.sleep(wait);
                    }
                }
                return;
            } catch (IOException e2) {
                e2.addSuppressed(e);
            } catch (InterruptedException e2) {
                Thread.currentThread().interrupt();
                e2.addSuppressed(e);
            }
            throw new FuseException("Unable to umount FS", e);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new FuseException("Unable to umount FS", ie);
        }
    }

    private static void forceUmount(Path mountPoint) throws IOException, InterruptedException {
        String mountPath = mountPoint.toAbsolutePath().toString();
        int r = new ProcessBuilder("umount", "-f", mountPath).start().waitFor();
        if (r != 0) {
logger.log(Level.INFO, "umount -f failed: " + r);
            throw new FuseException("done abnormally: " + r);
        }
    }
}
