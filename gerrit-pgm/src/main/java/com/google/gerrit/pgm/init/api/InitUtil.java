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

package com.google.gerrit.pgm.init.api;

import static com.google.gerrit.common.FileUtil.modified;

import com.google.common.io.ByteStreams;
import com.google.gerrit.common.Die;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.SystemReader;

/** Utility functions to help initialize a site. */
public class InitUtil {
  public static Die die(String why) {
    return new Die(why);
  }

  public static Die die(String why, Throwable cause) {
    return new Die(why, cause);
  }

  public static void savePublic(final FileBasedConfig sec) throws IOException {
    if (modified(sec)) {
      sec.save();
    }
  }

  public static void mkdir(File file) {
    mkdir(file.toPath());
  }

  public static void mkdir(Path path) {
    if (Files.isDirectory(path)) {
      return;
    }
    try {
      Files.createDirectory(path);
    } catch (IOException e) {
      throw die("Cannot make directory " + path, e);
    }
  }

  public static String version() {
    return com.google.gerrit.common.Version.getVersion();
  }

  public static String username() {
    return System.getProperty("user.name");
  }

  public static String hostname() {
    return SystemReader.getInstance().getHostname();
  }

  public static boolean isLocal(final String hostname) {
    try {
      return InetAddress.getByName(hostname).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  public static String dnOf(String name) {
    if (name != null) {
      int p = name.indexOf("://");
      if (0 < p) {
        name = name.substring(p + 3);
      }

      p = name.indexOf(".");
      if (0 < p) {
        name = name.substring(p + 1);
        name = "DC=" + name.replaceAll("\\.", ",DC=");
      } else {
        name = null;
      }
    }
    return name;
  }

  public static String domainOf(String name) {
    if (name != null) {
      int p = name.indexOf("://");
      if (0 < p) {
        name = name.substring(p + 3);
      }
      p = name.indexOf(".");
      if (0 < p) {
        name = name.substring(p + 1);
      }
    }
    return name;
  }

  public static void extract(Path dst, Class<?> sibling, String name) throws IOException {
    try (InputStream in = open(sibling, name)) {
      if (in != null) {
        copy(dst, ByteStreams.toByteArray(in));
      }
    }
  }

  private static InputStream open(final Class<?> sibling, final String name) {
    final InputStream in = sibling.getResourceAsStream(name);
    if (in == null) {
      String pkg = sibling.getName();
      int end = pkg.lastIndexOf('.');
      if (0 < end) {
        pkg = pkg.substring(0, end + 1);
        pkg = pkg.replace('.', '/');
      } else {
        pkg = "";
      }
      System.err.println("warn: Cannot read " + pkg + name);
      return null;
    }
    return in;
  }

  public static void copy(Path dst, byte[] buf) throws FileNotFoundException, IOException {
    // If the file already has the content we want to put there,
    // don't attempt to overwrite the file.
    //
    try (InputStream in = Files.newInputStream(dst)) {
      if (Arrays.equals(buf, ByteStreams.toByteArray(in))) {
        return;
      }
    } catch (NoSuchFileException notFound) {
      // Fall through and write the file.
    }

    Files.createDirectories(dst.getParent());
    LockFile lf = new LockFile(dst.toFile());
    if (!lf.lock()) {
      throw new IOException("Cannot lock " + dst);
    }
    try {
      try (InputStream in = new ByteArrayInputStream(buf);
          OutputStream out = lf.getOutputStream()) {
        ByteStreams.copy(in, out);
      }
      if (!lf.commit()) {
        throw new IOException("Cannot commit " + dst);
      }
    } finally {
      lf.unlock();
    }
  }

  public static URI toURI(String url) throws URISyntaxException {
    final URI u = new URI(url);
    if (isAnyAddress(u)) {
      // If the URL uses * it means all addresses on this system, use the
      // current hostname instead in the returned URI.
      //
      final int s = url.indexOf('*');
      url = url.substring(0, s) + hostname() + url.substring(s + 1);
    }
    return new URI(url);
  }

  public static boolean isAnyAddress(final URI u) {
    return u.getHost() == null
        && (u.getAuthority().equals("*") || u.getAuthority().startsWith("*:"));
  }

  public static int portOf(final URI uri) {
    int port = uri.getPort();
    if (port < 0) {
      port = "https".equals(uri.getScheme()) ? 443 : 80;
    }
    return port;
  }

  private InitUtil() {}
}
