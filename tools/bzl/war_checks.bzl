"""Reusable checks for WAR content guardrails."""

def war_jars_allowlist_test(name, war_jars_manifest, allowlist, **kwargs):
    """Checks that a WAR's normalized jar ID manifest matches an allowlist.

    Args:
        name: test name
        war_jars_manifest: label of the generated manifest, e.g. "//:release.war.jars.txt"
        allowlist: label of the checked-in allowlist file
        **kwargs: forwarded to sh_test
    """
    native.sh_test(
        name = name,
        srcs = ["//tools/bzl:diff_allowlist.sh"],
        args = [
            "$(location %s)" % allowlist,
            "$(location %s)" % war_jars_manifest,
        ],
        data = [
            allowlist,
            war_jars_manifest,
        ],
        **kwargs
    )
