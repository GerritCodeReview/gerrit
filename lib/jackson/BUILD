<<<<<<< PATCH SET (e78401 Migrate to log4j2)
package(default_visibility = ["//visibility:public"])

load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "jackson-annotations",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@jackson-annotations//jar"],
)

java_library(
    name = "jackson-core",
    data = ["//lib:LICENSE-Apache2.0"],
    visibility = [
        "//java/com/google/gerrit/acceptance:__pkg__",
        "//java/com/google/gerrit/elasticsearch:__pkg__",
        "//plugins:__pkg__",
    ],
    exports = ["@jackson-core//jar"],
)

java_library(
    name = "jackson-databind",
    data = ["//lib:LICENSE-Apache2.0"],
    exports = ["@jackson-databind//jar"],
)

=======
>>>>>>> BASE      (f8fd64 Merge branch 'stable-3.8')
