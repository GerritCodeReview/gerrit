def _repo_impl(ctx):
    result = ctx.execute(
        ["python3", ctx.path(Label("//:setup.py"))],
        timeout = 180,
    )
    if result.stdout:
        print(result.stdout)
    if result.return_code != 0:
        fail(result.stderr)
    ctx.file("BUILD", """
package(default_visibility = ["//visibility:public"])
toolchain_type(name = "type")
filegroup(name = "data", srcs = [])
toolchain(name = "toolchain", toolchain = ":data", toolchain_type = ":type")
""")

_repo = repository_rule(implementation = _repo_impl)

def _setup_impl(module_ctx):
    _repo(name = "setup_data")

setup = module_extension(implementation = _setup_impl)
