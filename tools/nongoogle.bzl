load("//tools/bzl:maven_jar.bzl", "maven_jar")

def declare_nongoogle_deps():
    """loads dependencies that are not used at Google.

    Changes to versions are exempt from library compliance review. New
    dependencies must pass through library compliance review. This is
    enforced by //lib:nongoogle_test.
    """

    # Transitive dependency of commons-compress
    maven_jar(
        name = "tukaani-xz",
        artifact = "org.tukaani:xz:1.6",
        sha1 = "05b6f921f1810bdf90e25471968f741f87168b64",
    )

    # Transitive dependency of commons-dbcp, do not update without
    # also updating commons-dbcp
    maven_jar(
        name = "commons-pool",
        artifact = "commons-pool:commons-pool:1.5.5",
        sha1 = "7d8ffbdc47aa0c5a8afe5dc2aaf512f369f1d19b",
    )

    maven_jar(
        name = "commons-text",
        artifact = "org.apache.commons:commons-text:1.2",
        sha1 = "74acdec7237f576c4803fff0c1008ab8a3808b2b",
    )

    # elasticsearch-rest-client explicitly depends on this version
    maven_jar(
        name = "httpasyncclient",
        artifact = "org.apache.httpcomponents:httpasyncclient:4.1.4",
        sha1 = "f3a3240681faae3fa46b573a4c7e50cec9db0d86",
    )

    # elasticsearch-rest-client explicitly depends on this version
    maven_jar(
        name = "httpcore-nio",
        artifact = "org.apache.httpcomponents:httpcore-nio:4.4.11",
        sha1 = "7d0a97d01d39cff9aa3e6db81f21fddb2435f4e6",
    )

    maven_jar(
        name = "j2objc",
        artifact = "com.google.j2objc:j2objc-annotations:1.1",
        sha1 = "ed28ded51a8b1c6b112568def5f4b455e6809019",
    )

    maven_jar(
        name = "guava-failureaccess",
        artifact = "com.google.guava:failureaccess:1.0.1",
        sha1 = "1dcf1de382a0bf95a3d8b0849546c88bac1292c9",
    )

    FLEXMARK_VERS = "0.34.18"

    maven_jar(
        name = "flexmark",
        artifact = "com.vladsch.flexmark:flexmark:" + FLEXMARK_VERS,
        sha1 = "65cc1489ef8902023140900a3a7fcce89fba678d",
    )

    maven_jar(
        name = "flexmark-ext-abbreviation",
        artifact = "com.vladsch.flexmark:flexmark-ext-abbreviation:" + FLEXMARK_VERS,
        sha1 = "a0384932801e51f16499358dec69a730739aca3f",
    )

    maven_jar(
        name = "flexmark-ext-anchorlink",
        artifact = "com.vladsch.flexmark:flexmark-ext-anchorlink:" + FLEXMARK_VERS,
        sha1 = "6df2e23b5c94a5e46b1956a29179eb783f84ea2f",
    )

    maven_jar(
        name = "flexmark-ext-autolink",
        artifact = "com.vladsch.flexmark:flexmark-ext-autolink:" + FLEXMARK_VERS,
        sha1 = "069f8ff15e5b435cc96b23f31798ce64a7a3f6d3",
    )

    maven_jar(
        name = "flexmark-ext-definition",
        artifact = "com.vladsch.flexmark:flexmark-ext-definition:" + FLEXMARK_VERS,
        sha1 = "ff177d8970810c05549171e3ce189e2c68b906c0",
    )

    maven_jar(
        name = "flexmark-ext-emoji",
        artifact = "com.vladsch.flexmark:flexmark-ext-emoji:" + FLEXMARK_VERS,
        sha1 = "410bf7d8e5b8bc2c4a8cff644d1b2bc7b271a41e",
    )

    maven_jar(
        name = "flexmark-ext-escaped-character",
        artifact = "com.vladsch.flexmark:flexmark-ext-escaped-character:" + FLEXMARK_VERS,
        sha1 = "6f4fb89311b54284a6175341d4a5e280f13b2179",
    )

    maven_jar(
        name = "flexmark-ext-footnotes",
        artifact = "com.vladsch.flexmark:flexmark-ext-footnotes:" + FLEXMARK_VERS,
        sha1 = "35efe7d9aea97b6f36e09c65f748863d14e1cfe4",
    )

    maven_jar(
        name = "flexmark-ext-gfm-issues",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-issues:" + FLEXMARK_VERS,
        sha1 = "ec1d660102f6a1d0fbe5e57c13b7ff8bae6cff72",
    )

    maven_jar(
        name = "flexmark-ext-gfm-strikethrough",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:" + FLEXMARK_VERS,
        sha1 = "6060442b742c9b6d4d83d7dd4f0fe477c4686dd2",
    )

    maven_jar(
        name = "flexmark-ext-gfm-tables",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-tables:" + FLEXMARK_VERS,
        sha1 = "2fe597849e46e02e0c1ea1d472848f74ff261282",
    )

    maven_jar(
        name = "flexmark-ext-gfm-tasklist",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-tasklist:" + FLEXMARK_VERS,
        sha1 = "b3af19ce4efdc980a066c1bf0f5a6cf8c24c487a",
    )

    maven_jar(
        name = "flexmark-ext-gfm-users",
        artifact = "com.vladsch.flexmark:flexmark-ext-gfm-users:" + FLEXMARK_VERS,
        sha1 = "7456c5f7272c195ee953a02ebab4f58374fb23ee",
    )

    maven_jar(
        name = "flexmark-ext-ins",
        artifact = "com.vladsch.flexmark:flexmark-ext-ins:" + FLEXMARK_VERS,
        sha1 = "13fe1a95a8f3be30b574451cfe8d3d5936fa3e94",
    )

    maven_jar(
        name = "flexmark-ext-jekyll-front-matter",
        artifact = "com.vladsch.flexmark:flexmark-ext-jekyll-front-matter:" + FLEXMARK_VERS,
        sha1 = "e146e2bf3a740d6ef06a33a516c4d1f6d3761109",
    )

    maven_jar(
        name = "flexmark-ext-superscript",
        artifact = "com.vladsch.flexmark:flexmark-ext-superscript:" + FLEXMARK_VERS,
        sha1 = "02541211e8e4a6c89ce0a68b07b656d8a19ac282",
    )

    maven_jar(
        name = "flexmark-ext-tables",
        artifact = "com.vladsch.flexmark:flexmark-ext-tables:" + FLEXMARK_VERS,
        sha1 = "775d9587de71fd50573f32eee98ab039b4dcc219",
    )

    maven_jar(
        name = "flexmark-ext-toc",
        artifact = "com.vladsch.flexmark:flexmark-ext-toc:" + FLEXMARK_VERS,
        sha1 = "85b75fe1ebe24c92b9d137bcbc51d232845b6077",
    )

    maven_jar(
        name = "flexmark-ext-typographic",
        artifact = "com.vladsch.flexmark:flexmark-ext-typographic:" + FLEXMARK_VERS,
        sha1 = "c1bf0539de37d83aa05954b442f929e204cd89db",
    )

    maven_jar(
        name = "flexmark-ext-wikilink",
        artifact = "com.vladsch.flexmark:flexmark-ext-wikilink:" + FLEXMARK_VERS,
        sha1 = "400b23b9a4e0c008af0d779f909ee357628be39d",
    )

    maven_jar(
        name = "flexmark-ext-yaml-front-matter",
        artifact = "com.vladsch.flexmark:flexmark-ext-yaml-front-matter:" + FLEXMARK_VERS,
        sha1 = "491f815285a8e16db1e906f3789a94a8a9836fa6",
    )

    maven_jar(
        name = "flexmark-formatter",
        artifact = "com.vladsch.flexmark:flexmark-formatter:" + FLEXMARK_VERS,
        sha1 = "d46308006800d243727100ca0f17e6837070fd48",
    )

    maven_jar(
        name = "flexmark-html-parser",
        artifact = "com.vladsch.flexmark:flexmark-html-parser:" + FLEXMARK_VERS,
        sha1 = "fece2e646d11b6a77fc611b4bd3eb1fb8a635c87",
    )

    maven_jar(
        name = "flexmark-profile-pegdown",
        artifact = "com.vladsch.flexmark:flexmark-profile-pegdown:" + FLEXMARK_VERS,
        sha1 = "297f723bb51286eaa7029558fac87d819643d577",
    )

    maven_jar(
        name = "flexmark-util",
        artifact = "com.vladsch.flexmark:flexmark-util:" + FLEXMARK_VERS,
        sha1 = "31e2e1fbe8273d7c913506eafeb06b1a7badb062",
    )

    # Transitive dependency of flexmark and gitiles
    maven_jar(
        name = "autolink",
        artifact = "org.nibor.autolink:autolink:0.7.0",
        sha1 = "649f9f13422cf50c926febe6035662ae25dc89b2",
    )

    maven_jar(
        name = "html-types",
        artifact = "com.google.common.html.types:types:1.0.8",
        sha1 = "9e9cf7bc4b2a60efeb5f5581fe46d17c068e0777",
    )

    maven_jar(
        name = "dropwizard-core",
        artifact = "io.dropwizard.metrics:metrics-core:4.0.5",
        sha1 = "b81ef162970cdb9f4512ee2da09715a856ff4c4c",
    )

    maven_jar(
        name = "sshd",
        artifact = "org.apache.sshd:sshd-core:2.0.0",
        sha1 = "f4275079a2463cfd2bf1548a80e1683288a8e86b",
    )

    maven_jar(
        name = "eddsa",
        artifact = "net.i2p.crypto:eddsa:0.2.0",
        sha1 = "0856a92559c4daf744cb27c93cd8b7eb1f8c4780",
    )

    maven_jar(
        name = "mina-core",
        artifact = "org.apache.mina:mina-core:2.0.17",
        sha1 = "7e10ec974760436d931f3e58be507d1957bcc8db",
    )

    maven_jar(
        name = "sshd-mina",
        artifact = "org.apache.sshd:sshd-mina:2.0.0",
        sha1 = "50f2669312494f6c1996d8bd0d266c1fca7be6f6",
    )

    # Test-only dependencies below.
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
        name = "cglib-3_2",
        artifact = "cglib:cglib-nodep:3.2.6",
        sha1 = "92bf48723d277d6efd1150b2f7e9e1e92cb56caf",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:1.3",
        sha1 = "dc13ae4faca6df981fc7aeb5a522d9db446d5d50",
    )

    maven_jar(
        name = "javassist",
        artifact = "org.javassist:javassist:3.22.0-GA",
        sha1 = "3e83394258ae2089be7219b971ec21a8288528ad",
    )

    POWERM_VERS = "1.6.1"

    maven_jar(
        name = "powermock-module-junit4",
        artifact = "org.powermock:powermock-module-junit4:" + POWERM_VERS,
        sha1 = "ea8530b2848542624f110a393513af397b37b9cf",
    )

    maven_jar(
        name = "powermock-module-junit4-common",
        artifact = "org.powermock:powermock-module-junit4-common:" + POWERM_VERS,
        sha1 = "7222ced54dabc310895d02e45c5428ca05193cda",
    )

    maven_jar(
        name = "powermock-reflect",
        artifact = "org.powermock:powermock-reflect:" + POWERM_VERS,
        sha1 = "97d25eda8275c11161bcddda6ef8beabd534c878",
    )

    maven_jar(
        name = "powermock-api-easymock",
        artifact = "org.powermock:powermock-api-easymock:" + POWERM_VERS,
        sha1 = "aa740ecf89a2f64d410b3d93ef8cd6833009ef00",
    )

    maven_jar(
        name = "powermock-api-support",
        artifact = "org.powermock:powermock-api-support:" + POWERM_VERS,
        sha1 = "592ee6d929c324109d3469501222e0c76ccf0869",
    )

    maven_jar(
        name = "powermock-core",
        artifact = "org.powermock:powermock-core:" + POWERM_VERS,
        sha1 = "5afc1efce8d44ed76b30af939657bd598e45d962",
    )

    maven_jar(
        name = "openid-consumer",
        artifact = "org.openid4java:openid4java:1.0.0",
        sha1 = "541091bb49f2c0d583544c5bb1e6df7612d31e3e",
    )

    maven_jar(
        name = "nekohtml",
        artifact = "net.sourceforge.nekohtml:nekohtml:1.9.10",
        sha1 = "14052461031a7054aa094f5573792feb6686d3de",
    )

    maven_jar(
        name = "xerces",
        artifact = "xerces:xercesImpl:2.8.1",
        attach_source = False,
        sha1 = "25101e37ec0c907db6f0612cbf106ee519c1aef1",
    )

    maven_jar(
        name = "jruby",
        artifact = "org.jruby:jruby-complete:9.1.17.0",
        sha1 = "76716d529710fc03d1d429b43e3cedd4419f78d4",
    )

    # When upgrading elasticsearch-rest-client, also upgrade httpcore-nio
    # and httpasyncclient as necessary.
    maven_jar(
        name = "elasticsearch-rest-client",
        artifact = "org.elasticsearch.client:elasticsearch-rest-client:7.0.0",
        sha1 = "121d12f1c71f318be1a654e8a956e38d5b68e98a",
    )

    JACKSON_VERSION = "2.9.8"

    maven_jar(
        name = "jackson-core",
        artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VERSION,
        sha1 = "0f5a654e4675769c716e5b387830d19b501ca191",
    )

    TESTCONTAINERS_VERSION = "1.11.2"

    maven_jar(
        name = "testcontainers",
        artifact = "org.testcontainers:testcontainers:" + TESTCONTAINERS_VERSION,
        sha1 = "eae47ed24bb07270d4b60b5e2c3444c5bf3c8ea9",
    )

    maven_jar(
        name = "testcontainers-elasticsearch",
        artifact = "org.testcontainers:elasticsearch:" + TESTCONTAINERS_VERSION,
        sha1 = "a327bd8cb68eb7146b36d754aee98a8018132d8f",
    )

    maven_jar(
        name = "duct-tape",
        artifact = "org.rnorth.duct-tape:duct-tape:1.0.7",
        sha1 = "a26b5d90d88c91321dc7a3734ea72d2fc019ebb6",
    )

    maven_jar(
        name = "visible-assertions",
        artifact = "org.rnorth.visible-assertions:visible-assertions:2.1.2",
        sha1 = "20d31a578030ec8e941888537267d3123c2ad1c1",
    )

    maven_jar(
        name = "jna",
        artifact = "net.java.dev.jna:jna:5.2.0",
        sha1 = "ed8b772eb077a9cb50e44e90899c66a9a6c00e67",
    )

    maven_jar(
        name = "javax-activation",
        artifact = "javax.activation:activation:1.1.1",
        sha1 = "485de3a253e23f645037828c07f1d7f1af40763a",
    )

    maven_jar(
        name = "mockito",
        artifact = "org.mockito:mockito-core:2.24.0",
        sha1 = "969a7bcb6f16e076904336ebc7ca171d412cc1f9",
    )

    BYTE_BUDDY_VERSION = "1.9.7"

    maven_jar(
        name = "byte-buddy",
        artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
        sha1 = "8fea78fea6449e1738b675cb155ce8422661e237",
    )

    maven_jar(
        name = "byte-buddy-agent",
        artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
        sha1 = "8e7d1b599f4943851ffea125fd9780e572727fc0",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:2.6",
        sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
    )
