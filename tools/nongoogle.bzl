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
        artifact = "org.tukaani:xz:1.6",
        sha1 = "05b6f921f1810bdf90e25471968f741f87168b64",
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
        artifact = "org.elasticsearch.client:elasticsearch-rest-client:7.8.1",
        sha1 = "59feefe006a96a39f83b0dfb6780847e06c1d0a8",
    )

    maven_jar(
        name = "jackson-core",
        artifact = "com.fasterxml.jackson.core:jackson-core:2.11.1",
        sha1 = "8b02908d53183fdf9758e7e20f2fdee87613a962",
    )

    # Google internal dependencies: these are developed at Google, so there is
    # no concern about version skew.

    FLOGGER_VERS = "0.4"

    maven_jar(
        name = "flogger",
        artifact = "com.google.flogger:flogger:" + FLOGGER_VERS,
        sha1 = "9c8863dcc913b56291c0c88e6d4ca9715b43df98",
    )

    maven_jar(
        name = "flogger-log4j-backend",
        artifact = "com.google.flogger:flogger-log4j-backend:" + FLOGGER_VERS,
        sha1 = "17aa5e31daa1354187e14b6978597d630391c028",
    )

    maven_jar(
        name = "flogger-system-backend",
        artifact = "com.google.flogger:flogger-system-backend:" + FLOGGER_VERS,
        sha1 = "287b569d76abcd82f9de87fe41829fbc7ebd8ac9",
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
        name = "javassist",
        artifact = "org.javassist:javassist:3.22.0-GA",
        sha1 = "3e83394258ae2089be7219b971ec21a8288528ad",
    )

    TESTCONTAINERS_VERSION = "1.14.3"

    maven_jar(
        name = "testcontainers",
        artifact = "org.testcontainers:testcontainers:" + TESTCONTAINERS_VERSION,
        sha1 = "071fc82ba663f469447a19434e7db90f3a872753",
    )

    maven_jar(
        name = "testcontainers-elasticsearch",
        artifact = "org.testcontainers:elasticsearch:" + TESTCONTAINERS_VERSION,
        sha1 = "3709e2ebb0b6aa4e2ba2b6ca92ffdd3bf637a86c",
    )

    maven_jar(
        name = "duct-tape",
        artifact = "org.rnorth.duct-tape:duct-tape:1.0.8",
        sha1 = "92edc22a9ab2f3e17c9bf700aaee377d50e8b530",
    )

    maven_jar(
        name = "visible-assertions",
        artifact = "org.rnorth.visible-assertions:visible-assertions:2.1.2",
        sha1 = "20d31a578030ec8e941888537267d3123c2ad1c1",
    )

    maven_jar(
        name = "jna",
        artifact = "net.java.dev.jna:jna:5.5.0",
        sha1 = "0e0845217c4907822403912ad6828d8e0b256208",
    )
