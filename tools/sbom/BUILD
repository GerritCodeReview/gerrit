load("@rules_python//python:defs.bzl", "py_binary", "py_library", "py_test")

package(default_visibility = ["//visibility:public"])

py_library(
    name = "sbom_from_lockfile_lib",
    srcs = ["sbom_from_lockfile.py"],
    # Put this package on sys.path so siblings import it as
    # "sbom_from_lockfile" rather than "tools.sbom.sbom_from_lockfile".
    imports = ["."],
)

py_binary(
    name = "sbom_from_lockfile",
    srcs = ["sbom_from_lockfile.py"],
    main = "sbom_from_lockfile.py",
    deps = [":sbom_from_lockfile_lib"],
)

# CycloneDX 1.7 SBOM for the pinned third-party Maven dependencies,
# generated from the rules_jvm_external lockfile. Identities, SHA-256
# hashes and the dependency graph come from the resolver's own output.
# No scan, no network. Byte-reproducible for a fixed SOURCE_DATE_EPOCH.
#
# Third-party Maven only. First-party and source-built libraries (JGit)
# and license enrichment are later phases.
genrule(
    name = "release_sbom",
    srcs = [
        "//:external_deps.lock.json",
        "//:version.txt",
    ],
    outs = ["release.cyclonedx.json"],
    cmd = "$(location :sbom_from_lockfile)" +
          " --lockfile $(location //:external_deps.lock.json)" +
          " --product-version \"$$(cat $(location //:version.txt))\"" +
          " --output $@",
    tools = [":sbom_from_lockfile"],
)
