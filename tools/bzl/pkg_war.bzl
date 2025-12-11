# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# War packaging.

load("@com_googlesource_gerrit_bazlets//tools:pkg_war.bzl", _pkg_war = "pkg_war")

LIBS = [
    "//java/com/google/gerrit/common:version",
    "//java/com/google/gerrit/httpd/init",
    "//lib/bouncycastle:bcpkix",
    "//lib/bouncycastle:bcprov",
    "//lib/bouncycastle:bcpg",
    "//lib/log:impl-log4j",
    "//prolog:gerrit-prolog-common",
    "//resources:log4j-config",
]

PGMLIBS = [
    "//java/com/google/gerrit/pgm",
]

# Jars that must not be packaged into release.war.
#
# Keep this list prefix-based and version-agnostic so it remains stable
# across dependency upgrades.
EXCLUDE_WAR_JAR_PREFIXES = [
    # Codegen / annotation processor support libs (compile-time only).
    "autotransient-",
    "auto-",
    "javapoet-",
    "checker-qual-",
    "checker-compat-qual-",
    "error_prone_annotations-",
    "jspecify-",
    "jsinterop-annotations-",

    # Placeholder jar used to avoid conflicts with Guava.
    "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava",
]

# Identifiers that should not be tracked in third-party WAR allowlists.
THIRD_PARTY_EXCLUDE_ID_PREFIXES = [
    "com_google_",
    "gerrit_",
]

# Gerrit-internal jars whose normalized IDs do not retain the com_google_/gerrit_
# namespace after normalization and should not appear in third-party allowlists.
THIRD_PARTY_EXCLUDE_ID_EXACT = [
    "index",
    "libcache_proto-speed",
    "libentities_proto-speed",
    "libgerrit-prolog-common",
    "libjgit-archive",
    "libjgit-servlet",
    "libquery_parser",
    "libssh-apache",
    "log4j-config",
]

def pkg_war(name, ui = "polygerrit", context = [], doc = False, **kwargs):
    doc_ctx = []
    doc_lib = []
    ui_deps = []
    if ui == "polygerrit":
        ui_deps.append("//polygerrit-ui/app:polygerrit_ui")
    if doc:
        doc_ctx.append("//Documentation:html")
        doc_lib.append("//Documentation:index")

    _pkg_war(
        name = name,
        libs = LIBS + doc_lib,
        pgmlibs = PGMLIBS,
        context = doc_ctx + context + ui_deps + [
            "//java:gerrit-main-class_deploy.jar",
            "//webapp:assets",
        ],
        exclude_jar_prefixes = EXCLUDE_WAR_JAR_PREFIXES,
        exclude_third_party_jar = THIRD_PARTY_EXCLUDE_ID_EXACT,
        exclude_third_party_jar_prefix = THIRD_PARTY_EXCLUDE_ID_PREFIXES,
        **kwargs
    )
