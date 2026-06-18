# Guard: //:headless must not depend on the canonical Jakarta JGit
# servlet jar. //lib:jgit-servlet exports the EE8 bridge; both jars
# share class FQNs and cannot co-exist on a classpath.

genquery(
    name = "original_jgit_servlet_path",
    expression = "somepath(//:headless, @jgit//org.eclipse.jgit.http.server:jgit-servlet)",
    scope = [
        "//:headless",
        "@jgit//org.eclipse.jgit.http.server:jgit-servlet",
    ],
)

sh_test(
    name = "classpath_duplicates_test",
    srcs = ["classpath_duplicates_test.sh"],
    args = ["$(location :original_jgit_servlet_path)"],
    data = [":original_jgit_servlet_path"],
)
