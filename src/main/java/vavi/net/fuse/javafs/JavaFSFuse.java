/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.fuse.javafs;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.FileSystem;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import vavi.net.fuse.Fuse;

import co.paralleluniverse.javafs.JavaFS;

import static java.lang.System.getLogger;


/**
 * JavaFSFuse. (javafs engine)
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/05/29 umjammer initial version <br>
 */
public class JavaFSFuse implements Fuse {

    private static final Logger logger = getLogger(JavaFSFuse.class.getName());

    public static final String ENV_READ_ONLY = "readOnly";

    public static final String ENV_DEBUG = "debug";

    /** */
    private String mountPoint;

    @Override
    public void mount(FileSystem fs, String mountPoint, Map<String, Object> env) throws IOException {
        this.mountPoint = mountPoint;
        boolean readOnly = false;
        boolean debug = false;
        Map<String, String> env_ = null;
        if (env != null) {
            if (env.containsKey(ENV_READ_ONLY)) {
                readOnly = Boolean.parseBoolean(String.valueOf(env.get(ENV_READ_ONLY)));
                env.remove(ENV_READ_ONLY);
            }
            if (env.containsKey(ENV_DEBUG)) {
                debug = Boolean.parseBoolean(String.valueOf(env.get(ENV_DEBUG)));
                env.remove(ENV_DEBUG);
            }
            env_ = new HashMap<>();
            for (Map.Entry<String, Object> e : env.entrySet()) {
                env_.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
            }
        }
//logger.log(Level.INFO, "debug: " + debug);
//logger.log(Level.INFO, "readonly: " + debug);
        JavaFS.mount(fs, Paths.get(mountPoint), readOnly, debug, env_);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { close(); } catch (IOException e) { e.printStackTrace(); }}));
    }

    @Override
    public void close() throws IOException {
        try {
            if (mountPoint != null) {
logger.log(Level.INFO, "umount...");
                JavaFS.unmount(Paths.get(mountPoint));
                mountPoint = null;
logger.log(Level.INFO, "umount done");
            }
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("Tried to unmount a filesystem which is not mounted")) {
logger.log(Level.INFO, "already umount");
            } else {
                throw e;
            }
        } catch (IOException e) {
logger.log(Level.WARNING, "umount: " + e);
            throw e;
        }
    }
}
