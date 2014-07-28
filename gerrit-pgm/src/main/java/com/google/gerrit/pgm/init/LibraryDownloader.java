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
import com.google.common.io.Files;
import com.google.gerrit.common.Die;
import com.google.gerrit.pgm.util.IoUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.HttpSupport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Get optional or required 3rd party library files into $site_path/lib. */
class LibraryDownloader {
  private final ConsoleUI ui;
  private final File lib_dir;

  private boolean required;
  private String name;
  private String jarUrl;
  private String sha1;
  private String remove;
  private List<LibraryDownloader> needs;
  private LibraryDownloader neededBy;
  private File dst;
  private boolean download; // download or copy
  private boolean exists;

  @Inject
  LibraryDownloader(ConsoleUI ui, SitePaths site) {
    this.ui = ui;
    this.lib_dir = site.lib_dir;
    this.needs = new ArrayList<>(2);
  }

  void setName(final String name) {
    this.name = name;
  }

  void setJarUrl(final String url) {
    this.jarUrl = url;
    download = jarUrl.startsWith("http");
  }

  void setSHA1(final String sha1) {
    this.sha1 = sha1;
  }

  void setRemove(String remove) {
    this.remove = remove;
  }

  void addNeeds(LibraryDownloader lib) {
    needs.add(lib);
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

    dst = new File(lib_dir, jarName);
    if (dst.exists()) {
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

    } else {
      final StringBuilder msg = new StringBuilder();
      msg.append("\n");
      msg.append("Gerrit Code Review is not shipped with %s\n");
      if (neededBy != null) {
        msg.append(String.format(
            "** This library is required by %s. **\n",
            neededBy.name));
      } else if (required) {
        msg.append("**  This library is required for your configuration. **\n");
      } else {
        msg.append("  If available, Gerrit can take advantage of features\n");
        msg.append("  in the library, but will also function without it.\n");
      }
      msg.append(String.format(
          "%s and install it now", download ? "Download" : "Copy"));
      return ui.yesno(true, msg.toString(), name);
    }
  }

  private void doGet() {
    if (!lib_dir.exists() && !lib_dir.mkdirs()) {
      throw new Die("Cannot create " + lib_dir);
    }

    try {
      removeStaleVersions();
      if (download) {
        doGetByHttp();
      } else {
        doGetByLocalCopy();
      }
      verifyFileChecksum();
    } catch (IOException err) {
      dst.delete();

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
      System.err.println("  " + dst.getAbsolutePath());
      System.err.println();
      System.err.flush();

      ui.waitForUser();

      if (dst.exists()) {
        verifyFileChecksum();

      } else if (!ui.yesno(!required, "Continue without this library")) {
        throw new Die("aborted by user");
      }
    }

    if (dst.exists()) {
      exists = true;
      IoUtil.loadJARs(dst);
    }
  }

  private void removeStaleVersions() {
    if (!Strings.isNullOrEmpty(remove)) {
      String[] names = lib_dir.list(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.matches("^" + remove + "$");
        }
      });
      if (names != null) {
        for (String old : names) {
          String bak = "." + old + ".backup";
          ui.message("Renaming %s to %s", old, bak);
          if (!new File(lib_dir, old).renameTo(new File(lib_dir, bak))) {
            throw new Die("cannot rename " + old);
          }
        }
      }
    }
  }

  private void doGetByLocalCopy() throws IOException {
    System.err.print("Copying " + jarUrl + " ...");
    File f = url2file(jarUrl);
    if (!f.exists()) {
      StringBuilder msg = new StringBuilder()
          .append("\n")
          .append("Can not find the %s at this location: %s\n")
          .append("Please provide alternative URL");
      f = url2file(ui.readString(null, msg.toString(), name, jarUrl));
    }
    Files.copy(f, dst);
  }

  private static File url2file(final String urlString) throws IOException {
    final URL url = new URL(urlString);
    try {
      return new File(url.toURI());
    } catch (URISyntaxException e) {
      return new File(url.getPath());
    }
  }

  private void doGetByHttp() throws IOException {
    System.err.print("Downloading " + jarUrl + " ...");
    System.err.flush();
    try {
      final ProxySelector proxySelector = ProxySelector.getDefault();
      final URL url = new URL(jarUrl);
      final Proxy proxy = HttpSupport.proxyFor(proxySelector, url);
      final HttpURLConnection c = (HttpURLConnection) url.openConnection(proxy);
      final InputStream in;

      switch (HttpSupport.response(c)) {
        case HttpURLConnection.HTTP_OK:
          in = c.getInputStream();
          break;

        case HttpURLConnection.HTTP_NOT_FOUND:
          throw new FileNotFoundException(url.toString());

        default:
          throw new IOException(url.toString() + ": " + HttpSupport.response(c)
              + " " + c.getResponseMessage());
      }

      try {
        final OutputStream out = new FileOutputStream(dst);
        try {
          final byte[] buf = new byte[8192];
          int n;
          while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
          }
        } finally {
          out.close();
        }
      } finally {
        in.close();
      }
      System.err.println(" OK");
      System.err.flush();
    } catch (IOException err) {
      dst.delete();
      System.err.println(" !! FAIL !!");
      System.err.flush();
      throw err;
    }
  }

  private void verifyFileChecksum() {
    if (sha1 != null) {
      try {
        final MessageDigest md = MessageDigest.getInstance("SHA-1");
        final FileInputStream in = new FileInputStream(dst);
        try {
          final byte[] buf = new byte[8192];
          int n;
          while ((n = in.read(buf)) > 0) {
            md.update(buf, 0, n);
          }
        } finally {
          in.close();
        }

        if (sha1.equals(ObjectId.fromRaw(md.digest()).name())) {
          System.err.println("Checksum " + dst.getName() + " OK");
          System.err.flush();

        } else if (ui.isBatch()) {
          dst.delete();
          throw new Die(dst + " SHA-1 checksum does not match");

        } else if (!ui.yesno(null /* force an answer */,
            "error: SHA-1 checksum does not match\n" + "Use %s anyway",//
            dst.getName())) {
          dst.delete();
          throw new Die("aborted by user");
        }

      } catch (IOException checksumError) {
        dst.delete();
        throw new Die("cannot checksum " + dst, checksumError);

      } catch (NoSuchAlgorithmException checksumError) {
        dst.delete();
        throw new Die("cannot checksum " + dst, checksumError);
      }
    }
  }
}
