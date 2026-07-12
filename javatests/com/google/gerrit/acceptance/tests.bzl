load("@com_googlesource_gerrit_bazlets//tools:flavour.bzl", "flavoured_tests")
load("//tools/bzl:junit.bzl", "junit_tests")

def acceptance_tests(
        group,
        deps = [],
        labels = [],
        vm_args = ["-Xmx256m"],
        canonical = None,
        **kwargs):
    """Acceptance test group; `canonical` makes it a servlet-flavour twin pair.

    With `canonical = "ee11"` the group's sources are jakarta.servlet and the
    runnable default `<group>` suite is generated through to_javax, mirroring
    flavoured_tests: the servlet boundary the group exercises runs in both
    flavours. Without it the group is a plain single suite, which is only
    valid for flavour-neutral sources (no servlet imports).
    """
    common = dict(
        deps = deps + [
            "//java/com/google/gerrit/acceptance:lib",
        ],
        tags = labels + [
            "acceptance",
            "slow",
        ],
        size = "large",
        jvm_flags = vm_args,
    )
    common.update(kwargs)
    if canonical:
        flavoured_tests(
            name = group,
            canonical = canonical,
            **common
        )
    else:
        junit_tests(
            name = group,
            **common
        )
