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

import static com.google.gerrit.common.FileUtil.chmod;
import static com.google.gerrit.pgm.init.api.InitUtil.die;
import static com.google.gerrit.pgm.init.api.InitUtil.domainOf;
import static com.google.gerrit.pgm.init.api.InitUtil.isAnyAddress;
import static com.google.gerrit.pgm.init.api.InitUtil.toURI;

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Initialize the {@code httpd} configuration section. */
@Singleton
class InitHttpd implements InitStep {
  private final ConsoleUI ui;
  private final SitePaths site;
  private final InitFlags flags;
  private final Section httpd;
  private final Section gerrit;

  @Inject
  InitHttpd(
      final ConsoleUI ui,
      final SitePaths site,
      final InitFlags flags,
      final Section.Factory sections) {
    this.ui = ui;
    this.site = site;
    this.flags = flags;
    this.httpd = sections.get("httpd", null);
    this.gerrit = sections.get("gerrit", null);
  }

  @Override
  public void run() throws IOException, InterruptedException {
    ui.header("HTTP Daemon");

    boolean proxy = false;
    boolean ssl = false;
    String address = "*";
    int port = -1;
    String context = "/";
    String listenUrl = httpd.get("listenUrl");
    if (listenUrl != null && !listenUrl.isEmpty()) {
      try {
        final URI uri = toURI(listenUrl);
        proxy = uri.getScheme().startsWith("proxy-");
        ssl = uri.getScheme().endsWith("https");
        address = isAnyAddress(new URI(listenUrl)) ? "*" : uri.getHost();
        port = uri.getPort();
        context = uri.getPath();
      } catch (URISyntaxException e) {
        System.err.println("warning: invalid httpd.listenUrl " + listenUrl);
      }
    }

    proxy = ui.yesno(proxy, "Behind reverse proxy");

    if (proxy) {
      ssl = ui.yesno(ssl, "Proxy uses SSL (https://)");
      context = ui.readString(context, "Subdirectory on proxy server");
    } else {
      ssl = ui.yesno(ssl, "Use SSL (https://)");
      context = "/";
    }

    address = ui.readString(address, "Listen on address");

    if (port < 0) {
      if (proxy) {
        port = 8081;
      } else if (ssl) {
        port = 8443;
      } else {
        port = 8080;
      }
    }
    port = ui.readInt(port, "Listen on port");

    final StringBuilder urlbuf = new StringBuilder();
    urlbuf.append(proxy ? "proxy-" : "");
    urlbuf.append(ssl ? "https" : "http");
    urlbuf.append("://");
    urlbuf.append(address);
    if (0 <= port) {
      urlbuf.append(":");
      urlbuf.append(port);
    }
    urlbuf.append(context);
    httpd.set("listenUrl", urlbuf.toString());

    URI uri;
    try {
      uri = toURI(httpd.get("listenUrl"));
      if (uri.getScheme().startsWith("proxy-")) {
        // If its a proxy URL, assume the reverse proxy is on our system
        // at the protocol standard ports (so omit the ports from the URL).
        //
        String s = uri.getScheme().substring("proxy-".length());
        uri = new URI(s + "://" + uri.getHost() + uri.getPath());
      }
    } catch (URISyntaxException e) {
      throw die("invalid httpd.listenUrl");
    }
    gerrit.string("Canonical URL", "canonicalWebUrl", uri.toString());
    generateSslCertificate();
  }

  private void generateSslCertificate() throws IOException, InterruptedException {
    final String listenUrl = httpd.get("listenUrl");

    if (!listenUrl.startsWith("https://")) {
      // We aren't responsible for SSL processing.
      //
      return;
    }

    String hostname;
    try {
      String url = gerrit.get("canonicalWebUrl");
      if (url == null || url.isEmpty()) {
        url = listenUrl;
      }
      hostname = toURI(url).getHost();
    } catch (URISyntaxException e) {
      System.err.println("Invalid httpd.listenUrl, not checking certificate");
      return;
    }

    Path store = site.ssl_keystore;
    if (!ui.yesno(!Files.exists(store), "Create new self-signed SSL certificate")) {
      return;
    }

    String ssl_pass = flags.sec.get("http", null, "sslKeyPassword");
    if (ssl_pass == null || ssl_pass.isEmpty()) {
      ssl_pass = SignedToken.generateRandomKey();
      flags.sec.set("httpd", null, "sslKeyPassword", ssl_pass);
    }

    hostname = ui.readString(hostname, "Certificate server name");
    final String validity = ui.readString("365", "Certificate expires in (days)");

    final String dname = "CN=" + hostname + ",OU=Gerrit Code Review,O=" + domainOf(hostname);

    Path tmpdir = site.etc_dir.resolve("tmp.sslcertgen");
    try {
      Files.createDirectory(tmpdir);
    } catch (IOException e) {
      throw die("Cannot create directory " + tmpdir, e);
    }
    chmod(0600, tmpdir);

    Path tmpstore = tmpdir.resolve("keystore");
    Runtime.getRuntime()
        .exec(
            new String[] {
              "keytool", //
              "-keystore",
              tmpstore.toAbsolutePath().toString(), //
              "-storepass",
              ssl_pass, //
              "-genkeypair", //
              "-alias",
              hostname, //
              "-keyalg",
              "RSA", //
              "-validity",
              validity, //
              "-dname",
              dname, //
              "-keypass",
              ssl_pass, //
            })
        .waitFor();
    chmod(0600, tmpstore);

    try {
      Files.move(tmpstore, store);
    } catch (IOException e) {
      throw die("Cannot rename " + tmpstore + " to " + store, e);
    }
    try {
      Files.delete(tmpdir);
    } catch (IOException e) {
      throw die("Cannot delete " + tmpdir, e);
    }
  }
}
