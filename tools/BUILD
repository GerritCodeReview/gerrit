load(
    "@bazel_tools//tools/jdk:default_java_toolchain.bzl",
    "JDK9_JVM_OPTS",
    "default_java_toolchain",
)
load("@rules_java//java:defs.bzl", "java_package_configuration")

exports_files(["nongoogle.bzl"])

default_java_toolchain(
    name = "error_prone_warnings_toolchain",
    bootclasspath = ["@bazel_tools//tools/jdk:platformclasspath.jar"],
    jvm_opts = JDK9_JVM_OPTS,
    package_configuration = [
        ":error_prone",
    ],
    visibility = ["//visibility:public"],
)

JDK11_JVM_OPTS = select({"@bazel_tools//src/conditions:openbsd": ["-Xbootclasspath/p:$(location @bazel_tools//tools/jdk:javac_jar)"], "//conditions:default": ["--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED", "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--patch-module=java.compiler=$(location @bazel_tools//tools/jdk:java_compiler_jar)", "--patch-module=jdk.compiler=$(location @bazel_tools//tools/jdk:jdk_compiler_jar)", "--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED"]})

default_java_toolchain(
    name = "error_prone_warnings_toolchain_java11",
    bootclasspath = ["@bazel_tools//tools/jdk:platformclasspath.jar"],
    jvm_opts = JDK11_JVM_OPTS,
    package_configuration = [
        ":error_prone",
    ],
    visibility = ["//visibility:public"],
    source_version = "11",
    target_version = "11",
    misc = ["-XDskipDuplicateBridges=true", "-g", "-parameters"],
    javac_supports_workers = True,
    javac = ["@bazel_tools//tools/jdk:javac_jar"],
    tools = ["@bazel_tools//tools/jdk:java_compiler_jar", "@bazel_tools//tools/jdk:jdk_compiler_jar"],
    javabuilder = ["@bazel_tools//tools/jdk:javabuilder"],
    singlejar = ["@bazel_tools//tools/jdk:singlejar"],
    genclass = ["@bazel_tools//tools/jdk:genclass"],
    ijar = ["@bazel_tools//tools/jdk:ijar"],
    header_compiler = ["@bazel_tools//tools/jdk:turbine"],
    header_compiler_direct = ["@bazel_tools//tools/jdk:turbine_direct"],
    forcibly_disable_header_compilation = False,
)

# Error Prone errors enabled by default; see ../.bazelrc for how this is
# enabled. This warnings list is originally based on:
# https://github.com/bazelbuild/BUILD_file_generator/blob/master/tools/bazel_defs/java.bzl
# However, feel free to add any additional errors. Thus far they have all been pretty useful.
# TODO(davido): Enable ImmutableAnnotationChecker again when these issues are fixed:
# https://github.com/google/error-prone/issues/1348
# https://github.com/bazelbuild/bazel/issues/9378
java_package_configuration(
    name = "error_prone",
    javacopts = [
        "-XepDisableWarningsInGeneratedCode",
        "-Xep:AmbiguousMethodReference:ERROR",
        "-Xep:AutoValueFinalMethods:ERROR",
        "-Xep:BadAnnotationImplementation:ERROR",
        "-Xep:BadComparable:ERROR",
        "-Xep:BoxedPrimitiveConstructor:ERROR",
        "-Xep:CannotMockFinalClass:ERROR",
        "-Xep:ClassCanBeStatic:ERROR",
        "-Xep:ClassNewInstance:ERROR",
        "-Xep:DateFormatConstant:ERROR",
        "-Xep:DefaultCharset:ERROR",
        "-Xep:DoubleCheckedLocking:ERROR",
        "-Xep:ElementsCountedInLoop:ERROR",
        "-Xep:EqualsHashCode:ERROR",
        "-Xep:EqualsIncompatibleType:ERROR",
        "-Xep:ExpectedExceptionChecker:ERROR",
        "-Xep:Finally:ERROR",
        "-Xep:FloatingPointLiteralPrecision:ERROR",
        "-Xep:FormatStringAnnotation:ERROR",
        "-Xep:FragmentInjection:ERROR",
        "-Xep:FragmentNotInstantiable:ERROR",
        "-Xep:FunctionalInterfaceClash:ERROR",
        "-Xep:FutureReturnValueIgnored:ERROR",
        "-Xep:GetClassOnEnum:ERROR",
        "-Xep:ImmutableAnnotationChecker:OFF",
        "-Xep:ImmutableEnumChecker:ERROR",
        "-Xep:IncompatibleModifiers:ERROR",
        "-Xep:InjectOnConstructorOfAbstractClass:ERROR",
        "-Xep:InputStreamSlowMultibyteRead:ERROR",
        "-Xep:IterableAndIterator:ERROR",
        "-Xep:JUnit3FloatingPointComparisonWithoutDelta:ERROR",
        "-Xep:JUnitAmbiguousTestClass:ERROR",
        "-Xep:LiteralClassName:ERROR",
        "-Xep:MissingCasesInEnumSwitch:ERROR",
        "-Xep:MissingFail:ERROR",
        "-Xep:MissingOverride:ERROR",
        "-Xep:MutableConstantField:ERROR",
        "-Xep:NarrowingCompoundAssignment:ERROR",
        "-Xep:NonAtomicVolatileUpdate:ERROR",
        "-Xep:NonOverridingEquals:ERROR",
        "-Xep:NullableConstructor:ERROR",
        "-Xep:NullablePrimitive:ERROR",
        "-Xep:NullableVoid:ERROR",
        "-Xep:ObjectToString:ERROR",
        "-Xep:OperatorPrecedence:ERROR",
        "-Xep:OverridesGuiceInjectableMethod:ERROR",
        "-Xep:PreconditionsInvalidPlaceholder:ERROR",
        "-Xep:ProtoFieldPreconditionsCheckNotNull:ERROR",
        "-Xep:ProtocolBufferOrdinal:ERROR",
        "-Xep:ReferenceEquality:ERROR",
        "-Xep:RequiredModifiers:ERROR",
        "-Xep:ShortCircuitBoolean:ERROR",
        "-Xep:SimpleDateFormatConstant:ERROR",
        "-Xep:StaticGuardedByInstance:ERROR",
        "-Xep:StringEquality:ERROR",
        "-Xep:SynchronizeOnNonFinalField:ERROR",
        "-Xep:TruthConstantAsserts:ERROR",
        "-Xep:TypeParameterShadowing:ERROR",
        "-Xep:TypeParameterUnusedInFormals:ERROR",
        "-Xep:URLEqualsHashCode:ERROR",
        "-Xep:UnsynchronizedOverridesSynchronized:ERROR",
        "-Xep:UnusedException:ERROR",
        "-Xep:WaitNotInLoop:ERROR",
        "-Xep:WildcardImport:ERROR",
    ],
    packages = ["error_prone_packages"],
)

package_group(
    name = "error_prone_packages",
    packages = [
        "//java/...",
        "//javatests/...",
        "//plugins/codemirror-editor/...",
        "//plugins/commit-message-length-validator/...",
        "//plugins/delete-project/...",
        "//plugins/download-commands/...",
        "//plugins/gitiles/...",
        "//plugins/hooks/...",
        "//plugins/plugin-manager/...",
        "//plugins/replication/...",
        "//plugins/reviewnotes/...",
        "//plugins/singleusergroup/...",
        "//plugins/webhooks/...",
    ],
)
