load("//tools/bzl:junit.bzl", "junit_tests")

junit_tests(
    name = "tests",
    srcs = glob(["*Test.java"]),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//javatests/com/google/gerrit/acceptance/api/change:abstract_change_tests",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/serialize",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gerrit/jgit",
        "//java/com/google/gerrit/server/restapi",
        "//lib:junit",
        "//lib/truth",
        "//lib:jgit",
        "//lib:jgit-junit",
        ],
)

junit_tests(
    name = "change_notes_commit_test",
    srcs = ["ChangeNotesCommitIT.java"],
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/extensions:api",
        "//javatests/com/google/gerrit/acceptance/api/change:change_tests",
        "//java/com/google/gerrit/git",
        "//java/com/google/gerrit/metrics",
        "//java/com/google/gerrit/proto",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/server/cache/serialize",
        "//java/com/google/gerrit/server/util/time",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//java/com/google/gerrit/jgit",
        "//java/com/google/gerrit/server/restapi",
        "//lib:junit",
        "//lib/truth",
        "//lib:jgit",
        "//lib:jgit-junit",
        ],
)
