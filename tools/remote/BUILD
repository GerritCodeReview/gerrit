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

platform(
    name = "ubuntu-24-04",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    exec_properties = {
        "ISA": "x86-64",
        "container-image": "docker://ubuntu:24.04",
        "OSFamily": "linux",
    },
)

platform(
    name = "host",
    exec_properties = {
        "no-remote-exec": "true",
    },
    parents = ["@platforms//host"],
)
