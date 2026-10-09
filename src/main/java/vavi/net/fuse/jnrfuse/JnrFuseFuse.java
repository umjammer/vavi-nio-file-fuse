/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.fuse.jnrfuse;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import co.paralleluniverse.fuse.TypeMode;
import ru.serce.jnrfuse.FuseStubFS;
import vavi.net.fuse.Fuse;

import static java.lang.System.getLogger;


/**
 * JnrFuseFuse. (jnr-fuse engine)
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/05/29 umjammer initial version <br>
 */
public class JnrFuseFuse implements Fuse {

    private static final Logger logger = getLogger(JnrFuseFuse.class.getName());

    /** key for env, no need to specify value */
    public static final String ENV_IGNORE_APPLE_DOUBLE = JavaNioFileFS.ENV_IGNORE_APPLE_DOUBLE;

    /** TODO utility delegate */
    static boolean isEnabled(String key, Map<String, Object> map) {
        return Fuse.isEnabled(key, map);
    }

    /** */
    private FuseStubFS fuse;

    /** non-daemon thread */
    private final ExecutorService es = Executors.newSingleThreadExecutor();

    /** max time to wait for mounting/unmounting is done */
    private static final long TIMEOUT_MILLIS = 10_000;

    /** */
    private Path mountPoint;

    @Override
    public void mount(FileSystem fs, String mountPoint, Map<String, Object> env) throws IOException {
        if (env.containsKey(ENV_SINGLE_THREAD) && (Boolean) env.get(ENV_SINGLE_THREAD)) {
            fuse = new SingleThreadJavaNioFileFS(fs, env);
logger.log(Level.INFO, "use single thread");
        } else {
            fuse = new JavaNioFileFS(fs, env);
        }
        this.mountPoint = Paths.get(mountPoint).toAbsolutePath();
        Future<?> mounting = es.submit(() -> {
            // jnrfuse non-blocking thread is daemon
            // so make mount blocking and make own non-daemon thread
            fuse.mount(this.mountPoint, true);
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { close(); } catch (IOException e) { e.printStackTrace(); }}));

        // the blocking mount returns only when unmounted, so wait the mount point is mounted actually.
        // without this, operations right after this method go to the mount point directory itself,
        // and an unmount before the mount is done leaves an orphan mount.
        long limit = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!isMounted(this.mountPoint, false)) {
            if (mounting.isDone()) {
                try {
                    mounting.get();
                } catch (ExecutionException e) {
                    throw new IOException("mount failed: " + mountPoint, e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new IOException("mount failed: " + mountPoint);
            }
            if (System.currentTimeMillis() > limit) {
                throw new IOException("mount timeout: " + mountPoint);
            }
            sleep();
        }
logger.log(Level.DEBUG, "mounted: " + mountPoint);
    }

    @Override
    public void close() throws IOException {
        if (fuse != null) {
logger.log(Level.INFO, "unmount...");
            es.shutdown();
            fuse.umount();
            fuse = null;
            // umount might be asynchronous
            long limit = System.currentTimeMillis() + TIMEOUT_MILLIS;
            while (isMounted(mountPoint, true)) {
                if (System.currentTimeMillis() > limit) {
                    throw new IOException("unmount timeout: " + mountPoint);
                }
                sleep();
            }
logger.log(Level.INFO, "unmount done");
        }
    }

    /**
     * a mount point is on the different file store from its parent while mounted
     * @param whenError the result when the mount point cannot be accessed (e.g. EIO by a fuse daemon not ready or dead)
     */
    private static boolean isMounted(Path mountPoint, boolean whenError) {
        try {
            return !Files.getFileStore(mountPoint).equals(Files.getFileStore(mountPoint.getParent()));
        } catch (IOException e) {
            return whenError;
        }
    }

    /** */
    private static void sleep() throws IOException {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    /** */
    static long permissionsToMode(Set<PosixFilePermission> permissions) {
        long mode = 0;
        for (PosixFilePermission px : permissions) {
            switch (px) {
            case OWNER_READ: mode |= TypeMode.S_IRUSR; break;
            case OWNER_WRITE: mode |= TypeMode.S_IWUSR; break;
            case OWNER_EXECUTE: mode |= TypeMode.S_IXUSR; break;
            case GROUP_READ: mode |= TypeMode.S_IRGRP; break;
            case GROUP_WRITE: mode |= TypeMode.S_IWGRP; break;
            case GROUP_EXECUTE: mode |= TypeMode.S_IXGRP; break;
            case OTHERS_READ: mode |= TypeMode.S_IROTH; break;
            case OTHERS_WRITE: mode |= TypeMode.S_IWOTH; break;
            case OTHERS_EXECUTE: mode |= TypeMode.S_IXOTH; break;
            }
        }
        return mode;
    }

    /** */
    static Set<PosixFilePermission> modeToPermissions(long mode) {
        EnumSet<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        if ((mode & TypeMode.S_IRUSR) != 0)
            permissions.add(PosixFilePermission.OWNER_READ);
        if ((mode & TypeMode.S_IWUSR) != 0)
            permissions.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & TypeMode.S_IXUSR) != 0)
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
        if ((mode & TypeMode.S_IRGRP) != 0)
            permissions.add(PosixFilePermission.GROUP_READ);
        if ((mode & TypeMode.S_IWGRP) != 0)
            permissions.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & TypeMode.S_IXGRP) != 0)
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
        if ((mode & TypeMode.S_IROTH) != 0)
            permissions.add(PosixFilePermission.OTHERS_READ);
        if ((mode & TypeMode.S_IWOTH) != 0)
            permissions.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & TypeMode.S_IXOTH) != 0)
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        return permissions;
    }
}
