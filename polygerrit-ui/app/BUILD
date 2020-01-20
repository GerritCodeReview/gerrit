load("@io_bazel_rules_closure//closure:defs.bzl", "closure_js_binary", "closure_js_library", "closure_js_test")
load("//tools/bzl:genrule2.bzl", "genrule2")
load("//tools/bzl:js.bzl", "bower_component_bundle")
load(":rules.bzl", "polygerrit_bundle")

package(default_visibility = ["//visibility:public"])

polygerrit_bundle(
    name = "polygerrit_ui",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "bower_components/**",
            "test/**",
            "**/*_test.html",
        ],
    ),
    outs = ["polygerrit_ui.zip"],
    app = "elements/gr-app.html",
)

bower_component_bundle(
    name = "test_components",
    testonly = True,
    deps = [
        "//lib/js:iron-test-helpers",
        "//lib/js:test-fixture",
        "//lib/js:web-component-tester",
        "//polygerrit-ui:polygerrit_components.bower_components",
    ],
)

filegroup(
    name = "pg_code",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "bower_components/**",
        ],
    ),
)

filegroup(
    name = "pg_code_without_test",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "bower_components/**",
            "**/*_test.html",
            "test/**",
            "samples/**",
        ],
    ),
)

genrule2(
    name = "pg_code_zip",
    srcs = [":pg_code"],
    outs = ["pg_code.zip"],
    cmd = " && ".join([
        ("tar -hcf- $(locations :pg_code) |" +
         " tar --strip-components=2 -C $$TMP/ -xf-"),
        "cd $$TMP",
        "TZ=UTC",
        "export TZ",
        "find . -exec touch -t 198001010000 '{}' ';'",
        "zip -rq $$ROOT/$@ *",
    ]),
)

sh_test(
    name = "wct_test",
    size = "enormous",
    srcs = ["wct_test.sh"],
    data = [
        "test/common-test-setup.html",
        "test/index.html",
        ":pg_code.zip",
        ":test_components.zip",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)

# TODO(taoalpha): alias to `npm run eslint` or just remove once CI moved to npm
sh_test(
    name = "lint_test",
    size = "large",
    srcs = ["lint_test.sh"],
    data = [
        ".eslintrc.json",
        ":pg_code",
    ],
    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)

sh_test(
    name = "polylint_test",
    size = "large",
    srcs = ["polylint_test.sh"],
    data = [
        "polymer.json",
        ":pg_code_without_test",
        "//polygerrit-ui:polygerrit_components.bower_components.zip",
    ],

    # Should not run sandboxed.
    tags = [
        "local",
        "manual",
    ],
)

sh_test(
    name = "closure_test",
    size = "enormous",
    srcs = ["run_closure_test.sh"],
    args = ["skip"],
    tags = [
        "local",
        "template",
    ],
    data = [
        ":closure_test_bin",
    ],
)

closure_js_binary(
    name = "closure_test_bin",
    compilation_level = "SIMPLE_OPTIMIZATIONS",
    defs = [
        "--polymer_version=2",
        "--jscomp_off=duplicate",
    ],
    language = "ECMASCRIPT_2017",
    deps = ["closure_test_lib"],
    dependency_mode = "PRUNE_LEGACY",
)

closure_js_library(
    name = "closure_test_lib",
    srcs = glob(["types/*.js"]) + [":pg_all_js"],
    convention = "GOOGLE",
    suppress = [
        "JSC_UNKNOWN_EXPR_TYPE",
        "JSC_INEXISTENT_PROPERTY",
        "JSC_STRICT_INEXISTENT_PROPERTY",
        "JSC_POSSIBLE_INEXISTENT_PROPERTY",
        "JSC_IMPLICITLY_NULLABLE_JSDOC",
        "JSC_TYPE_MISMATCH",
        "JSC_ILLEGAL_PROPERTY_CREATION",
        "JSC_WRONG_ARGUMENT_COUNT",
        "JSC_ILLEGAL_PROPERTY_ACCESS",
        "JSC_INVALID_OPERAND_TYPE",
        "JSC_UNRECOGNIZED_TYPE_ERROR",
        "JSC_HIDDEN_SUPERCLASS_PROPERTY",
        "JSC_NULLABLE_RETURN_WITH_NAME",
        "JSC_INEXISTENT_PARAM",
        "JSC_OPTIONAL_ARG_AT_END",
        "JSC_UNUSED_PRIVATE_PROPERTY",
        "JSC_UNUSED_LOCAL_ASSIGNMENT",
    ],
    deps = [
        # TODO: should be replaced by polymer 2 if exists
        "//lib/polymer_externs:polymer_closure",
        "@io_bazel_rules_closure//closure/library",
    ],
)

filegroup(
    name = "pg_code_without_test_and_type",
    srcs = glob(
        [
            "**/*.html",
            "**/*.js",
        ],
        exclude = [
            "bower_components/**",
            "node_modules/**",
            "test/**",
            "externs/**",
            "closure_test_srcs/**",
            "samples/**",
            "types/**",
            "**/*_test.html",
        ],
    ),
)

genrule2(
    name = "pg_all_js",
    srcs = [":pg_code_without_test_and_type"],
    outs = ["gr-app_all_code_test.js"],
    cmd = " && ".join([
        "$(location closure_test_srcs/closure_test.sh) $$ROOT/$@ $(locations :pg_code_without_test_and_type)"
    ]),
    tools = [
        "closure_test_srcs/closure_test.sh",
        "closure_test_srcs/closure_test.js",
    ],
)