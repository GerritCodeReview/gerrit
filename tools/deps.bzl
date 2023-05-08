load("//tools/bzl:maven_jar.bzl", "GERRIT", "maven_jar")
load("@bazel_tools//tools/build_defs/repo:java.bzl", "java_import_external")

CAFFEINE_VERS = "2.9.2"
ANTLR_VERS = "3.5.2"
COMMONMARK_VERS = "0.10.0"
FLEXMARK_VERS = "0.50.50"
GREENMAIL_VERS = "1.5.5"
MAIL_VERS = "1.6.0"
MIME4J_VERS = "0.8.1"
OW2_VERS = "9.2"
AUTO_COMMON_VERSION = "1.2.1"
AUTO_FACTORY_VERSION = "1.0.1"
AUTO_VALUE_VERSION = "1.7.4"
AUTO_VALUE_GSON_VERSION = "1.3.1"
PROLOG_VERS = "1.4.4"
PROLOG_REPO = GERRIT
GITILES_VERS = "1.0.0"
GITILES_REPO = GERRIT

# When updating Bouncy Castle, also update it in bazlets.
BC_VERS = "1.72"
HTTPCOMP_VERS = "4.5.2"
JETTY_VERS = "9.4.36.v20210114"
BYTE_BUDDY_VERSION = "1.10.7"

def java_dependencies():
    maven_jar(
        name = "java-runtime",
        artifact = "org.antlr:antlr-runtime:" + ANTLR_VERS,
        sha1 = "cd9cd41361c155f3af0f653009dcecb08d8b4afd",
    )

    maven_jar(
        name = "stringtemplate",
        artifact = "org.antlr:stringtemplate:4.0.2",
        sha1 = "e28e09e2d44d60506a7bcb004d6c23ff35c6ac08",
    )

    maven_jar(
        name = "org-antlr",
        artifact = "org.antlr:antlr:" + ANTLR_VERS,
        sha1 = "c4a65c950bfc3e7d04309c515b2177c00baf7764",
    )

    maven_jar(
        name = "antlr27",
        artifact = "antlr:antlr:2.7.7",
        attach_source = False,
        sha1 = "83cd2cd674a217ade95a4bb83a8a14f351f48bd0",
    )

    maven_jar(
        name = "aopalliance",
        artifact = "aopalliance:aopalliance:1.0",
        sha1 = "0235ba8b489512805ac13a8f9ea77a1ca5ebe3e8",
    )

    maven_jar(
        name = "javax_inject",
        artifact = "javax.inject:javax.inject:1",
        sha1 = "6975da39a7040257bd51d21a231b76c915872d38",
    )

    maven_jar(
        name = "servlet-api",
        artifact = "javax.servlet:javax.servlet-api:3.1.0",
        sha1 = "3cd63d075497751784b2fa84be59432f4905bf7c",
    )

    # JGit's transitive dependencies

    maven_jar(
        name = "javaewah",
        artifact = "com.googlecode.javaewah:JavaEWAH:1.1.12",
        attach_source = False,
        sha1 = "9feecc2b24d6bc9ff865af8d082f192238a293eb",
    )

    maven_jar(
        name = "gson",
        artifact = "com.google.code.gson:gson:2.9.0",
        sha1 = "8a1167e089096758b49f9b34066ef98b2f4b37aa",
    )

    maven_jar(
        name = "caffeine",
        artifact = "com.github.ben-manes.caffeine:caffeine:" + CAFFEINE_VERS,
        sha1 = "0a17ed335e0ce2d337750772c0709b79af35a842",
    )

    maven_jar(
        name = "guava-failureaccess",
        artifact = "com.google.guava:failureaccess:1.0.1",
        sha1 = "1dcf1de382a0bf95a3d8b0849546c88bac1292c9",
    )

    maven_jar(
        name = "juniversalchardet",
        artifact = "com.github.albfernandez:juniversalchardet:2.0.0",
        sha1 = "28c59f58f5adcc307604602e2aa89e2aca14c554",
    )

    maven_jar(
        name = "json-smart",
        artifact = "net.minidev:json-smart:1.1.1",
        sha1 = "24a2f903d25e004de30ac602c5b47f2d4e420a59",
    )

    maven_jar(
        name = "args4j",
        artifact = "args4j:args4j:2.33",
        sha1 = "bd87a75374a6d6523de82fef51fc3cfe9baf9fc9",
    )

    maven_jar(
        name = "commons-codec",
        artifact = "commons-codec:commons-codec:1.10",
        sha1 = "4b95f4897fa13f2cd904aee711aeafc0c5295cd8",
    )

    # When upgrading commons-compress, also upgrade tukaani-xz
    maven_jar(
        name = "commons-compress",
        artifact = "org.apache.commons:commons-compress:1.22",
        sha1 = "691a8b4e6cf4248c3bc72c8b719337d5cb7359fa",
    )

    maven_jar(
        name = "commons-lang3",
        artifact = "org.apache.commons:commons-lang3:3.8.1",
        sha1 = "6505a72a097d9270f7a9e7bf42c4238283247755",
    )

    maven_jar(
        name = "commons-text",
        artifact = "org.apache.commons:commons-text:1.2",
        sha1 = "74acdec7237f576c4803fff0c1008ab8a3808b2b",
    )

    maven_jar(
        name = "commons-dbcp",
        artifact = "commons-dbcp:commons-dbcp:1.4",
        sha1 = "30be73c965cc990b153a100aaaaafcf239f82d39",
    )

    # Transitive dependency of commons-dbcp, do not update without
    # also updating commons-dbcp
    maven_jar(
        name = "commons-pool",
        artifact = "commons-pool:commons-pool:1.5.5",
        sha1 = "7d8ffbdc47aa0c5a8afe5dc2aaf512f369f1d19b",
    )

    maven_jar(
        name = "commons-net",
        artifact = "commons-net:commons-net:3.6",
        sha1 = "b71de00508dcb078d2b24b5fa7e538636de9b3da",
    )

    maven_jar(
        name = "commons-validator",
        artifact = "commons-validator:commons-validator:1.6",
        sha1 = "e989d1e87cdd60575df0765ed5bac65c905d7908",
    )

    maven_jar(
        name = "automaton",
        artifact = "dk.brics:automaton:1.12-1",
        sha1 = "959a0c62f9a5c2309e0ad0b0589c74d69e101241",
    )

    # commonmark must match the version used in Gitiles
    maven_jar(
        name = "commonmark",
        artifact = "com.atlassian.commonmark:commonmark:" + COMMONMARK_VERS,
        sha1 = "119cb7bedc3570d9ecb64ec69ab7686b5c20559b",
    )

    maven_jar(
        name = "cm-autolink",
        artifact = "com.atlassian.commonmark:commonmark-ext-autolink:" + COMMONMARK_VERS,
        sha1 = "a6056a5efbd68f57d420bc51bbc54b28a5d3c56b",
    )

    maven_jar(
        name = "gfm-strikethrough",
        artifact = "com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:" + COMMONMARK_VERS,
        sha1 = "40837da951b421b545edddac57012e15fcc9e63c",
    )

    maven_jar(
        name = "gfm-tables",
        artifact = "com.atlassian.commonmark:commonmark-ext-gfm-tables:" + COMMONMARK_VERS,
        sha1 = "c075db2a3301100cf70c7dced8ecf86b494458a2",
    )

    maven_jar(
        name = "flexmark",
        artifact = "com.vladsch.flexmark:flexmark:" + FLEXMARK_VERS,
        sha1 = "7f61e190cff7e1bea64906408ccfa583b5ac9fc2",
    )

    maven_jar(
        name = "flexmark-ext-abbreviation",
        artifact = "com.vladsch.flexmark:flexmark-ext-abbreviation:" + FLEXMARK_VERS,
        sha1 = "8f62f49cfaf8d33391e48a3b79e941d94e444e50",
    )

    maven_jar(
        name = "flexmark-ext-anchorlink",
        artifact = "com.vladsch.flexmark:flexmark-ext-anchorlink:" + FLEXMARK_VERS,
        sha1 = "1d530e44b4439abf53ce9dcc784ffa5b9fd6ce89",
    )

    maven_jar(
        name = "flexmark-ext-autolink",
        artifact = "com.vladsch.flexmark:flexmark-ext-autolink:" + FLEXMARK_VERS,
        sha1 = "4026aa60fd6e146c2d4931acb19b2409c6a79b8a",
    )

    maven_jar(
        name = "flexmark-ext-definition",
        artifact = "com.vladsch.flexmark:flexmark-ext-definition:" + FLEXMARK_VERS,
        sha1 = "bb74d36459e8e34653761aaa0095220b8b7b6cb6",
    )

    maven_jar(
        name = "flexmark-ext-emoji",
        artifact = "com.vladsch.flexmark:flexmark-ext-emoji:" + FLEXMARK_VERS,
        sha1 = "36d36cb227f831b81b636d3f901f07db06b8d84d",
    )

    maven_jar(
        name = "flexmark-ext-escaped-character",
        artifact = "com.vladsch.flexmark:flexmark-ext-escaped-character:" + FLEXMARK_VERS,
        sha1 = "09f0cef3260b6f6371d6066cf32ce6a7dc2a7922",
    )

    maven_jar(
        name = "flexmark-ext-footnotes",
        artifact = "com.vladsch.flexmark:flexmark-ext-footnotes:" + FLEXMARK_VERS,
        sha1 = "cdf0b79f026b09c6b87d91e61eb29ada1577aa5c",
    )

    maven_jar(
        name = "flexmark-ext-gfm-issues",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-issues:" + FLEXMARK_VERS,
        sha1 = "e40d347e242e35d4344553120cb9e69c1c975f41",
    )

    maven_jar(
        name = "flexmark-ext-gfm-strikethrough",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:" + FLEXMARK_VERS,
        sha1 = "a6948f6e4fc2ec1059d6b53e73d4a30c24f6e05d",
    )

    maven_jar(
        name = "flexmark-ext-gfm-tables",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-tables:" + FLEXMARK_VERS,
        sha1 = "92d3eb0c5dc79924448c186d89717f1df853aaa0",
    )

    maven_jar(
        name = "flexmark-ext-gfm-tasklist",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-tasklist:" + FLEXMARK_VERS,
        sha1 = "e6fb4d9e46c96e61a07fbb40cf653db58ed95a95",
    )

    maven_jar(
        name = "flexmark-ext-gfm-users",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-users:" + FLEXMARK_VERS,
        sha1 = "44c83bdccfd41a399f3f7b11ba72728382dd3f5a",
    )

    maven_jar(
        name = "flexmark-ext-ins",
        artifact = "com.vladsch.flexmark:flexmark-ext-ins:" + FLEXMARK_VERS,
        sha1 = "b0d174d86ac2348420993dc2c997fad5f02c68fa",
    )

    maven_jar(
        name = "flexmark-ext-jekyll-front-matter",
        artifact = "com.vladsch.flexmark:flexmark-ext-jekyll-front-matter:" + FLEXMARK_VERS,
        sha1 = "2bfb31c67fd10af058b90f1b364f881f6276de80",
    )

    maven_jar(
        name = "flexmark-ext-superscript",
        artifact = "com.vladsch.flexmark:flexmark-ext-superscript:" + FLEXMARK_VERS,
        sha1 = "d210b007b46339082f79b6caf632b19ac8efc341",
    )

    maven_jar(
        name = "flexmark-ext-tables",
        artifact = "com.vladsch.flexmark:flexmark-ext-tables:" + FLEXMARK_VERS,
        sha1 = "be77790470aa9bd90011067221504162a1d7b083",
    )

    maven_jar(
        name = "flexmark-ext-toc",
        artifact = "com.vladsch.flexmark:flexmark-ext-toc:" + FLEXMARK_VERS,
        sha1 = "91d6d63ff5b70c3ebae98867bd7a346d71cb0ce1",
    )

    maven_jar(
        name = "flexmark-ext-typographic",
        artifact = "com.vladsch.flexmark:flexmark-ext-typographic:" + FLEXMARK_VERS,
        sha1 = "f51e247df80628df509a3af92a1efa1efb83d746",
    )

    maven_jar(
        name = "flexmark-ext-wikilink",
        artifact = "com.vladsch.flexmark:flexmark-ext-wikilink:" + FLEXMARK_VERS,
        sha1 = "d14aeb078dbaf3e166621ae9499595a4a57b22ab",
    )

    maven_jar(
        name = "flexmark-ext-yaml-front-matter",
        artifact = "com.vladsch.flexmark:flexmark-ext-yaml-front-matter:" + FLEXMARK_VERS,
        sha1 = "aba328b70e15f2553aac0a74e391edab37bf7b30",
    )

    maven_jar(
        name = "flexmark-formatter",
        artifact = "com.vladsch.flexmark:flexmark-formatter:" + FLEXMARK_VERS,
        sha1 = "3e4ad3b1be29d41e8c35cadb70761768505f2164",
    )

    maven_jar(
        name = "flexmark-html-parser",
        artifact = "com.vladsch.flexmark:flexmark-html-parser:" + FLEXMARK_VERS,
        sha1 = "3c59b0061c50c9a8a48c46d4f4498d8eba249921",
    )

    maven_jar(
        name = "flexmark-profile-pegdown",
        artifact = "com.vladsch.flexmark:flexmark-profile-pegdown:" + FLEXMARK_VERS,
        sha1 = "c01a2c2eebe06230858956d45847cee233790d7c",
    )

    maven_jar(
        name = "flexmark-util",
        artifact = "com.vladsch.flexmark:flexmark-util:" + FLEXMARK_VERS,
        sha1 = "fdfce72f5eb9ec53085804fd0c8d15f3680ae359",
    )

    # Transitive dependency of flexmark and gitiles
    maven_jar(
        name = "autolink",
        artifact = "org.nibor.autolink:autolink:0.7.0",
        sha1 = "649f9f13422cf50c926febe6035662ae25dc89b2",
    )

    maven_jar(
        name = "greenmail",
        artifact = "com.icegreen:greenmail:" + GREENMAIL_VERS,
        sha1 = "9ea96384ad2cb8118c22f493b529eb72c212691c",
    )

    maven_jar(
        name = "mail",
        artifact = "com.sun.mail:javax.mail:" + MAIL_VERS,
        sha1 = "a055c648842c4954c1f7db7254f45d9ad565e278",
    )

    maven_jar(
        name = "mime4j-core",
        artifact = "org.apache.james:apache-mime4j-core:" + MIME4J_VERS,
        sha1 = "c62dfe18a3b827a2c626ade0ffba44562ddf3f61",
    )

    maven_jar(
        name = "mime4j-dom",
        artifact = "org.apache.james:apache-mime4j-dom:" + MIME4J_VERS,
        sha1 = "f2d653c617004193f3350330d907f77b60c88c56",
    )

    maven_jar(
        name = "jsoup",
        artifact = "org.jsoup:jsoup:1.9.2",
        sha1 = "5e3bda828a80c7a21dfbe2308d1755759c2fd7b4",
    )

    maven_jar(
        name = "ow2-asm",
        artifact = "org.ow2.asm:asm:" + OW2_VERS,
        sha1 = "81a03f76019c67362299c40e0ba13405f5467bff",
    )

    maven_jar(
        name = "ow2-asm-analysis",
        artifact = "org.ow2.asm:asm-analysis:" + OW2_VERS,
        sha1 = "7487dd756daf96cab9986e44b9d7bcb796a61c10",
    )

    maven_jar(
        name = "ow2-asm-commons",
        artifact = "org.ow2.asm:asm-commons:" + OW2_VERS,
        sha1 = "f4d7f0fc9054386f2893b602454d48e07d4fbead",
    )

    maven_jar(
        name = "ow2-asm-tree",
        artifact = "org.ow2.asm:asm-tree:" + OW2_VERS,
        sha1 = "d96c99a30f5e1a19b0e609dbb19a44d8518ac01e",
    )

    maven_jar(
        name = "ow2-asm-util",
        artifact = "org.ow2.asm:asm-util:" + OW2_VERS,
        sha1 = "fbc178fc5ba3dab50fd7e8a5317b8b647c8e8946",
    )

    maven_jar(
        name = "auto-common",
        artifact = "com.google.auto:auto-common:" + AUTO_COMMON_VERSION,
        sha1 = "f6da26895f759010f5f170c8044e84c1b17ef83e",
    )

    maven_jar(
        name = "auto-factory",
        artifact = "com.google.auto.factory:auto-factory:" + AUTO_FACTORY_VERSION,
        sha1 = "f81ece06b6525085da217cd900116f44caafe877",
    )

    maven_jar(
        name = "auto-service-annotations",
        artifact = "com.google.auto.service:auto-service-annotations:" + AUTO_FACTORY_VERSION,
        sha1 = "ac86dacc0eb9285ea9d42eee6aad8629ca3a7432",
    )

    maven_jar(
        name = "auto-value",
        artifact = "com.google.auto.value:auto-value:" + AUTO_VALUE_VERSION,
        sha1 = "6b126cb218af768339e4d6e95a9b0ae41f74e73d",
    )

    maven_jar(
        name = "auto-value-annotations",
        artifact = "com.google.auto.value:auto-value-annotations:" + AUTO_VALUE_VERSION,
        sha1 = "eff48ed53995db2dadf0456426cc1f8700136f86",
    )

    maven_jar(
        name = "auto-value-gson-runtime",
        artifact = "com.ryanharter.auto.value:auto-value-gson-runtime:" + AUTO_VALUE_GSON_VERSION,
        sha1 = "addda2ae6cce9f855788274df5de55dde4de7b71",
    )

    maven_jar(
        name = "auto-value-gson-extension",
        artifact = "com.ryanharter.auto.value:auto-value-gson-extension:" + AUTO_VALUE_GSON_VERSION,
        sha1 = "0c4c01a3e10e5b10df2e5f5697efa4bb3f453ac1",
    )

    maven_jar(
        name = "auto-value-gson-factory",
        artifact = "com.ryanharter.auto.value:auto-value-gson-factory:" + AUTO_VALUE_GSON_VERSION,
        sha1 = "9ed8d79144ee8d60cc94cc11f847b5ed8ee9f19c",
    )

    maven_jar(
        name = "javapoet",
        artifact = "com.squareup:javapoet:1.13.0",
        sha1 = "d6562d385049f35eb50403fa86bb11cce76b866a",
    )

    maven_jar(
        name = "autotransient",
        artifact = "io.sweers.autotransient:autotransient:1.0.0",
        sha1 = "38b1c630b8e76560221622289f37be40105abb3d",
    )

    maven_jar(
        name = "mime-util",
        artifact = "eu.medsea.mimeutil:mime-util:2.1.3",
        attach_source = False,
        sha1 = "0c9cfae15c74f62491d4f28def0dff1dabe52a47",
    )

    maven_jar(
        name = "prolog-runtime",
        artifact = "com.googlecode.prolog-cafe:prolog-runtime:" + PROLOG_VERS,
        attach_source = False,
        repository = PROLOG_REPO,
        sha1 = "e9a364f4233481cce63239e8e68a6190c8f58acd",
    )

    maven_jar(
        name = "prolog-compiler",
        artifact = "com.googlecode.prolog-cafe:prolog-compiler:" + PROLOG_VERS,
        attach_source = False,
        repository = PROLOG_REPO,
        sha1 = "570295026f6aa7b905e423d107cb2e081eecdc04",
    )

    maven_jar(
        name = "prolog-io",
        artifact = "com.googlecode.prolog-cafe:prolog-io:" + PROLOG_VERS,
        attach_source = False,
        repository = PROLOG_REPO,
        sha1 = "1f25c4e27d22bdbc31481ee0c962a2a2853e4428",
    )

    maven_jar(
        name = "cafeteria",
        artifact = "com.googlecode.prolog-cafe:prolog-cafeteria:" + PROLOG_VERS,
        attach_source = False,
        repository = PROLOG_REPO,
        sha1 = "0e6c2deeaf5054815a561cbd663566fd59b56c6c",
    )

    maven_jar(
        name = "guava-retrying",
        artifact = "com.github.rholder:guava-retrying:2.0.0",
        sha1 = "974bc0a04a11cc4806f7c20a34703bd23c34e7f4",
    )

    maven_jar(
        name = "jsr305",
        artifact = "com.google.code.findbugs:jsr305:3.0.1",
        sha1 = "f7be08ec23c21485b9b5a1cf1654c2ec8c58168d",
    )

    maven_jar(
        name = "blame-cache",
        artifact = "com.google.gitiles:blame-cache:" + GITILES_VERS,
        attach_source = False,
        repository = GITILES_REPO,
        sha1 = "f46833f8aa6f33ce3e443c8a414c295559eaf43e",
    )

    maven_jar(
        name = "gitiles-servlet",
        artifact = "com.google.gitiles:gitiles-servlet:" + GITILES_VERS,
        repository = GITILES_REPO,
        sha1 = "90e107da00c2cd32490dd9ae8e3fb1ee095ea675",
    )

    # prettify must match the version used in Gitiles
    maven_jar(
        name = "prettify",
        artifact = "com.github.twalcari:java-prettify:1.2.2",
        sha1 = "b8ba1c1eb8b2e45cfd465d01218c6060e887572e",
    )

    maven_jar(
        name = "html-types",
        artifact = "com.google.common.html.types:types:1.0.8",
        sha1 = "9e9cf7bc4b2a60efeb5f5581fe46d17c068e0777",
    )

    maven_jar(
        name = "icu4j",
        artifact = "com.ibm.icu:icu4j:57.1",
        sha1 = "198ea005f41219f038f4291f0b0e9f3259730e92",
    )

    maven_jar(
        name = "bcprov",
        artifact = "org.bouncycastle:bcprov-jdk18on:" + BC_VERS,
        sha1 = "d8dc62c28a3497d29c93fee3e71c00b27dff41b4",
    )

    maven_jar(
        name = "bcpg",
        artifact = "org.bouncycastle:bcpg-jdk18on:" + BC_VERS,
        sha1 = "1a36a1740d07869161f6f0d01fae8d72dd1d8320",
    )

    maven_jar(
        name = "bcpkix",
        artifact = "org.bouncycastle:bcpkix-jdk18on:" + BC_VERS,
        sha1 = "bb3fdb5162ccd5085e8d7e57fada4d8eaa571f5a",
    )

    maven_jar(
        name = "bcutil",
        artifact = "org.bouncycastle:bcutil-jdk18on:" + BC_VERS,
        sha1 = "41f19a69ada3b06fa48781120d8bebe1ba955c77",
    )

    maven_jar(
        name = "h2",
        artifact = "com.h2database:h2:1.3.176",
        sha1 = "fd369423346b2f1525c413e33f8cf95b09c92cbd",
    )

    maven_jar(
        name = "fluent-hc",
        artifact = "org.apache.httpcomponents:fluent-hc:" + HTTPCOMP_VERS,
        sha1 = "7bfdfa49de6d720ad3c8cedb6a5238eec564dfed",
    )

    maven_jar(
        name = "httpclient",
        artifact = "org.apache.httpcomponents:httpclient:" + HTTPCOMP_VERS,
        sha1 = "733db77aa8d9b2d68015189df76ab06304406e50",
    )

    maven_jar(
        name = "httpcore",
        artifact = "org.apache.httpcomponents:httpcore:4.4.4",
        sha1 = "b31526a230871fbe285fbcbe2813f9c0839ae9b0",
    )

    # Test-only dependencies below.
    maven_jar(
        name = "junit",
        artifact = "junit:junit:4.12",
        sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
    )

    maven_jar(
        name = "hamcrest-core",
        artifact = "org.hamcrest:hamcrest-core:1.3",
        sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
    )

    maven_jar(
        name = "diffutils",
        artifact = "com.googlecode.java-diff-utils:diffutils:1.3.0",
        sha1 = "7e060dd5b19431e6d198e91ff670644372f60fbd",
    )

    maven_jar(
        name = "jetty-servlet",
        artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VERS,
        sha1 = "b189e52a5ee55ae172e4e99e29c5c314f5daf4b9",
    )

    maven_jar(
        name = "jetty-security",
        artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VERS,
        sha1 = "42030d6ed7dfc0f75818cde0adcf738efc477574",
    )

    maven_jar(
        name = "jetty-server",
        artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VERS,
        sha1 = "88a7d342974aadca658e7386e8d0fcc5c0788f41",
    )

    maven_jar(
        name = "jetty-jmx",
        artifact = "org.eclipse.jetty:jetty-jmx:" + JETTY_VERS,
        sha1 = "bb3847eabe085832aeaedd30e872b40931632e54",
    )

    maven_jar(
        name = "jetty-http",
        artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VERS,
        sha1 = "1eee89a55e04ff94df0f85d95200fc48acb43d86",
    )

    maven_jar(
        name = "jetty-io",
        artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VERS,
        sha1 = "84a8faf9031eb45a5a2ddb7681e22c483d81ab3a",
    )

    maven_jar(
        name = "jetty-util",
        artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VERS,
        sha1 = "925257fbcca6b501a25252c7447dbedb021f7404",
    )

    maven_jar(
        name = "jetty-util-ajax",
        artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VERS,
        sha1 = "2f478130c21787073facb64d7242e06f94980c60",
        src_sha1 = "7153d7ca38878d971fd90992c303bb7719ba7a21",
    )

    maven_jar(
        name = "asciidoctor",
        artifact = "org.asciidoctor:asciidoctorj:1.5.7",
        sha1 = "8e8c1d8fc6144405700dd8df3b177f2801ac5987",
    )

    maven_jar(
        name = "javax-activation",
        artifact = "javax.activation:activation:1.1.1",
        sha1 = "485de3a253e23f645037828c07f1d7f1af40763a",
    )

    maven_jar(
        name = "mockito",
        artifact = "org.mockito:mockito-core:3.3.3",
        sha1 = "4878395d4e63173f3825e17e5e0690e8054445f1",
    )

    maven_jar(
        name = "bytebuddy",
        artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
        sha1 = "1eefb7dd1b032b33c773ca0a17d5cc9e6b56ea1a",
    )

    maven_jar(
        name = "bytebuddy-agent",
        artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
        sha1 = "c472fad33f617228601172682aa64f8b78508045",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:3.0.1",
        sha1 = "11cfac598df9dc48bb9ed9357ed04212694b7808",
    )
