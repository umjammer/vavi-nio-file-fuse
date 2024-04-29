/*
 * Copyright (c) 2016 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.net.fuse.fusejna;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
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

import vavi.nio.file.Util;

import jnr.constants.platform.Errno;
import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.StructFuseFileInfo.FileInfoWrapper;
import net.fusejna.StructStat.StatWrapper;
import net.fusejna.StructStatvfs.StatvfsWrapper;
import net.fusejna.types.TypeMode.ModeWrapper;
import net.fusejna.types.TypeMode.NodeType;
import net.fusejna.util.FuseFilesystemAdapterAssumeImplemented;


/**
 * JavaNioFileFS. (fuse-jna)
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2016/02/29 umjammer initial version <br>
 */
class JavaNioFileFS extends FuseFilesystemAdapterAssumeImplemented {

    private static final Logger logger = System.getLogger(JavaNioFileFS.class.getName());

    /** */
    protected transient FileSystem fileSystem;

    /** key for env, no need to specify value */
    static final String ENV_IGNORE_APPLE_DOUBLE = "noappledouble";

    /** */
    private final AtomicLong fileHandle = new AtomicLong(0);

    /** <file handle, channel> */
    private final ConcurrentMap<Long, SeekableByteChannel> fileHandles = new ConcurrentHashMap<>();

    protected boolean ignoreAppleDouble;

    /**
     * @param fileSystem a file system to wrap by fuse
     */
    public JavaNioFileFS(FileSystem fileSystem, Map<String, Object> env) {
        this.fileSystem = fileSystem;
        ignoreAppleDouble = FuseJnaFuse.isEnabled(ENV_IGNORE_APPLE_DOUBLE, env);
logger.log(Level.DEBUG, "ENV_IGNORE_APPLE_DOUBLE: " + ignoreAppleDouble);
    }

    @Override
    public int access(final String path, final int access) {
logger.log(Level.TRACE, "access: " + path);
        try {
            // TODO access
            fileSystem.provider().checkAccess(fileSystem.getPath(path));
            return 0;
        } catch (NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (AccessDeniedException e) {
logger.log(Level.INFO, e);
            return -ErrorCodes.EACCES();
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EACCES();
        }
    }

    @Override
    public int create(final String path, final ModeWrapper mode, final FileInfoWrapper info) {
logger.log(Level.DEBUG, "create: " + path);
        try {
            Set<OpenOption> options = new HashSet<>();
            options.add(StandardOpenOption.WRITE);
            options.add(StandardOpenOption.CREATE_NEW);
            SeekableByteChannel channel = fileSystem.provider().newByteChannel(fileSystem.getPath(path), options);
            long fh = fileHandle.incrementAndGet();
            fileHandles.put(fh, channel);
            info.fh(fh);

            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int getattr(final String path, final StatWrapper stat) {
logger.log(Level.TRACE, "getattr: " + path);
        try {
            BasicFileAttributes attributes =
                    fileSystem.provider().readAttributes(fileSystem.getPath(path), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            if (attributes instanceof PosixFileAttributes) {
                boolean[] m = FuseJnaFuse.permissionsToMode(((PosixFileAttributes) attributes).permissions());
                if (attributes.isDirectory()) {
                    stat.setMode(NodeType.DIRECTORY, m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8])
                        .setAllTimesSec(attributes.lastModifiedTime().to(TimeUnit.SECONDS));
                } else {
                    stat.setMode(NodeType.FILE, m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8])
                        .setAllTimesSec(attributes.lastModifiedTime().to(TimeUnit.SECONDS))
                        .size(attributes.size());
                }
            } else {
                if (attributes.isDirectory()) {
                    stat.setMode(NodeType.DIRECTORY, true, true, true, true, false, true, true, false, true)
                        .setAllTimesSec(attributes.lastModifiedTime().to(TimeUnit.SECONDS));
                } else {
                    stat.setMode(NodeType.FILE, true, true, false, true, false, false, true, false, false)
                        .setAllTimesSec(attributes.lastModifiedTime().to(TimeUnit.SECONDS))
                        .size(attributes.size());
                }
            }
            return 0;
        } catch (NoSuchFileException e) {
            if (e.getMessage().startsWith("ignore apple double file:")) {
logger.log(Level.DEBUG, e.getMessage());
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
    public int fgetattr(final String path, final StatWrapper stat, final FileInfoWrapper info)
    {
logger.log(Level.DEBUG, "fgetattr: " + path);
        return getattr(path, stat);
    }

    @Override
    public int mkdir(final String path, final ModeWrapper mode) {
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
    public int open(final String path, final FileInfoWrapper info) {
logger.log(Level.DEBUG, "open: " + path);
        try {
            Set<OpenOption> options = new HashSet<>();
            options.add(StandardOpenOption.READ);
            SeekableByteChannel channel = fileSystem.provider().newByteChannel(fileSystem.getPath(path), options);
            long fh = fileHandle.incrementAndGet();
            fileHandles.put(fh, channel);
            info.fh(fh);

            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    /** why not defined? */
    private static final int O_NONBLOCK = 04000;

    @Override
    public int read(final String path, final ByteBuffer buffer, final long size, final long offset, final FileInfoWrapper info) {
logger.log(Level.DEBUG, "read: " + path + ", " + offset + ", " + size + ", " + info.fh());
        try {
            if (fileHandles.containsKey(info.fh())) {
                SeekableByteChannel channel = fileHandles.get(info.fh());
                if (info.nonseekable()) {
                    assert offset == channel.position();
                } else {
                    channel.position(offset);
                }
                int n = channel.read(buffer);
                if (n > 0) {
                    if ((info.flags() & O_NONBLOCK) != 0) {
                        assert n <= 0 || n == size;
                    } else {
                        int c;
                        while (n < size) {
                            if ((c = channel.read(buffer)) <= 0)
                                break;
                            n += c;
                        }
                    }
logger.log(Level.DEBUG, "read: " + n);
                    return n;
                } else {
logger.log(Level.DEBUG, "read: 0");
                    return 0; // we did not read any bytes
                }
            } else {
                return -ErrorCodes.EEXIST();
            }
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int readdir(final String path, final DirectoryFiller filler) {
logger.log(Level.DEBUG, "readdir: " + path);
        try {
            fileSystem.provider().newDirectoryStream(fileSystem.getPath(path), p -> true)
                .forEach(p -> {
                    try {
                        filler.add(Util.toFilenameString(p));
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
    public int rename(final String path, final String newName) {
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
    public int rmdir(final String path) {
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
    public int truncate(final String path, final long offset) {
logger.log(Level.DEBUG, "truncate: " + path);
        // TODO
        return -ErrorCodes.ENOSYS();
    }

    @Override
    public int unlink(final String path) {
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
    public int write(final String path,
                     final ByteBuffer buf,
                     final long size,
                     final long offset,
                     final FileInfoWrapper info) {
logger.log(Level.DEBUG, "write: " + path + ", " + offset + ", " + size + ", " + info.fh());
        try {
            if (fileHandles.containsKey(info.fh())) {
                SeekableByteChannel channel = fileHandles.get(info.fh());
                if (!info.append() && !info.nonseekable()) {
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
                }
                int n = channel.write(buf);
                if (n > 0) {
                    if ((info.flags() & O_NONBLOCK) != 0) {
                        assert n <= 0 || n == size;
                    } else {
                        int c;
                        while (n < size) {
                            if ((c = channel.write(buf)) <= 0) {
                                break;
                            }
                            n += c;
                        }
                    }
                }
                return n;
            } else {
                return -ErrorCodes.EEXIST();
            }
        } catch (NonWritableChannelException e) {
logger.log(Level.TRACE, "NonWritableChannelException: unmounting?");
            return -ErrorCodes.EIO();
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int statfs(final String path, final StatvfsWrapper stat) {
logger.log(Level.TRACE, "statfs: " + path);
        try {
            FileStore fileStore = fileSystem.getFileStores().iterator().next();
//logger.log(Level.INFO, "total: " + fileStore.getTotalSpace());
//logger.log(Level.INFO, "free: " + fileStore.getUsableSpace());

            long blockSize = 512;

            long total = fileStore.getTotalSpace() / blockSize;
            long free = fileStore.getUsableSpace() / blockSize;
            long used = total - free;

            stat.bavail(used);
            stat.bfree(free);
            stat.blocks(total);
            stat.bsize(blockSize);
            stat.favail(-1);
            stat.ffree(-1);
            stat.files(-1);
            stat.frsize(1);

            return 0;
        } catch (IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }

    @Override
    public int release(final String path, final FileInfoWrapper info) {
logger.log(Level.DEBUG, "release: " + path);
        try {
            if (fileHandles.containsKey(info.fh())) {
                Channel channel = fileHandles.get(info.fh());
                channel.close();
                return 0;
            } else {
                return -ErrorCodes.EEXIST();
            }
        } catch (IOException e) {
logger.log(Level.INFO, e);
            return -ErrorCodes.EIO();
        } finally {
            fileHandles.remove(info.fh());
        }
    }

    @Override
    public int chmod(String path, ModeWrapper mode) {
logger.log(Level.DEBUG, "chmod: " + path);
        try {
            if (fileSystem.provider().getFileStore(fileSystem.getPath(path)).supportsFileAttributeView(PosixFileAttributeView.class)) {
                PosixFileAttributeView attrs = fileSystem.provider().getFileAttributeView(fileSystem.getPath(path), PosixFileAttributeView.class);
                attrs.setPermissions(FuseJnaFuse.modeToPermissions(mode.mode()));
                return 0;
            } else {
                return -Errno.EAFNOSUPPORT.ordinal();
            }
        } catch (Exception e) {
logger.log(Level.ERROR, e.getMessage(), e);
            return -ErrorCodes.EIO();
        }
    }
}
