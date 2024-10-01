load("//tools/bzl:maven_jar.bzl", "maven_jar")

JACKSON_VER = "2.9.7"

def external_plugin_deps():
    maven_jar(
        name = "jackson-core",
        artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VER,
        sha1 = "4b7f0e0dc527fab032e9800ed231080fdc3ac015",
    )

    maven_jar(
        name = "jackson-databind",
        artifact = "com.fasterxml.jackson.core:jackson-databind:" + JACKSON_VER,
        sha1 = "e6faad47abd3179666e89068485a1b88a195ceb7",
    )

    maven_jar(
        name = "jackson-annotations",
        artifact = "com.fasterxml.jackson.core:jackson-annotations:" + JACKSON_VER,
        sha1 = "4b838e5c4fc17ac02f3293e9a558bb781a51c46d",
    )

    maven_jar(
        name = "jackson-dataformat-yaml",
        artifact = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:" + JACKSON_VER,
        sha1 = "a428edc4bb34a2da98a50eb759c26941d4e85960",
    )

    maven_jar(
        name = "snakeyaml",
        artifact = "org.yaml:snakeyaml:1.23",
        sha1 = "ec62d74fe50689c28c0ff5b35d3aebcaa8b5be68",
    )

    maven_jar(
        name = "easymock",
        artifact = "org.easymock:easymock:3.1",
        sha1 = "3e127311a86fc2e8f550ef8ee4abe094bbcf7e7e",
        deps = [
            "@cglib//jar",
            "@objenesis//jar",
        ],
    )

    maven_jar(
        name = "cglib",
        artifact = "cglib:cglib-nodep:3.2.6",
        sha1 = "92bf48723d277d6efd1150b2f7e9e1e92cb56caf",
    )

    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:2.6",
        sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
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

    maven_jar(
    	name = 'prometheus_simpleclient',
    	artifact = 'io.prometheus:simpleclient:0.2.0',
    	sha1 = 'be8de6a5a01f25074be3b27a8db4448c9cce0168',
    )
    maven_jar(
    	name = 'prometheus_simpleclient_common',
    	artifact = 'io.prometheus:simpleclient_common:0.2.0',
    	sha1 = '42d513358b26ae44137c620fa517d37b5e707ae1',
    )
    maven_jar(
    	name = 'prometheus_simpleclient_servlet',
    	artifact = 'io.prometheus:simpleclient_servlet:0.2.0',
    	sha1 = '98b6235fe4277ce0eb08ba8d171e57da9298a6f7',
    )
    maven_jar(
    	name = 'metrics_prometheus',
    	artifact = 'io.prometheus:simpleclient_dropwizard:0.2.0',
    	sha1 = '7a73c8db679de5193728fdc205d9734dbdd3d9f9',
    )

