load("//tools/bzl:jmh.bzl", "jmh_java_benchmarks")

jmh_java_benchmarks(
    name = "example-benchmarks",
    srcs = ["GitGetRefByPrefixBenchmark.java"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib:jgit",
        #  "//lib/commons:lang3",
    ],
)
