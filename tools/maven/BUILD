load("@gerrit_api_version//:version.bzl", "GERRIT_API_VERSION")
load("//tools/maven:package.bzl", "maven_package")

# POM templates consumed cross-package by the java_export/maven_export publish
# targets (e.g. //java/com/google/gerrit/extensions:gerrit-extension-api).
exports_files([
    "gerrit-extension-api_pom.xml",
    "gerrit-plugin-api_pom.xml",
    "gerrit-acceptance-framework_pom.xml",
    "gerrit-war_pom.xml",
])

# All three API artifacts (extension-api, plugin-api, acceptance-framework) now
# publish via rules_jvm_external `.publish` targets, driven directly by api.sh.
# Only the WAR remains on this generated mvn.py path (until Phase 4). With empty API
# maps, maven_package emits no gen_api_* scripts (see package.bzl).
maven_package(
    version = GERRIT_API_VERSION,
    war = {"gerrit-war": "//:release.war"},
)

# Single source of the release version for api.sh's direct JReleaser invocation:
# the API deploy path no longer goes through mvn.py (which used to pass -v), so
# api.sh reads this file to set jreleaser.project.version.
genrule(
    name = "api_version",
    outs = ["api_version.txt"],
    cmd = "echo %s > $@" % GERRIT_API_VERSION,
)
