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

import com.google.common.base.Strings;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.pgm.init.api.InitUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import org.eclipse.jgit.lib.Config;

/** Opens the user's web browser to the web UI. */
public class Browser {
  private final Config cfg;

  @Inject
  Browser(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
  }

  public void open() throws Exception {
    open(null /* root page */);
  }

  public void open(String link) throws Exception {
    String url = cfg.getString("gerrit", null, "canonicalWebUrl");
    if (url == null) {
      url = cfg.getString("httpd", null, "listenUrl");
      if (url == null) {
        return;
      }
      if (url.startsWith("proxy-")) {
        url = url.substring("proxy-".length());
      }
    }

    final URI uri;
    try {
      uri = InitUtil.toURI(url);
    } catch (URISyntaxException e) {
      System.err.println("error: invalid httpd.listenUrl: " + url);
      return;
    }
    waitForServer(uri);
    openBrowser(uri, link);
  }

  private void waitForServer(URI uri) throws IOException {
    String host = uri.getHost();
    int port = InitUtil.portOf(uri);
    System.err.format("Waiting for server on %s:%d ... ", host, port);
    System.err.flush();
    for (; ; ) {
      Socket s;
      try {
        s = new Socket(host, port);
      } catch (IOException e) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ie) {
          // Ignored
        }
        continue;
      }
      s.close();
      break;
    }
    System.err.println("OK");
  }

  private String resolveUrl(URI uri, String link) {
    String url = cfg.getString("gerrit", null, "canonicalWebUrl");
    if (Strings.isNullOrEmpty(url)) {
      url = uri.toString();
    }
    if (!url.endsWith("/")) {
      url += "/";
    }
    if (!Strings.isNullOrEmpty(link)) {
      url += "#" + link;
    }
    return url;
  }

  private void openBrowser(URI uri, String link) {
    String url = resolveUrl(uri, link);
    System.err.format("Opening %s ...", url);
    System.err.flush();
    try {
      org.h2.tools.Server.openBrowser(url);
      System.err.println("OK");
    } catch (Exception e) {
      System.err.println("FAILED");
      System.err.println("Open Gerrit with a JavaScript capable browser:");
      System.err.println("  " + url);
    }
  }
}
