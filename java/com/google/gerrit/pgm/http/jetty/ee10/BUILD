# EE10 (jakarta.servlet) hand-written overlays of the Jetty bootstrap files that
# diverge from a pure import rename: HiddenErrorHandler (Jetty-12 ee10 core
# ErrorHandler: handle(Request, Response, Callback) -> boolean) and JettyServer
# (ee10 ServletContextHandler/ServletHandler, Servlet-6 ambiguous-URI bypass).
# Same FQDN (package com.google.gerrit.pgm.http.jetty) as the canonical files,
# which are excluded from the to_jakarta transform. Only one flavour is on a
# classpath at a time. Consumed by //java/com/google/gerrit/pgm/http/jetty:jetty-ee10.
filegroup(
    name = "overlay",
    srcs = [
        "HiddenErrorHandler.java",
        "JettyServerFlavour.java",
    ],
    visibility = ["//java/com/google/gerrit/pgm/http/jetty:__pkg__"],
)
