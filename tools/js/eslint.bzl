load("@build_bazel_rules_nodejs//:index.bzl", "nodejs_test", "nodejs_binary")

def eslint(name, plugins, srcs, config, ignore, extensions=[".js"], data=[]):
    entry_point = "@npm//:node_modules/eslint/bin/eslint.js"
    bin_data = [
                                  "@npm//eslint:eslint",
                                  config,
                                  ignore
                                      ] + plugins + data
    common_templated_args = ["--ext",
                                      ",".join(extensions),
                                       "-c",
                                      "$$(rlocation $(rootpath {}))".format(config),
                                      "--ignore-path",
                                      "$$(rlocation $(rootpath {}))".format(ignore),
                                      ]
    nodejs_test(
            name = name+"_test",
            entry_point = entry_point,
            data = bin_data + srcs,
            templated_args = common_templated_args + [
                "--ignore-pattern",
                "*_test_require_patch.js",
                "--ignore-pattern",
                "*_test_loader.js",
                native.package_name()
            ]
          )

    nodejs_binary(
            name = name+"_bin",
            entry_point = "@npm//:node_modules/eslint/bin/eslint.js",
            data = bin_data,
            templated_args = common_templated_args + [
                "--ignore-pattern",
                "*_bin_require_patch.js",
                "--ignore-pattern",
                "*_bin_loader.js",
            ]
          )



