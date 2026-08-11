load("@gerrit_api_version//:version.bzl", "GERRIT_API_VERSION")

# POM templates consumed cross-package by the java_export/maven_export publish
# targets (e.g. //java/com/google/gerrit/extensions:gerrit-extension-api and the
# //:gerrit-war maven_export).
exports_files([
    "gerrit-extension-api_pom.xml",
    "gerrit-plugin-api_pom.xml",
    "gerrit-acceptance-framework_pom.xml",
    "gerrit-war_pom.xml",
])

# Single source of the release version for api.sh's direct JReleaser invocation:
# every artifact now publishes via rules_jvm_external `.publish` (no mvn.py, which
# used to pass -v), so api.sh reads this file to set jreleaser.project.version.
genrule(
    name = "api_version",
    outs = ["api_version.txt"],
    cmd = "echo %s > $@" % GERRIT_API_VERSION,
)
