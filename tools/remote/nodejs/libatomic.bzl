"""Fetches libatomic.so.1 from an Ubuntu package.

The Node.js binaries from nodejs.org are dynamically linked against
libatomic.so.1, which the remote execution image does not provide. See
//tools/remote/nodejs:nodejs_toolchain.
"""

def _libatomic_impl(rctx):
    # A .deb is an ar archive whose data.tar.* member holds the files.
    rctx.download_and_extract(
        url = rctx.attr.urls,
        sha256 = rctx.attr.sha256,
        type = "deb",
    )
    rctx.extract("data.tar.zst")
    rctx.file("BUILD.bazel", """\
exports_files(["usr/lib/x86_64-linux-gnu/libatomic.so.1.2.0"])
""")

libatomic = repository_rule(
    implementation = _libatomic_impl,
    attrs = {
        "sha256": attr.string(mandatory = True),
        "urls": attr.string_list(mandatory = True),
    },
)
