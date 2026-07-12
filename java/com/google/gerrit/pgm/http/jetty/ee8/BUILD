# EE8 (javax.servlet) hand-written overlays of the Jetty bootstrap files that
# diverge from a pure import rename: HiddenErrorHandler (Jetty-12 ee8 nested
# ErrorHandler with baseRequest.setHandled()) and JettyServer (ee8
# ServletContextHandler/ServletHandler on the nested API). Same FQDN
# (package com.google.gerrit.pgm.http.jetty) as the canonical jakarta files,
# which are excluded from the to_javax transform. Only one flavour is on a
# classpath at a time. Consumed by //java/com/google/gerrit/pgm/http/jetty:jetty-ee8.
filegroup(
    name = "overlay",
    srcs = [
        "HiddenErrorHandler.java",
        "JettyServerFlavour.java",
        "SameSiteFilter.java",
    ],
    visibility = ["//java/com/google/gerrit/pgm/http/jetty:__pkg__"],
)
