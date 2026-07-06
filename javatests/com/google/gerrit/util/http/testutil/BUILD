load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_java_library", "flavoured_twin_alias")

flavoured_twin_alias(
    name = "testutil",
    flavours = ["ee11"],
    visibility = ["//visibility:public"],
)

flavoured_java_library(
    name = "testutil-ee8",
    testonly = True,
    srcs = glob(["**/*.java"]),
    flavour = "ee8",
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api",
        "//lib/errorprone:annotations",
        "//lib/httpcomponents:httpclient",
    ],
)

# EE11 (jakarta.servlet) twin: the FakeHttpServlet{Request,Response} variants
# diverge from a mechanical transform (Servlet-6 method set), so this twin
# exports the hand-written ee11/ overlay instead of running to_jakarta.
# testutil-ee8's glob does not descend into ee11/ (own BUILD).
flavoured_java_library(
    name = "testutil-ee11",
    testonly = True,
    flavour = "ee11",
    visibility = ["//visibility:public"],
    exports = ["//javatests/com/google/gerrit/util/http/testutil/ee11:overlay"],
)
