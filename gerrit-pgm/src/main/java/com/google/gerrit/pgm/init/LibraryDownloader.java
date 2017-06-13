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

import com.google.common.hash.Funnels;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.gerrit.common.Die;
import com.google.gerrit.common.IoUtil;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.util.HttpSupport;

/** Get optional or required 3rd party library files into $site_path/lib. */
class LibraryDownloader {
  private final ConsoleUI ui;
  private final Path lib_dir;
  private final StaleLibraryRemover remover;

  private boolean required;
  private String name;
  private String jarUrl;
  private String sha1;
  private String remove;
  private List<LibraryDownloader> needs;
  private LibraryDownloader neededBy;
  private Path dst;
  private boolean download; // download or copy
  private boolean exists;
  private boolean skipDownload;

  @Inject
  LibraryDownloader(ConsoleUI ui, SitePaths site, StaleLibraryRemover remover) {
    this.ui = ui;
    this.lib_dir = site.lib_dir;
    this.remover = remover;
    this.needs = new ArrayList<>(2);
  }

  void setName(String name) {
    this.name = name;
  }

  void setJarUrl(String url) {
    this.jarUrl = url;
    download = jarUrl.startsWith("http");
  }

  void setSHA1(String sha1) {
    this.sha1 = sha1;
  }

  void setRemove(String remove) {
    this.remove = remove;
  }

  void addNeeds(LibraryDownloader lib) {
    needs.add(lib);
  }

  void setSkipDownload(boolean skipDownload) {
    this.skipDownload = skipDownload;
  }

  void downloadRequired() {
    setRequired(true);
    download();
  }

  void downloadOptional() {
    required = false;
    download();
  }

  private void setRequired(boolean r) {
    required = r;
    for (LibraryDownloader d : needs) {
      d.setRequired(r);
    }
  }

  private void download() {
    if (skipDownload) {
      return;
    }

    if (jarUrl == null || !jarUrl.contains("/")) {
      throw new IllegalStateException("Invalid JarUrl for " + name);
    }

    final String jarName = jarUrl.substring(jarUrl.lastIndexOf('/') + 1);
    if (jarName.contains("/") || jarName.contains("\\")) {
      throw new IllegalStateException("Invalid JarUrl: " + jarUrl);
    }

    if (name == null) {
      name = jarName;
    }

    dst = lib_dir.resolve(jarName);
    if (Files.exists(dst)) {
      exists = true;
    } else if (shouldGet()) {
      doGet();
    }

    if (exists) {
      for (LibraryDownloader d : needs) {
        d.neededBy = this;
        d.downloadRequired();
      }
    }
  }

  private boolean shouldGet() {
    if (ui.isBatch()) {
      return required;
    }
    final StringBuilder msg = new StringBuilder();
    msg.append("\n");
    msg.append("Gerrit Code Review is not shipped with %s\n");
    if (neededBy != null) {
      msg.append(String.format("** This library is required by %s. **\n", neededBy.name));
    } else if (required) {
      msg.append("**  This library is required for your configuration. **\n");
    } else {
      msg.append("  If available, Gerrit can take advantage of features\n");
      msg.append("  in the library, but will also function without it.\n");
    }
    msg.append(String.format("%s and install it now", download ? "Download" : "Copy"));
    return ui.yesno(true, msg.toString(), name);
  }

  private void doGet() {
    if (!Files.exists(lib_dir)) {
      try {
        Files.createDirectories(lib_dir);
      } catch (IOException e) {
        throw new Die("Cannot create " + lib_dir, e);
      }
    }

    try {
      remover.remove(remove);
      if (download) {
        doGetByHttp();
      } else {
        doGetByLocalCopy();
      }
      verifyFileChecksum();
    } catch (IOException err) {
      try {
        Files.delete(dst);
      } catch (IOException e) {
        // Delete failed; leave alone.
      }

      if (ui.isBatch()) {
        throw new Die("error: Cannot get " + jarUrl, err);
      }

      System.err.println();
      System.err.println();
      System.err.println("error: " + err.getMessage());
      System.err.println("Please download:");
      System.err.println();
      System.err.println("  " + jarUrl);
      System.err.println();
      System.err.println("and save as:");
      System.err.println();
      System.err.println("  " + dst.toAbsolutePath());
      System.err.println();
      System.err.flush();

      ui.waitForUser();

      if (Files.exists(dst)) {
        verifyFileChecksum();

      } else if (!ui.yesno(!required, "Continue without this library")) {
        throw new Die("aborted by user");
      }
    }

    if (Files.exists(dst)) {
      exists = true;
      IoUtil.loadJARs(dst);
    }
  }

  private void doGetByLocalCopy() throws IOException {
    System.err.print("Copying " + jarUrl + " ...");
    Path p = url2file(jarUrl);
    if (!Files.exists(p)) {
      StringBuilder msg =
          new StringBuilder()
              .append("\n")
              .append("Can not find the %s at this location: %s\n")
              .append("Please provide alternative URL");
      p = url2file(ui.readString(null, msg.toString(), name, jarUrl));
    }
    Files.copy(p, dst);
  }

  private static Path url2file(String urlString) throws IOException {
    final URL url = new URL(urlString);
    try {
      return Paths.get(url.toURI());
    } catch (URISyntaxException e) {
      return Paths.get(url.getPath());
    }
  }

  private void doGetByHttp() throws IOException {
    System.err.print("Downloading " + jarUrl + " ...");
    System.err.flush();
    try (InputStream in = openHttpStream(jarUrl);
        OutputStream out = Files.newOutputStream(dst)) {
      ByteStreams.copy(in, out);
      System.err.println(" OK");
      System.err.flush();
    } catch (IOException err) {
      deleteDst();
      System.err.println(" !! FAIL !!");
      System.err.flush();
      throw err;
    }
  }

  private static InputStream openHttpStream(String urlStr) throws IOException {
    ProxySelector proxySelector = ProxySelector.getDefault();
    URL url = new URL(urlStr);
    Proxy proxy = HttpSupport.proxyFor(proxySelector, url);
    HttpURLConnection c = (HttpURLConnection) url.openConnection(proxy);

    switch (HttpSupport.response(c)) {
      case HttpURLConnection.HTTP_OK:
        return c.getInputStream();

      case HttpURLConnection.HTTP_NOT_FOUND:
        throw new FileNotFoundException(url.toString());

      default:
        throw new IOException(
            url.toString() + ": " + HttpSupport.response(c) + " " + c.getResponseMessage());
    }
  }

  private void verifyFileChecksum() {
    if (sha1 == null) {
      System.err.println();
      System.err.flush();
      return;
    }
    Hasher h = Hashing.sha1().newHasher();
    try (InputStream in = Files.newInputStream(dst);
        OutputStream out = Funnels.asOutputStream(h)) {
      ByteStreams.copy(in, out);
    } catch (IOException e) {
      deleteDst();
      throw new Die("cannot checksum " + dst, e);
    }
    if (sha1.equals(h.hash().toString())) {
      System.err.println("Checksum " + dst.getFileName() + " OK");
      System.err.flush();
    } else if (ui.isBatch()) {
      deleteDst();
      throw new Die(dst + " SHA-1 checksum does not match");

    } else if (!ui.yesno(
        null /* force an answer */,
        "error: SHA-1 checksum does not match\nUse %s anyway", //
        dst.getFileName())) {
      deleteDst();
      throw new Die("aborted by user");
    }
  }

  private void deleteDst() {
    try {
      Files.delete(dst);
    } catch (IOException e) {
      System.err.println(" Failed to clean up lib: " + dst);
    }
  }
}
