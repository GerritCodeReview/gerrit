// (Copied from JGit org.eclipse.jgit.pgm.Main)
// Copyright (C) 2006, Robin Rosenberg <robin.rosenberg@dewire.com>
// Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or
// without modification, are permitted provided that the following
// conditions are met:
//
// - Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//
// - Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following
// disclaimer in the documentation and/or other materials provided
// with the distribution.
//
// - Neither the name of the Eclipse Foundation, Inc. nor the
// names of its contributors may be used to endorse or promote
// products derived from this software without specific prior
// written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
// OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
// NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
// ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.google.gerrit.pgm.util;

import java.net.MalformedURLException;
import java.net.URL;
import org.eclipse.jgit.util.CachedAuthenticator;

final class ProxyUtil {
  /**
   * Configure the JRE's standard HTTP based on {@code http_proxy}.
   *
   * <p>The popular libcurl library honors the {@code http_proxy} environment variable as a means of
   * specifying an HTTP proxy for requests made behind a firewall. This is not natively recognized
   * by the JRE, so this method can be used by command line utilities to configure the JRE before
   * the first request is sent.
   *
   * @throws MalformedURLException the value in {@code http_proxy} is unsupportable.
   */
  static void configureHttpProxy() throws MalformedURLException {
    final String s = System.getenv("http_proxy");
    if (s == null || s.equals("")) {
      return;
    }

    final URL u = new URL((!s.contains("://")) ? "http://" + s : s);
    if (!"http".equals(u.getProtocol())) {
      throw new MalformedURLException("Invalid http_proxy: " + s + ": Only http supported.");
    }

    final String proxyHost = u.getHost();
    final int proxyPort = u.getPort();

    System.setProperty("http.proxyHost", proxyHost);
    if (proxyPort > 0) {
      System.setProperty("http.proxyPort", String.valueOf(proxyPort));
    }

    final String userpass = u.getUserInfo();
    if (userpass != null && userpass.contains(":")) {
      final int c = userpass.indexOf(':');
      final String user = userpass.substring(0, c);
      final String pass = userpass.substring(c + 1);
      CachedAuthenticator.add(
          new CachedAuthenticator.CachedAuthentication(proxyHost, proxyPort, user, pass));
    }
  }

  ProxyUtil() {}
}
