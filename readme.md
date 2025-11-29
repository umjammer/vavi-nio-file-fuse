[![Release](https://jitpack.io/v/umjammer/vavi-nio-file-fuse.svg)](https://jitpack.io/#umjammer/vavi-nio-file-fuse)
[![Java CI](https://github.com/umjammer/vavi-nio-file-fuse/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-nio-file-fuse/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-nio-file-fuse/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/umjammer/vavi-nio-file-fuse/actions/workflows/codeql-analysis.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)

# vavi-nio-file-fuse

🌐 mount the world!

integrated fuse filesystem mounter. you can mount any java jsr-203 file system using this.<br/>
this is an api and implementations are provided as SPI also.

### Providers

| name     | list | upload | download | copy | move | rm | mkdir | mount | umount | backend                                              |
|----------|:----:|:------:|:--------:|:----:|:----:|:--:|:-----:|:-----:|:------:|------------------------------------------------------|
| javafs   |  ✅   |   ✅    |    ✅     |  ✅   |  ✅   | ✅  |   ✅   |   ✅   |   ✅    | [javafs](https://github.com/umjammer/javafs)         |
| fuse-jna |  ✅   |   ✅    |    ✅     |  ✅   |  ✅   | ✅  |   ✅   |   ✅   |   ✅    | [fuse-jna](https://github.com/EtiennePerot/fuse-jna) |
| jnr-fuse |  ✅   |   ✅    |    ✅     |  ✅   |  ✅   | ✅  |   ✅   |   ✅   |   🚧   | [jnr-fuse](https://github.com/SerCeMan/jnr-fuse)     |

## Install

### maven

 * https://jitpack.io/#umjammer/vavi-nio-file-fuse

## Usage

```java
    URI uri = URI.create("googledrive:///?id=you@gmail.com");
    FileSystems fs = FileSystems.newFileSystem(uri, Collections.emptyMap());

    Fuse.getFuse().mount(fs, "/your/mout/point", Collections.emptyMap());
```

### jdk argument

* `-Djna.library.path=/usr/local/lib`

### System property

* `vavi.net.fuse.FuseProvider.class` ... set a provider class name when you use the argument-less factory method 
  - `vavi.net.fuse.javafs.JavaFSFuseProvider`
  - `vavi.net.fuse.jnrfuse.JnrFuseFuseProvider`
  - `vavi.net.fuse.fusejna.FuseJnaFuseProvider`

### Workaround

 * if the test goes wrong, update macfuse and reboot the mac
 * `Path#toFile()` UnsupportedOperationException ... mount fs as fuse then `toFile`

## References

 * https://github.com/cryptomator/jfuse (JEP-454)

## TODO

 * ~~https://github.com/cryptomator/fuse-nio-adapter~~
 * spotlight
   * https://stackoverflow.com/a/2335565
   * https://wiki.samba.org/index.php/Spotlight_with_Elasticsearch_Backend
   * https://gitlab.com/samba-team/samba/-/blob/master/source3/rpcclient/cmd_spotlight.c
 * fusejnr umount problem
 * libfuse3 ?
