load("@rules_java//java:defs.bzl", "java_library")

# EE8 (javax.servlet) hand-written variants of the canonical jakarta
# FakeHttpServletRequest/Response. They keep the same FQDN (package
# com.google.gerrit.util.http.testutil); only one servlet flavour is ever on a
# classpath at a time. Consumed by //javatests/.../testutil:testutil-ee8 in
# place of the canonical fakes, which are excluded from the to_javax transform
# because their Servlet-6 method set diverges (getServletConnection returns a
# jakarta-only type, etc.).
java_library(
    name = "overlay",
    testonly = True,
    srcs = [
        "FakeHttpServletRequest.java",
        "FakeHttpServletResponse.java",
    ],
    visibility = ["//javatests/com/google/gerrit/util/http/testutil:__pkg__"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:jgit",
        "//lib:servlet-api-ee8",
        "//lib/errorprone:annotations",
    ],
)
