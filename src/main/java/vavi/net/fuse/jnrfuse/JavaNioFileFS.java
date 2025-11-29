/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.fuse.jnrfuse;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jnr.constants.platform.Errno;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.Flock;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;
import vavi.nio.file.Util;

import static java.lang.System.getLogger;


/**
 * JavaNioFileFS. (jnr-fuse)
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/02/29 umjammer initial version <br>
 */
class JavaNioFileFS extends FuseStubFS {

    private static final Logger logger = getLogger(JavaNioFileFS.class.getName());

    private static final int BUFFER_SIZE = 0x10000;

    /** */
    private transient FileSystem fileSystem;

    /** key for env, no need to specify value */
    static final String ENV_IGNORE_APPLE_DOUBLE = "noappledouble";

    /** */
    private final AtomicLong fileHandle = new AtomicLong(0);

    /** <file handle, channel> */
    private static final ConcurrentMap<Long, SeekableByteChannel> fileHandles = new ConcurrentHashMap<>();

    protected boolean ignoreAppleDouble;

    /**
     * @param fileSystem a file system to wrap by fuse
     */
    public JavaNioFileFS(FileSystem fileSystem, Map<String, Object> env) {
        this.fileSystem = fileSystem;
        ignoreAppleDouble = JnrFuseFuse.isEnabled(ENV_IGNORE_APPLE_DOUBLE, env);
logger.log(Level.DEBUG, "ENV_IGNORE_APPLE_DOUBLE: " + ignoreAppleDouble);
    }

    @Override
    public int access(String path, int access) {
logger.log(Level.TRACE, "access: " + path);
        try {
            // TODO access
            fileSystem.provider().checkAccess(fileSystem.getPath(path));
            return 0;
        } catch (NoSuchFileException e) {
logger.log(Level.INFO, e);
            return -ErrorCodes.ENOENT();
        } catch (IOException e) {
logger.log(Level.INFO, e);
            return -ErrorCodes.EACCES();
        }
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo info) {
logger.log(Level.DEBUG, "create: " + path);
        try {
            Set<OpenOption> options = new HashSet<>();
            options.add(StandardOpenOption.WRITE);
            options.add(StandardOpenOption.CREATE_NEW);
            SeekableByteChannel channel = fileSystem.provider().newByteChannel(fileSystem.getPath(path), options);
            long fh = fileHandle.incrementAndGet();
            fileHandles.put(fh, channel);
            info.fh.set(fh);

            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int getattr(String path, FileStat stat) {
logger.log(Level.TRACE, "getattr: " + path);
        try {
            BasicFileAttributes attributes =
                    fileSystem.provider().readAttributes(fileSystem.getPath(path), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            if (attributes instanceof PosixFileAttributes) {
                long mode = JnrFuseFuse.permissionsToMode(((PosixFileAttributes) attributes).permissions());
                if (attributes.isDirectory()) {
                    stat.st_mode.set(FileStat.S_IFDIR | mode);
                    stat.st_mtim.tv_sec.set(attributes.lastModifiedTime().to(TimeUnit.SECONDS));
                    stat.st_ctim.tv_sec.set(attributes.creationTime().to(TimeUnit.SECONDS));
                } else {
                    stat.st_mode.set(FileStat.S_IFREG | mode);
                    stat.st_mtim.tv_sec.set(attributes.lastModifiedTime().to(TimeUnit.SECONDS));
                    stat.st_ctim.tv_sec.set(attributes.creationTime().to(TimeUnit.SECONDS));
                    stat.st_size.set(attributes.size());
                }
            } else {
                if (attributes.isDirectory()) {
                    stat.st_mode.set(FileStat.S_IFDIR | 0755);
                    stat.st_mtim.tv_sec.set(attributes.lastModifiedTime().to(TimeUnit.SECONDS));
                    stat.st_ctim.tv_sec.set(attributes.creationTime().to(TimeUnit.SECONDS));
                } else {
                    stat.st_mode.set(FileStat.S_IFREG | 0644);
                    stat.st_mtim.tv_sec.set(attributes.lastModifiedTime().to(TimeUnit.SECONDS));
                    stat.st_ctim.tv_sec.set(attributes.creationTime().to(TimeUnit.SECONDS));
                    stat.st_size.set(attributes.size());
                }
            }
            return 0;
        } catch (NoSuchFileException e) {
            if (e.getMessage().startsWith("ignore apple double file:")) {
logger.log(Level.TRACE, e.getMessage());
                return 0;
            } else {
                if (ignoreAppleDouble) {
                    if (Util.isAppleDouble(path)) {
logger.log(Level.TRACE, e.getMessage());
                    } else {
logger.log(Level.DEBUG, e);
                    }
                }
                return -ErrorCodes.ENOENT();
            }
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int fgetattr(String path, FileStat stat, FuseFileInfo info)
    {
logger.log(Level.DEBUG, "fgetattr: " + path);
        return getattr(path, stat);
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
logger.log(Level.DEBUG, "mkdir: " + path);
        try {
            fileSystem.provider().createDirectory(fileSystem.getPath(path));
            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int open(String path, FuseFileInfo info) {
        try {
            Set<OpenOption> options = new HashSet<>();
            options.add(StandardOpenOption.READ);
            SeekableByteChannel channel = fileSystem.provider().newByteChannel(fileSystem.getPath(path), options);
            long fh = fileHandle.incrementAndGet();
            fileHandles.put(fh, channel);
            info.fh.set(fh);
logger.log(Level.DEBUG, "open: " + path + ", fh: " + fh);

            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int read(String path, Pointer buf, long size, long offset, FuseFileInfo info) {
logger.log(Level.DEBUG, "read: " + path + ", " + offset + ", " + size + ", fh: " + info.fh.get());
        try {
            if (fileHandles.containsKey(info.fh.get())) {
                SeekableByteChannel channel = fileHandles.get(info.fh.get());
                channel.position(offset);
                ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
                long pos = 0;
logger.log(Level.TRACE, "Attempting to read %d-%d:".formatted(offset, offset + size));
                do {
                    bb.clear();
                    bb.limit((int) Math.min(bb.capacity(), size - pos));
                    int read = channel.read(bb);
                    if (read == -1) {
logger.log(Level.TRACE, "Reached EOF");
                        return (int) pos; // reached EOF TODO: wtf cast
                    } else {
logger.log(Level.TRACE, "Reading %d-%d".formatted(offset + pos, offset + pos + read));
                        buf.put(pos, bb.array(), 0, read);
                        pos += read;
                    }
                } while (pos < size);
                return (int) pos;
            } else {
logger.log(Level.DEBUG, "read: no fh: " + path);
                return -ErrorCodes.EEXIST();
            }
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filler, @off_t long offset, FuseFileInfo info) {
logger.log(Level.TRACE, "readdir: " + path);
        try {
            fileSystem.provider().newDirectoryStream(fileSystem.getPath(path), p -> true)
                .forEach(p -> {
logger.log(Level.TRACE, "p: " + p);
                    try {
                        filler.apply(buf, Util.toFilenameString(p), null, 0);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int rename(String path, String newName) {
logger.log(Level.DEBUG, "rename: " + path);
        try {
            fileSystem.provider().move(fileSystem.getPath(path), fileSystem.getPath(newName));
            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int rmdir(String path) {
logger.log(Level.DEBUG, "rmdir: " + path);
        try {
            fileSystem.provider().delete(fileSystem.getPath(path));
            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int truncate(String path, long offset) {
logger.log(Level.DEBUG, "truncate: " + path);
        // TODO
        return -ErrorCodes.ENOSYS();
    }

    @Override
    public int unlink(String path) {
logger.log(Level.DEBUG, "unlink: " + path);
        try {
            fileSystem.provider().delete(fileSystem.getPath(path));
            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo info) {
logger.log(Level.DEBUG, "write: " + path + ", " + offset + ", " + size + ", fh: " + info.fh.get());
        try {
            if (fileHandles.containsKey(info.fh.get())) {
                ByteBuffer bb = ByteBuffer.allocate(BUFFER_SIZE);
                long written = 0;
                SeekableByteChannel channel = fileHandles.get(info.fh.get());
try { // TODO ad-hoc
                channel.position(offset);
} catch (IOException e) {
 if (e.getMessage().contains("@vavi")) {
  long o = Long.parseLong(e.getMessage().substring(9, e.getMessage().length() - 1));
  if (offset > o) {
   logger.log(Level.ERROR, "write: skip bad position: " + offset);
   throw new IOException("cannot skip last bytes send", e);
  } else {
   logger.log(Level.WARNING, "write: correct bad position: " + offset + " -> " + o);
   return Math.min((int) (o - offset), (int) size);
  }
 } else {
  throw e;
 }
}
                do {
                    long remaining = size - written;
                    bb.clear();
                    int len = (int) Math.min(remaining, bb.capacity());
                    buf.get(written, bb.array(), 0, len);
                    bb.limit(len);
                    int r = channel.write(bb);
                    written += r;
                } while (written < size);
                return (int) written;
            } else {
                return -ErrorCodes.EEXIST();
            }
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
logger.log(Level.TRACE, "statfs: " + path);
        try {
            FileStore fileStore = fileSystem.getFileStores().iterator().next();
//logger.log(Level.INFO, "total: " + fileStore.getTotalSpace());
//logger.log(Level.INFO, "free: " + fileStore.getUsableSpace());

            long blockSize = 512;

            long total = fileStore.getTotalSpace() / blockSize;
            long free = fileStore.getUsableSpace() / blockSize;
            long used = total - free;

            stbuf.f_bavail.set(used);
            stbuf.f_bfree.set(free);
            stbuf.f_blocks.set(total);
            stbuf.f_bsize.set(blockSize);
            stbuf.f_favail.set(-1);
            stbuf.f_ffree.set(-1);
            stbuf.f_files.set(-1);
            stbuf.f_frsize.set(1);

            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int chmod(String path, @mode_t long mode) {
logger.log(Level.DEBUG, "chmod: " + path);
        try {
            if (fileSystem.provider().getFileStore(fileSystem.getPath(path)).supportsFileAttributeView(PosixFileAttributeView.class)) {
                PosixFileAttributeView attrs = fileSystem.provider().getFileAttributeView(fileSystem.getPath(path), PosixFileAttributeView.class);
                attrs.setPermissions(JnrFuseFuse.modeToPermissions(mode));
                return 0;
            } else {
                return -Errno.EAFNOSUPPORT.ordinal();
            }
        } catch (Exception e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int release(String path, FuseFileInfo info) {
logger.log(Level.DEBUG, "release: " + path + ", fh: " + info.fh.get());
        try {
            if (fileHandles.containsKey(info.fh.get())) {
                SeekableByteChannel channel = fileHandles.get(info.fh.get());
                channel.close();
                return 0;
            } else {
logger.log(Level.DEBUG, "release: no fh: " + path);
                return -ErrorCodes.EEXIST();
            }
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        } finally {
            fileHandles.remove(info.fh.get());
        }
    }

    @Override
    public int lock(String path, FuseFileInfo info, int cmd, Flock flock) {
logger.log(Level.DEBUG, "lock: " + path + ", fh: " + info.fh.get());
        return 0;
    }
}
