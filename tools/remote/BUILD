# RBE execution platform. Names the container Bazel actions run in on GCP
# RBE and BuildBuddy; the hermetic C++ toolchain (registered in
# MODULE.bazel) supplies the compiler, so this platform only declares the
# container image and OS/CPU constraints -- there is no generated CROSSTOOL.
platform(
    name = "platform",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    exec_properties = {
        "OSFamily": "Linux",
        "container-image": "docker://gcr.io/bazel-public/ubuntu2404@sha256:57bbaa84bec679736c53dcd1d326e8f835b3b9ce3e36c12a3c12a07eb59177d3",
    },
    visibility = ["//visibility:public"],
)
