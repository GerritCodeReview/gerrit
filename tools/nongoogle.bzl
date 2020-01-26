load("//tools/bzl:maven_jar.bzl", "maven_jar")

def declare_nongoogle_deps():
    """loads dependencies that are not used at Google.

    Changes to versions are exempt from library compliance review. New
    dependencies must pass through library compliance review. This is
    enforced by //lib:nongoogle_test.
    """

    maven_jar(
        name = "j2objc",
        artifact = "com.google.j2objc:j2objc-annotations:1.1",
        sha1 = "ed28ded51a8b1c6b112568def5f4b455e6809019",
    )

    # Transitive dependency of commons-compress
    maven_jar(
        name = "tukaani-xz",
        artifact = "org.tukaani:xz:1.8",
        sha1 = "c4f7d054303948eb6a4066194253886c8af07128",
    )

    maven_jar(
        name = "dropwizard-core",
        artifact = "io.dropwizard.metrics:metrics-core:4.1.2",
        sha1 = "bba231bbf3024c19e75622ec168821cbbd4261a4",
    )

    SSHD_VERS = "2.3.0"

    maven_jar(
        name = "sshd",
        artifact = "org.apache.sshd:sshd-core:" + SSHD_VERS,
        sha1 = "21aeea9deba96c9b81ea0935fa4fac61aa3cf646",
    )

    maven_jar(
        name = "sshd-common",
        artifact = "org.apache.sshd:sshd-common:" + SSHD_VERS,
        sha1 = "8b6e3baaa0d35b547696965eef3e62477f5e74c9",
    )

    maven_jar(
        name = "eddsa",
        artifact = "net.i2p.crypto:eddsa:0.3.0",
        sha1 = "1901c8d4d8bffb7d79027686cfb91e704217c3e1",
    )

    maven_jar(
        name = "mina-core",
        artifact = "org.apache.mina:mina-core:2.0.21",
        sha1 = "e1a317689ecd438f54e863747e832f741ef8e092",
    )

    maven_jar(
        name = "sshd-mina",
        artifact = "org.apache.sshd:sshd-mina:" + SSHD_VERS,
        sha1 = "55dc0830dfcbceba01f9460812ee454978a15fe8",
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
        artifact = "org.apache.httpcomponents:httpcore-nio:4.4.12",
        sha1 = "84cd29eca842f31db02987cfedea245af020198b",
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
        artifact = "org.elasticsearch.client:elasticsearch-rest-client:7.5.2",
        sha1 = "e11393f600a425b7f62e6f653e19a9e53556fd79",
    )

    maven_jar(
        name = "jackson-core",
        artifact = "com.fasterxml.jackson.core:jackson-core:2.10.2",
        sha1 = "73d4322a6bda684f676a2b5fe918361c4e5c7cca",
    )

    # Test-only dependencies below.

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

    TESTCONTAINERS_VERSION = "1.12.5"

    maven_jar(
        name = "testcontainers",
        artifact = "org.testcontainers:testcontainers:" + TESTCONTAINERS_VERSION,
        sha1 = "ca8a8dfbd3b194fb6c541e3bad858898865ce069",
    )

    maven_jar(
        name = "testcontainers-elasticsearch",
        artifact = "org.testcontainers:elasticsearch:" + TESTCONTAINERS_VERSION,
        sha1 = "d88f79c83e7057edd23d33621c36d6267b9c4e96",
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
