load("//:version.bzl", "GERRIT_VERSION")
load("//tools/maven:package.bzl", "maven_package")

MAVEN_REPOSITORY = "sonatype-nexus-staging"

URL = "https://oss.sonatype.org/content/repositories/snapshots" if GERRIT_VERSION.endswith("-SNAPSHOT") else "https://oss.sonatype.org/service/local/staging/deploy/maven2"

maven_package(
    src = {
        "gerrit-acceptance-framework": "//java/com/google/gerrit/acceptance:libframework-lib-src.jar",
        "gerrit-extension-api": "//java/com/google/gerrit/extensions:libapi-src.jar",
        "gerrit-plugin-api": "//plugins:plugin-api-sources_deploy.jar",
    },
    doc = {
        "gerrit-acceptance-framework": "//java/com/google/gerrit/acceptance:framework-javadoc",
        "gerrit-extension-api": "//java/com/google/gerrit/extensions:extension-api-javadoc",
        "gerrit-plugin-api": "//plugins:plugin-api-javadoc",
    },
    jar = {
        "gerrit-acceptance-framework": "//java/com/google/gerrit/acceptance:framework_deploy.jar",
        "gerrit-extension-api": "//java/com/google/gerrit/extensions:extension-api_deploy.jar",
        "gerrit-plugin-api": "//plugins:plugin-api_deploy.jar",
    },
    repository = MAVEN_REPOSITORY,
    url = URL,
    version = GERRIT_VERSION,
    war = {"gerrit-war": "//:release"},
)
