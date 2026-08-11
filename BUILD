load("@com_googlesource_gerrit_bazlets//tools:genrule2.bzl", "genrule2")
load("@gerrit_api_version//:version.bzl", "GERRIT_API_VERSION")
load("@npm//:defs.bzl", "npm_link_all_packages")
load("@rules_jvm_external//:defs.bzl", "maven_export")
load("//tools/bzl:pkg_war.bzl", "pkg_war")

npm_link_all_packages(name = "node_modules")

package(default_visibility = ["//visibility:public"])

genrule(
    name = "gen_version",
    outs = ["version.txt"],
    cmd = ("(cat bazel-out/volatile-status.txt bazel-out/stable-status.txt | " +
           "grep STABLE_BUILD_GERRIT_LABEL | cut -d ' ' -f 2) > $@ || echo 'UNKNOWN' > $@"),
    stamp = 1,
)

genrule(
    name = "LICENSES",
    srcs = ["//Documentation:licenses.txt"],
    outs = ["LICENSES.txt"],
    cmd = "cp $< $@",
)

pkg_war(
    name = "gerrit",
    ui = "polygerrit",
)

pkg_war(
    name = "headless",
    ui = None,
)

pkg_war(
    name = "release",
    context = ["//plugins:core"],
    doc = True,
)

# Publish gerrit-war to Maven Central via rules_jvm_external's maven_export. A WAR
# is not a jar, so this uses maven_export with `target` (the release.war archive)
# rather than a java library; MavenPublisher uploads it with the .war extension.
# The WAR contents are unchanged -- only its publishing plumbing moves off mvn.py.
maven_export(
    name = "gerrit-war",
    maven_coordinates = "com.google.gerrit:gerrit-war:%s" % GERRIT_API_VERSION,
    pom_template = "//tools/maven:gerrit-war_pom.xml",
    tags = ["no-javadocs"],
    target = ":release.war",
)

pkg_war(
    name = "withdocs",
    doc = True,
)

# The `api` zip is a self-contained download bundle (the "API jars" archive), a
# distinct distribution channel from Maven Central. It intentionally keeps the fat
# `*_deploy.jar` API jars so the archive is self-contained; the Maven-Central
# migration to thin, POM-declared jars (rules_jvm_external) is out of scope for it.
API_DEPS = [
    "//java/com/google/gerrit/acceptance:framework_deploy.jar",
    "//java/com/google/gerrit/acceptance:libframework-lib-src.jar",
    "//java/com/google/gerrit/extensions:extension-api_deploy.jar",
    "//java/com/google/gerrit/extensions:libapi-src.jar",
    "//plugins:plugin-api_deploy.jar",
    "//plugins:plugin-api-sources_deploy.jar",
]

API_JAVADOC_DEPS = [
    "//java/com/google/gerrit/acceptance:framework-javadoc",
    "//java/com/google/gerrit/extensions:extension-api-javadoc",
    "//plugins:plugin-api-javadoc",
]

genrule2(
    name = "api",
    testonly = True,
    srcs = API_DEPS + API_JAVADOC_DEPS,
    outs = ["api.zip"],
    cmd = " && ".join([
        "cp $(SRCS) $$TMP",
        "cd $$TMP",
        "zip -qr $$ROOT/$@ .",
    ]),
)

genrule2(
    name = "api-skip-javadoc",
    testonly = True,
    srcs = API_DEPS,
    outs = ["api-skip-javadoc.zip"],
    cmd = " && ".join([
        "cp $(SRCS) $$TMP",
        "cd $$TMP",
        "zip -qr $$ROOT/$@ .",
    ]),
)
