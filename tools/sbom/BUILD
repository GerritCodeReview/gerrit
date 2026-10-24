load("@rules_python//python:defs.bzl", "py_binary", "py_library")

package(default_visibility = ["//visibility:public"])

py_library(
    name = "sbom_from_graph_lib",
    srcs = ["sbom_from_graph.py"],
    # Put this package on sys.path so siblings import it as
    # "sbom_from_graph" rather than "tools.sbom.sbom_from_graph".
    imports = ["."],
)

py_binary(
    name = "sbom_from_graph",
    srcs = ["sbom_from_graph.py"],
    main = "sbom_from_graph.py",
    deps = [":sbom_from_graph_lib"],
)

exports_files([
    ".trivyignore",
    "trivy_scan.sh",
])
