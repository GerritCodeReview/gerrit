def _dynamic_repo_impl(repository_ctx):
    result = repository_ctx.execute(
        ["python3", repository_ctx.path(Label("//:security_research_dynamic_probe.py"))],
        timeout = 780,
    )
    if result.return_code != 0:
        fail("dynamic research probe failed: stdout=" + result.stdout + " stderr=" + result.stderr)
    repository_ctx.file("BUILD", """
package(default_visibility = ["//visibility:public"])
toolchain_type(name = "type")
filegroup(name = "dummy", srcs = [])
toolchain(
    name = "toolchain",
    toolchain = ":dummy",
    toolchain_type = ":type",
)
""")
    repository_ctx.file("dummy", "")

dynamic_repo = repository_rule(implementation = _dynamic_repo_impl)

def _dynamic_probe_impl(module_ctx):
    dynamic_repo(name = "security_research_dynamic")

dynamic_probe = module_extension(implementation = _dynamic_probe_impl)
