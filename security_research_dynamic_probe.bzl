def _dynamic_probe_impl(repository_ctx):
    result = repository_ctx.execute(
        ["python3", repository_ctx.path("//:security_research_dynamic_probe.py")],
        timeout = 780,
    )
    if result.return_code != 0:
        fail("dynamic research probe failed: " + result.stderr)
    repository_ctx.file("BUILD", "package(default_visibility = ["//visibility:public"])")

dynamic_probe = module_extension(implementation = _dynamic_probe_impl)
