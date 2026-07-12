load("@gerrit_api_version//:version.bzl", "GERRIT_API_VERSION")
load("//tools/maven:package.bzl", "maven_package")

maven_package(
    src = {
        "gerrit-acceptance-framework": "//java/com/google/gerrit/acceptance:libframework-lib-src.jar",
        "gerrit-acceptance-framework-ee8": "//java/com/google/gerrit/acceptance:framework-ee8-sources",
        "gerrit-extension-api": "//java/com/google/gerrit/extensions:libapi-src.jar",
        "gerrit-plugin-api": "//plugins:plugin-api-sources_deploy.jar",
        # EE8 (javax.servlet) legacy flavour of the plugin API, for standalone
        # EE8 plugin builds during the deprecation window. Self-transitioning,
        # so it stages alongside the jakarta default.
        "gerrit-plugin-api-ee8": "//plugins:plugin-api-ee8-sources",
    },
    doc = {
        "gerrit-acceptance-framework": "//java/com/google/gerrit/acceptance:framework-javadoc",
        "gerrit-acceptance-framework-ee8": "//java/com/google/gerrit/acceptance:framework-ee8-javadoc",
        "gerrit-extension-api": "//java/com/google/gerrit/extensions:extension-api-javadoc",
        "gerrit-plugin-api": "//plugins:plugin-api-javadoc",
        "gerrit-plugin-api-ee8": "//plugins:plugin-api-ee8-javadoc",
    },
    jar = {
        "gerrit-acceptance-framework": "//java/com/google/gerrit/acceptance:framework_deploy.jar",
        "gerrit-acceptance-framework-ee8": "//java/com/google/gerrit/acceptance:framework-ee8",
        "gerrit-extension-api": "//java/com/google/gerrit/extensions:extension-api_deploy.jar",
        "gerrit-plugin-api": "//plugins:plugin-api_deploy.jar",
        "gerrit-plugin-api-ee8": "//plugins:plugin-api-ee8",
    },
    version = GERRIT_API_VERSION,
    war = {
        "gerrit-war": "//:release.war",
        # EE8 (javax.servlet) legacy WAR, published side by side with the
        # jakarta default for one release train.
        "gerrit-war-ee8": "//:release-ee8",
    },
)
