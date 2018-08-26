load("@bazel_tools//tools/jdk:default_java_toolchain.bzl", "default_java_toolchain")

py_binary(
    name = "merge_jars",
    srcs = ["merge_jars.py"],
    main = "merge_jars.py",
    visibility = ["//visibility:public"],
)

# See https://github.com/bazelbuild/bazel/issues/3427 for more context
default_java_toolchain(
    name = "error_prone_warnings_toolchain",
    bootclasspath = ["@bazel_tools//tools/jdk:platformclasspath.jar"],
    package_configuration = [
        ":error_prone",
    ],
    visibility = ["//visibility:public"],
)

# This EP warnings list borrowed from here:
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
        "//java/...",
        "//javatests/...",
    ],
)
