// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;

/** Opens the user's web browser to the web UI. */
public class Browser {
  private final Config cfg;

  @Inject
  Browser(final @GerritServerConfig Config cfg) {
    this.cfg = cfg;
  }

  public void open() throws Exception {
    open(null /* root page */);
  }

  public void open(final String link) throws Exception {
    String url = cfg.getString("httpd", null, "listenUrl");
    if (url == null) {
      return;
    }

    if (url.startsWith("proxy-")) {
      url = url.substring("proxy-".length());
    }

    final URI uri;
    try {
      uri = InitUtil.toURI(url);
    } catch (URISyntaxException e) {
      System.err.println("error: invalid httpd.listenUrl: " + url);
      return;
    }
    final String hostname = uri.getHost();
    final int port = InitUtil.portOf(uri);

    System.err.print("Waiting for server to start ... ");
    System.err.flush();
    for (;;) {
      final Socket s;
      try {
        s = new Socket(hostname, port);
      } catch (IOException e) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ie) {
        }
        continue;
      }
      s.close();
      break;
    }
    System.err.println("OK");

    url = cfg.getString("gerrit", null, "canonicalWebUrl");
    if (url == null || url.isEmpty()) {
      url = uri.toString();
    }
    if (!url.endsWith("/")) {
      url += "/";
    }
    if (link != null && !link.isEmpty()) {
      url += "#" + link;
    }
    System.err.println("Opening browser ...");
    org.h2.tools.Server.openBrowser(url);
  }
}
