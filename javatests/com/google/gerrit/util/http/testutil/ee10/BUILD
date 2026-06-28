load("@rules_java//java:defs.bzl", "java_library")

# EE10 (jakarta.servlet) hand-written variants of the canonical
# FakeHttpServletRequest/Response. They keep the same FQDN (package
# com.google.gerrit.util.http.testutil); only one servlet flavour is ever on a
# classpath at a time. Consumed by //javatests/.../testutil:testutil-ee10 in
# place of the canonical fakes, which are excluded from the to_jakarta transform
# because their Servlet-6 method set diverges (getServletConnection returns a
# jakarta-only type, etc.).
java_library(
    name = "fakes",
    testonly = True,
    srcs = [
        "FakeHttpServletRequest.java",
        "FakeHttpServletResponse.java",
    ],
    visibility = ["//javatests/com/google/gerrit/util/http/testutil:__pkg__"],
    deps = [
        "//java/com/google/gerrit/common:annotations",
        "//lib:guava",
        "//lib:jakarta-servlet-api",
        "//lib:jgit",
        "//lib/errorprone:annotations",
    ],
)
