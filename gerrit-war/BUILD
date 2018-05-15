load("//tools/bzl:genrule2.bzl", "genrule2")

java_library(
    name = "init",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//gerrit-cache-h2:cache-h2",
        "//gerrit-cache-mem:mem",
        "//gerrit-elasticsearch:elasticsearch",
        "//gerrit-extension-api:api",
        "//gerrit-gpg:gpg",
        "//gerrit-httpd:httpd",
        "//gerrit-lucene:lucene",
        "//gerrit-oauth:oauth",
        "//gerrit-openid:openid",
        "//gerrit-pgm:http",
        "//gerrit-pgm:init",
        "//gerrit-pgm:init-api",
        "//gerrit-pgm:util",
        "//gerrit-reviewdb:server",
        "//gerrit-server:server",
        "//gerrit-server/src/main/prolog:common",
        "//gerrit-sshd:sshd",
        "//lib:guava",
        "//lib:gwtorm",
        "//lib:servlet-api-3_1",
        "//lib/guice",
        "//lib/guice:guice-servlet",
        "//lib/jgit/org.eclipse.jgit:jgit",
        "//lib/log:api",
    ],
)

genrule2(
    name = "webapp_assets",
    srcs = glob(["src/main/webapp/**/*"]),
    outs = ["webapp_assets.zip"],
    cmd = "cd gerrit-war/src/main/webapp; zip -qr $$ROOT/$@ .",
    visibility = ["//visibility:public"],
)

java_import(
    name = "log4j-config",
    jars = [":log4j-config__jar"],
    visibility = ["//visibility:public"],
)

genrule2(
    name = "log4j-config__jar",
    srcs = ["src/main/resources/log4j.properties"],
    outs = ["log4j-config.jar"],
    cmd = "cd gerrit-war/src/main/resources && zip -9Dqr $$ROOT/$@ .",
)

java_import(
    name = "version",
    jars = [":gen_version"],
    visibility = ["//visibility:public"],
)

genrule2(
    name = "gen_version",
    outs = ["gen_version.jar"],
    cmd = " && ".join([
        "cd $$TMP",
        "mkdir -p com/google/gerrit/common",
        "cat $$ROOT/$(location //:version.txt) >com/google/gerrit/common/Version",
        "zip -9Dqr $$ROOT/$@ .",
    ]),
    tools = ["//:version.txt"],
)
