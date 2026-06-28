// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// EE10 (jakarta.servlet) implementation of the Jetty-12 flavour seam used by the
// flavour-neutral JettyServer. Same FQDN (package com.google.gerrit.pgm.http.jetty)
// as the EE8 JettyServerFlavour; only one is on a classpath at a time. Excluded
// from the to_jakarta transform (its body differs in real Jetty-12 ee10 API, not
// just imports).
package com.google.gerrit.pgm.http.jetty;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;

final class JettyServerFlavour {
  /** ee10's ServletContextHandler is itself a Handler; install it as-is. */
  static Handler toCoreHandler(ServletContextHandler app) {
    return app;
  }

  /**
   * Decode ambiguous URIs for this context's servlet handler (Servlet 6
   * tightened ambiguous-path rules; Gerrit opts back out).
   */
  static void configureServletHandler(ServletContextHandler app) {
    ServletHandler handler = new ServletHandler();
    handler.setDecodeAmbiguousURIs(true);
    app.setServletHandler(handler);
  }

  /**
   * Bypass Servlet 6 ambiguous-URI rules on every servlet handler in the server.
   *
   * @see <a href="https://github.com/jakartaee/servlet/issues/18">Servlet 6 URI
   *     changes</a>
   */
  static void applyServlet6Compliance(Server httpd) {
    httpd
        .getContainedBeans(ServletHandler.class)
        .forEach(handler -> handler.setDecodeAmbiguousURIs(true));
  }

  private JettyServerFlavour() {}
}
