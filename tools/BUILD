load("@bazel_tools//tools/jdk:default_java_toolchain.bzl", "default_java_toolchain")

py_binary(
    name = "merge_jars",
    srcs = ["merge_jars.py"],
    main = "merge_jars.py",
    visibility = ["//visibility:public"],
)

# TODO(davido): remove this when minimum suported Bazel version >= 0.17
# Copied from tools/jdk/default_java_toolchain.bzl to make Bazel 0.16
# and later Bazel released to work as expected. See this issue for context:
# https://github.com/bazelbuild/bazel/issues/6009
JDK9_JVM_OPTS = [
    # Allow JavaBuilder to access internal javac APIs.
    "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
    "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",

    # override the javac in the JDK.
    "--patch-module=java.compiler=$(location @bazel_tools//third_party/java/jdk/langtools:java_compiler_jar)",
    "--patch-module=jdk.compiler=$(location @bazel_tools//third_party/java/jdk/langtools:jdk_compiler_jar)",

    # quiet warnings from com.google.protobuf.UnsafeUtil,
    # see: https://github.com/google/protobuf/issues/3781
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
]

# See https://github.com/bazelbuild/bazel/issues/3427 for more context
default_java_toolchain(
    name = "error_prone_warnings_toolchain_bazel_0.16",
    bootclasspath = ["@bazel_tools//tools/jdk:platformclasspath.jar"],
    jvm_opts = JDK9_JVM_OPTS,
    package_configuration = [
        ":error_prone",
    ],
    visibility = ["//visibility:public"],
)

default_java_toolchain(
    name = "error_prone_warnings_toolchain",
    bootclasspath = ["@bazel_tools//tools/jdk:platformclasspath8.jar"],
    package_configuration = [
        ":error_prone",
    ],
    visibility = ["//visibility:public"],
)

# This EP warnings list is based on:
# https://github.com/bazelbuild/BUILD_file_generator/blob/master/tools/bazel_defs/java.bzl
java_package_configuration(
    name = "error_prone",
    javacopts = [
        "-XepDisableWarningsInGeneratedCode",
        "-Xep:MissingCasesInEnumSwitch:ERROR",
        "-Xep:ReferenceEquality:WARN",
        "-Xep:StringEquality:WARN",
        "-Xep:WildcardImport:WARN",
        "-Xep:AmbiguousMethodReference:WARN",
        "-Xep:BadAnnotationImplementation:WARN",
        "-Xep:BadComparable:WARN",
        "-Xep:BoxedPrimitiveConstructor:ERROR",
        "-Xep:CannotMockFinalClass:WARN",
        "-Xep:ClassCanBeStatic:WARN",
        "-Xep:ClassNewInstance:WARN",
        "-Xep:DefaultCharset:WARN",
        "-Xep:DoubleCheckedLocking:WARN",
        "-Xep:ElementsCountedInLoop:WARN",
        "-Xep:EqualsHashCode:WARN",
        "-Xep:EqualsIncompatibleType:WARN",
        "-Xep:ExpectedExceptionChecker:ERROR",
        "-Xep:Finally:WARN",
        "-Xep:FloatingPointLiteralPrecision:WARN",
        "-Xep:FragmentInjection:WARN",
        "-Xep:FragmentNotInstantiable:WARN",
        "-Xep:FunctionalInterfaceClash:WARN",
        "-Xep:FutureReturnValueIgnored:WARN",
        "-Xep:GetClassOnEnum:WARN",
        "-Xep:ImmutableAnnotationChecker:WARN",
        "-Xep:ImmutableEnumChecker:WARN",
        "-Xep:IncompatibleModifiers:WARN",
        "-Xep:InjectOnConstructorOfAbstractClass:WARN",
        "-Xep:InputStreamSlowMultibyteRead:WARN",
        "-Xep:IterableAndIterator:WARN",
        "-Xep:JUnit3FloatingPointComparisonWithoutDelta:WARN",
        "-Xep:JUnitAmbiguousTestClass:WARN",
        "-Xep:LiteralClassName:WARN",
        "-Xep:MissingFail:WARN",
        "-Xep:MissingOverride:WARN",
        "-Xep:MutableConstantField:WARN",
        "-Xep:NarrowingCompoundAssignment:WARN",
        "-Xep:NonAtomicVolatileUpdate:WARN",
        "-Xep:NonOverridingEquals:WARN",
        "-Xep:NullableConstructor:WARN",
        "-Xep:NullablePrimitive:WARN",
        "-Xep:NullableVoid:WARN",
        "-Xep:OperatorPrecedence:WARN",
        "-Xep:OverridesGuiceInjectableMethod:WARN",
        "-Xep:PreconditionsInvalidPlaceholder:WARN",
        "-Xep:ProtoFieldPreconditionsCheckNotNull:WARN",
        "-Xep:ProtocolBufferOrdinal:WARN",
        "-Xep:RequiredModifiers:WARN",
        "-Xep:ShortCircuitBoolean:WARN",
        "-Xep:SimpleDateFormatConstant:WARN",
        "-Xep:StaticGuardedByInstance:WARN",
        "-Xep:SynchronizeOnNonFinalField:WARN",
        "-Xep:TruthConstantAsserts:WARN",
        "-Xep:TypeParameterShadowing:WARN",
        "-Xep:TypeParameterUnusedInFormals:WARN",
        "-Xep:URLEqualsHashCode:WARN",
        "-Xep:UnsynchronizedOverridesSynchronized:WARN",
        "-Xep:WaitNotInLoop:WARN",
    ],
    packages = ["error_prone_packages"],
)

package_group(
    name = "error_prone_packages",
    packages = [
        "//gerrit-acceptance-framework/...",
        "//gerrit-acceptance-tests/...",
        "//gerrit-cache-h2/...",
        "//gerrit-cache-mem/...",
        "//gerrit-common/...",
        "//gerrit-elasticsearch/...",
        "//gerrit-extension-api/...",
        "//gerrit-gpg/...",
        "//gerrit-httpd/...",
        "//gerrit-launcher/...",
        "//gerrit-lucene/...",
        "//gerrit-main/...",
        "//gerrit-oauth/...",
        "//gerrit-openid/...",
        "//gerrit-patch-commonsnet/...",
        "//gerrit-patch-jgit/...",
        "//gerrit-pgm/...",
        "//gerrit-plugin-api/...",
        "//gerrit-plugin-gwtui/...",
        "//gerrit-prettify/...",
        "//gerrit-reviewdb/...",
        "//gerrit-server/...",
        "//gerrit-sshd/...",
        "//gerrit-test-util/...",
        "//gerrit-util-cli/...",
        "//gerrit-util-http/...",
        "//gerrit-util-ssl/...",
        "//gerrit-war/...",
    ],
)
