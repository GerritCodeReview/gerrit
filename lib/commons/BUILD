load("@rules_java//java:defs.bzl", "java_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "codec",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:commons_codec_commons_codec"],
)

java_library(
    name = "compress",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_apache_commons_commons_compress"],
    runtime_deps = [":io"],
)

java_library(
    name = "lang3",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_apache_commons_commons_lang3"],
)

java_library(
    name = "net",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:commons_net_commons_net"],
)

java_library(
    name = "dbcp2",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_apache_commons_commons_dbcp2"],
    runtime_deps = [
        ":pool2",
        # commons-logging is excluded; jcl-over-slf4j provides its API.
        "//lib/log:jcl-over-slf4j",
    ],
)

java_library(
    name = "pool2",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:org_apache_commons_commons_pool2"],
)

java_library(
    name = "text",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = ["//visibility:public"],
    exports = ["@external_deps//:org_apache_commons_commons_text"],
)

java_library(
    name = "validator",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:commons_validator_commons_validator"],
)

java_library(
    name = "io",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@external_deps//:commons_io_commons_io"],
)
