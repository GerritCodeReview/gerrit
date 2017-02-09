load("//:version.bzl", "GERRIT_VERSION")
load("//tools/maven:package.bzl", "maven_package")

MAVEN_REPOSITORY = "sonatype-nexus-staging"

URL = "https://oss.sonatype.org/service/local/staging/deploy/maven2" \
    if GERRIT_VERSION.endswith('-SNAPSHOT') \
    else 'https://oss.sonatype.org/content/repositories/snapshots'

maven_package(
    src = {
        "gerrit-acceptance-framework": "//gerrit-acceptance-framework:liblib-src.jar",
        "gerrit-extension-api": "//gerrit-extension-api:libapi-src.jar",
        "gerrit-plugin-api": "//gerrit-plugin-api:plugin-api-sources_deploy.jar",
        "gerrit-plugin-gwtui": "//gerrit-plugin-gwtui:gwtui-api-source_deploy.jar",
    },
    doc = {
        "gerrit-acceptance-framework": "//gerrit-acceptance-framework:acceptance-framework-javadoc",
        "gerrit-extension-api": "//gerrit-extension-api:extension-api-javadoc",
        "gerrit-plugin-api": "//gerrit-plugin-api:plugin-api-javadoc",
        "gerrit-plugin-gwtui": "//gerrit-plugin-gwtui:gwtui-api-javadoc",
    },
    jar = {
        "gerrit-acceptance-framework": "//gerrit-acceptance-framework:acceptance-framework_deploy.jar",
        "gerrit-extension-api": "//gerrit-extension-api:extension-api_deploy.jar",
        "gerrit-plugin-api": "//gerrit-plugin-api:plugin-api_deploy.jar",
        "gerrit-plugin-gwtui": "//gerrit-plugin-gwtui:gwtui-api_deploy.jar",
    },
    repository = MAVEN_REPOSITORY,
    url = URL,
    version = GERRIT_VERSION,
    war = {"gerrit-war": "//:release"},
)
