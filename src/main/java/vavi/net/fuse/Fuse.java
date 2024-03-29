/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.fuse;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;


/**
 * Fuse.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/05/29 umjammer initial version <br>
 */
public interface Fuse extends Closeable {

    /**  */
    String ENV_SINGLE_THREAD = "single_thread";

    /** should be non-blocking */
    void mount(FileSystem fs, String mountPoint, Map<String, Object> env) throws IOException;

    /**  */
    ServiceLoader<FuseProvider> serviceLoader = ServiceLoader.load(FuseProvider.class);

    /**  */
    static Fuse getFuse() {
        String className = System.getProperty("vavi.net.fuse.FuseProvider.class", "vavi.net.fuse.fusejna.FuseJnaFuseProvider");
        for (FuseProvider provider : serviceLoader) {
            if (provider.getClass().getName().equals(className)) {
                return provider.getFuse();
            }
        }
        throw new NoSuchElementException(className);
    }

    /** TODO location */
    static boolean isEnabled(String key, Map<String, Object> map) {
        return map.containsKey(key) && (map.get(key) == null || (boolean) map.get(key));
    }
}

/* */
