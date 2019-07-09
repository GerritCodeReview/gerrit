load(
    "@bazel_tools//tools/jdk:default_java_toolchain.bzl",
    "JDK9_JVM_OPTS",
    "default_java_toolchain",
)

exports_files(["nongoogle.bzl"])

py_binary(
    name = "merge_jars",
    srcs = ["merge_jars.py"],
    main = "merge_jars.py",
    visibility = ["//visibility:public"],
)

default_java_toolchain(
    name = "error_prone_warnings_toolchain",
    bootclasspath = ["@bazel_tools//tools/jdk:platformclasspath.jar"],
    jvm_opts = JDK9_JVM_OPTS,
    package_configuration = [
        ":error_prone",
    ],
    visibility = ["//visibility:public"],
)

# Error Prone errors enabled by default; see ../.bazelrc for how this is
# enabled. This warnings list is originally based on:
# https://github.com/bazelbuild/BUILD_file_generator/blob/master/tools/bazel_defs/java.bzl
# However, feel free to add any additional errors. Thus far they have all been pretty useful.
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
        "-Xep:DateFormatConstant:ERROR",
        "-Xep:DefaultCharset:ERROR",
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
        "-Xep:MissingFail:ERROR",
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
        "//java/...",
        "//javatests/...",
        "//plugins/codemirror-editor/...",
        "//plugins/commit-message-length-validator/...",
        "//plugins/download-commands/...",
        "//plugins/hooks/...",
        "//plugins/replication/...",
        "//plugins/reviewnotes/...",
        "//plugins/singleusergroup/...",
    ],
)
