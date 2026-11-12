"""Building zip archives in genrules with the JDK's jar tool.

zip(1) and unzip(1) are missing on minimal remote executors, the JDK is not.

Usage in a genrule (or genrule2) that runs in the exec configuration:

    genrule2(
        ...
        cmd = " && ".join([
            JAR_SETUP,
            "cd somedir",
            JAR_CREATE + " --file $$ROOT/$@ .",
        ]),
        toolchains = JAR_TOOLCHAINS,
    )
"""

# The host (exec) runtime, so that jar matches the machine running the action.
# current_java_runtime would resolve for the target platform instead.
JAR_TOOLCHAINS = ["@bazel_tools//tools/jdk:current_host_java_runtime"]

# $(JAVABASE) can be relative to the execroot: define $JAR before any cd.
JAR_SETUP = "JAR=$$(cd $(JAVABASE) && pwd)/bin/jar"

# Flags shared by every archive creation, also usable outside of genrules (e.g.
# in ctx.actions.run_shell). The fixed date keeps archives reproducible. It
# cannot be earlier than 1980-01-01T00:00:02Z, the epoch of the zip format with
# a 2s granularity.
JAR_FLAGS = "--create --no-manifest --date=1980-01-01T00:00:02Z"

# For genrules only: $$ is the genrule escape for $.
JAR_CREATE = "$$JAR " + JAR_FLAGS
