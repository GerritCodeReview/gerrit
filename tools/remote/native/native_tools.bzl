"""Native tools (git, ssh, ssh-keygen) from Ubuntu packages.

The remote executor image (ubuntu:24.04) does not ship git or OpenSSH. The
packages are fetched hermetically by rules_distroless (see rbe.MODULE.bazel)
and pruned here down to what is not already part of the image: glibc and the
other libraries that the base image provides are dropped, so the tools run
against the glibc of the executor.
"""

# Libraries that are part of the ubuntu:24.04 base image (or only needed by
# the perl scripts shipped with git). Everything else the tools link against is
# bundled.
_EXCLUDED_LIBS = [
    # glibc
    "ld-linux*",
    "libc.so*",
    "libc_malloc_debug*",
    "libm.so*",
    "libmvec*",
    "libdl.so*",
    "libpthread*",
    "librt.so*",
    "libresolv*",
    "libutil.so*",
    "libanl*",
    "libBrokenLocale*",
    "libnss_*",
    "libnsl*",
    "libthread_db*",
    "libSegFault*",
    "libmemusage*",
    "libpcprofile*",
    "libcrypt.so*",
    "gconv",
    "audit",
    # gcc
    "libgcc_s*",
    "libstdc++*",
    # zlib1g, libzstd1, liblzma5, libbz2-1.0, libpcre2-8-0, libselinux1,
    # libacl1, libmd0
    "libz.so*",
    "libzstd*",
    "liblzma*",
    "libbz2*",
    "libpcre2-8*",
    "libselinux*",
    "libacl*",
    "libmd.so*",
    # perl, which is only needed by git's perl scripts (git add -p, ...).
    "libperl*",
    "perl*",
    "libgdbm*",
    "libdb-5*",
    "libdb5*",
]

_BINARIES = ["git", "ssh", "ssh-keygen"]

_ASSEMBLE = """\
set -eu
out=$1; shift
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
# Packages without anything under ./usr make tar fail, which is fine.
for deb in "$@"; do tar -xzf "$deb" -C "$tmp" ./usr 2>/dev/null || true; done

lib=usr/lib/x86_64-linux-gnu
mkdir -p "$out/usr/bin" "$out/$lib" "$out/usr/share/git-core" "$out/bin"
for b in {binaries}; do cp -a "$tmp/usr/bin/$b" "$out/usr/bin/"; done
cp -a "$tmp/usr/lib/git-core" "$out/usr/lib/"
cp -a "$tmp/usr/share/git-core/templates" "$out/usr/share/git-core/"
cp -a "$tmp/$lib/." "$out/$lib/"
(cd "$out/$lib" && rm -rf {excluded})
# Symlinks into what was just dropped (e.g. git-core/* -> perl scripts).
find "$out" -type l ! -exec test -e {{}} \\; -delete

# Shims: the directory of the shims is meant to be put on PATH. They find the
# tree through their own location and set up what the relocated binaries need.
for b in {binaries}; do
  cat > "$out/bin/$b" <<'SHIM'
#!/bin/sh
root=$(cd "$(dirname "$0")/.." && pwd -P)
LD_LIBRARY_PATH="$root/usr/lib/x86_64-linux-gnu${{LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}}"
export LD_LIBRARY_PATH
GIT_EXEC_PATH="$root/usr/lib/git-core"
GIT_TEMPLATE_DIR="$root/usr/share/git-core/templates"
export GIT_EXEC_PATH GIT_TEMPLATE_DIR
SHIM
  if [ "$b" = ssh ]; then
    # The build directories of the remote executor are a FUSE file system that
    # reports every file as 0666, which makes ssh refuse private keys given
    # with -i ("UNPROTECTED PRIVATE KEY FILE"). Use private copies from /tmp.
    cat >> "$out/bin/$b" <<'SSHFIX'
tmpdir=
cleanup() {{
  [ -z "$tmpdir" ] || rm -rf "$tmpdir"
}}
trap cleanup EXIT
# Sets key to a private copy of key if it is accessible by others.
fix_key() {{
  if [ -f "$key" ] && [ -n "$(find "$key" -perm /077)" ]; then
    [ -n "$tmpdir" ] || tmpdir=$(mktemp -d /tmp/ssh-keys.XXXXXX)
    copy="$tmpdir/$n"
    cp "$key" "$copy" && chmod 600 "$copy" && key=$copy
  fi
}}
n=$#
while [ "$n" -gt 0 ]; do
  arg=$1; shift; n=$((n - 1))
  # Both "-i key" and "-ikey" are in use.
  case "$arg" in
    -i)
      if [ "$n" -gt 0 ]; then
        key=$1; shift; n=$((n - 1))
        fix_key
        set -- "$@" -i "$key"
        continue
      fi
      ;;
    -i?*)
      key=${{arg#-i}}
      fix_key
      set -- "$@" "-i$key"
      continue
      ;;
  esac
  set -- "$@" "$arg"
done
"$root/usr/bin/ssh" "$@"
SSHFIX
  else
    echo "exec \\"\\$root/usr/bin/$b\\" \\"\\$@\\"" >> "$out/bin/$b"
  fi
  chmod +x "$out/bin/$b"
done
"""

def _native_tools_impl(ctx):
    out = ctx.actions.declare_directory(ctx.label.name)
    ctx.actions.run_shell(
        inputs = ctx.files.debs,
        outputs = [out],
        command = _ASSEMBLE.format(
            binaries = " ".join(_BINARIES),
            excluded = " ".join(_EXCLUDED_LIBS),
        ),
        arguments = [out.path] + [f.path for f in ctx.files.debs],
        mnemonic = "NativeTools",
        progress_message = "Assembling native tools %{label}",
    )
    return [DefaultInfo(files = depset([out]))]

native_tools = rule(
    implementation = _native_tools_impl,
    attrs = {
        # The content.tar.gz of every package, e.g. a rules_distroless package
        # closure (the :flat target is not used: it builds gawk from source,
        # which needs a C toolchain with libc headers on the executor).
        "debs": attr.label_list(allow_files = True, mandatory = True),
    },
)
