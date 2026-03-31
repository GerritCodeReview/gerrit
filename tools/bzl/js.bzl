load("@rules_shell//shell:sh_test.bzl", "sh_test")

def web_test_runner(name, srcs, data):
    """Creates a Web Test Runner test target.

    It can be used both for the main Gerrit js bundle, but also for plugins. So
    it should be extremely easy to add Web Test Runner test capabilities for new plugins.

    We are sharing one web-test-runner.config.mjs file. If you want to customize that, then
    consider using command line arguments that the config file can process, see
    the `root` argument for an example.

    Args:
      name: The name of the test rule.
      srcs: The shell script to invoke, where you can set command line
        arguments for Web Test Runner and its config.
      data: The bundle of JavaScript files with the tests included.
    """

    sh_test(
        name = name,
        size = "enormous",
        srcs = srcs,
        args = [
            "$(location //polygerrit-ui:web_test_runner_bin)",
            "$(location //polygerrit-ui:web-test-runner.config.mjs)",
        ],
        data = data + [
            "//polygerrit-ui:web_test_runner_bin",
            "//polygerrit-ui:web-test-runner.config.mjs",
        ],
        # Should not run sandboxed.
        tags = ["local", "manual"],
    )
