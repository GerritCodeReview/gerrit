exports_files([
    "license-map.py",
    "test_empty.sh",
    "test_license.sh",
])

java_binary(
    name = "google-java-format",
    main_class = "com.google.googlejavaformat.java.Main",
    visibility = ["//visibility:public"],
    runtime_deps = ["@google_java_format//jar"],
)
