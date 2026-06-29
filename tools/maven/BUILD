load("@gerrit_api_version//:version.bzl", "GERRIT_API_VERSION")
load("//tools/maven:package.bzl", "maven_package")

maven_package(
    src = {
        "gerrit-acceptance-framework": "//java/com/google/gerrit/acceptance:libframework-lib-src.jar",
        "gerrit-extension-api": "//java/com/google/gerrit/extensions:libapi-src.jar",
        "gerrit-plugin-api": "//plugins:plugin-api-sources_deploy.jar",
        # EE10 (jakarta.servlet) flavour of the plugin API, for standalone EE10
        # plugin builds. Self-transitioning, so it stages alongside the EE8 one.
        "gerrit-plugin-api-ee10": "//plugins:plugin-api-ee10-sources",
    },
    doc = {
        "gerrit-acceptance-framework": "//java/com/google/gerrit/acceptance:framework-javadoc",
        "gerrit-extension-api": "//java/com/google/gerrit/extensions:extension-api-javadoc",
        "gerrit-plugin-api": "//plugins:plugin-api-javadoc",
        "gerrit-plugin-api-ee10": "//plugins:plugin-api-ee10-javadoc",
    },
    jar = {
        "gerrit-acceptance-framework": "//java/com/google/gerrit/acceptance:framework_deploy.jar",
        "gerrit-extension-api": "//java/com/google/gerrit/extensions:extension-api_deploy.jar",
        "gerrit-plugin-api": "//plugins:plugin-api_deploy.jar",
        "gerrit-plugin-api-ee10": "//plugins:plugin-api-ee10",
    },
    version = GERRIT_API_VERSION,
    war = {
        "gerrit-war": "//:release.war",
        # EE10 (jakarta.servlet) WAR, published side by side with the EE8 default.
        "gerrit-war-ee10": "//:release-ee10",
    },
)
