# Node.js toolchain for minimal remote executors

The nodejs.org binaries link against `libatomic.so.1`, which minimal executor
images (e.g. `ubuntu:24.04`) lack, so every Node.js action fails with
`error while loading shared libraries: libatomic.so.1`.

This directory provides a toolchain whose `node` is a self-extracting
executable: a `/bin/sh` stub plus a tar with `node` and `libatomic.so.1`. It
unpacks to a temp dir and `exec`s `node` with `LD_LIBRARY_PATH` set. It should
only be registered for remote builds; local builds keep the default toolchain.

## FAQ

**Why not a wrapper with runfiles?**
`js_binary` stages only the toolchain's `node` file; the runfiles of that
target are not propagated, so the library never reaches the executor (tested).
See https://github.com/bazel-contrib/rules_nodejs/pull/3911 for a possible
future fix, after which this can become a plain wrapper.

**Why not patchelf?**
`RPATH` still needs the library staged next to `node`, and `--remove-needed`
is unsafe: `node` imports `__atomic_compare_exchange`.

**Why a custom repository rule (`libatomic.bzl`)?**
The library comes from the Ubuntu `.deb`, whose `data.tar.zst` is nested in an
`ar` archive. `http_archive` only unpacks the outer layer, and `patch_cmds`
would need `zstd` on the host.

**Why no caching in the stub?**
Executors are fresh for every action.

## Updating

`libatomic`: change `urls` and `sha256` in `tools/rbe.MODULE.bazel`, using the
same Ubuntu release as the executor image.
