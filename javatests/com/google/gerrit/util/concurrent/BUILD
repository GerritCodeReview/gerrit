load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "concurrent_tests",
    srcs = glob(["**/*.java"]),
    deps = [
	"@guava//jar",
        "//java/com/google/gerrit/util/concurrent",
        "//lib:junit",
        "//lib:servlet-api-without-neverlink",
        "//lib/truth",
    ],
)
