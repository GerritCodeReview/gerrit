load("@io_bazel_rules_go//go:def.bzl", "go_library", "go_test")
load("@bazel_gazelle//:def.bzl", "gazelle")
load("@io_bazel_rules_webtesting//web:go.bzl", "go_web_test_suite")

# gazelle:prefix github.com/gerritcodereview/webtest
gazelle(name = "gazelle")

go_web_test_suite(
    name = "webtest_test",
    srcs = ["webtest_test.go"],
    browsers = [
        "@io_bazel_rules_webtesting//browsers:chromium-local",
    ],
    local = True,
    deps = [
        "@com_github_tebeka_selenium//:go_default_library",
        "@io_bazel_rules_webtesting//go/webtest:go_default_library",
    ],
)
