py_binary(
    name = "merge_jars",
    srcs = ["merge_jars.py"],
    main = "merge_jars.py",
    visibility = ["//visibility:public"],
)

java_binary(
    name = "google-java-format",
    main_class = "com.google.googlejavaformat.java.Main",
    runtime_deps = [ "@google_java_format//jar", ],
    visibility = [ "//visibility:public" ],
)
