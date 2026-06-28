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

package com.google.gerrit.pgm.http.jetty;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;

/**
 * EE8 (javax.servlet) implementation of the small Jetty-12 flavour seam used by
 * the otherwise flavour-neutral {@link JettyServer}. The EE10 counterpart lives
 * in {@code ee10/JettyServerFlavour.java} (same FQDN, hand-written, excluded
 * from the to_jakarta transform); only one is on a classpath at a time.
 */
final class JettyServerFlavour {
  /** ee8's ServletContextHandler is a {@code Supplier<Handler>}; unwrap it. */
  static Handler toCoreHandler(ServletContextHandler app) {
    return app.get();
  }

  /** No Servlet-6 ambiguous-URI handling exists in Servlet 4. */
  static void configureServletHandler(ServletContextHandler app) {}

  /** No Servlet-6 ambiguous-URI handling exists in Servlet 4. */
  static void applyServlet6Compliance(Server httpd) {}

  private JettyServerFlavour() {}
}
