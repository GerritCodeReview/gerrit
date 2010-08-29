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

import com.google.gerrit.pgm.util.Die;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.SystemReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Utility functions to help initialize a site. */
class InitUtil {
  static Die die(String why) {
    return new Die(why);
  }

  static Die die(String why, Throwable cause) {
    return new Die(why, cause);
  }

  static void savePublic(final FileBasedConfig sec) throws IOException {
    if (modified(sec)) {
      sec.save();
    }
  }

  static void saveSecure(final FileBasedConfig sec) throws IOException {
    if (modified(sec)) {
      final byte[] out = Constants.encode(sec.toText());
      final File path = sec.getFile();
      final LockFile lf = new LockFile(path, FS.DETECTED);
      if (!lf.lock()) {
        throw new IOException("Cannot lock " + path);
      }
      try {
        chmod(0600, new File(path.getParentFile(), path.getName() + ".lock"));
        lf.write(out);
        if (!lf.commit()) {
          throw new IOException("Cannot commit write to " + path);
        }
      } finally {
        lf.unlock();
      }
    }
  }

  private static boolean modified(FileBasedConfig cfg) throws IOException {
    byte[] curVers;
    try {
      curVers = IO.readFully(cfg.getFile());
    } catch (FileNotFoundException notFound) {
      return true;
    }

    byte[] newVers = Constants.encode(cfg.toText());
    return !Arrays.equals(curVers, newVers);
  }

  static void mkdir(final File path) {
    if (!path.isDirectory() && !path.mkdir()) {
      throw die("Cannot make directory " + path);
    }
  }

  static void chmod(final int mode, final File path) {
    path.setReadable(false, false /* all */);
    path.setWritable(false, false /* all */);
    path.setExecutable(false, false /* all */);

    path.setReadable((mode & 0400) == 0400, true /* owner only */);
    path.setWritable((mode & 0200) == 0200, true /* owner only */);
    if (path.isDirectory() || (mode & 0100) == 0100) {
      path.setExecutable(true, true /* owner only */);
    }

    if ((mode & 0044) == 0044) {
      path.setReadable(true, false /* all */);
    }
    if ((mode & 0011) == 0011) {
      path.setExecutable(true, false /* all */);
    }
  }

  static String version() {
    return com.google.gerrit.common.Version.getVersion();
  }

  static String username() {
    return System.getProperty("user.name");
  }

  static String hostname() {
    return SystemReader.getInstance().getHostname();
  }

  static boolean isLocal(final String hostname) {
    try {
      return InetAddress.getByName(hostname).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  static String dnOf(String name) {
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

  static String domainOf(String name) {
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

  static void extract(final File dst, final Class<?> sibling,
      final String name) throws IOException {
    final InputStream in = open(sibling, name);
    if (in != null) {
      ByteBuffer buf = IO.readWholeStream(in, 8192);
      copy(dst, buf);
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

  static void copy(final File dst, final ByteBuffer buf)
      throws FileNotFoundException, IOException {
    // If the file already has the content we want to put there,
    // don't attempt to overwrite the file.
    //
    try {
      if (buf.equals(ByteBuffer.wrap(IO.readFully(dst)))) {
        return;
      }
    } catch (FileNotFoundException notFound) {
      // Fall through and write the file.
    }

    dst.getParentFile().mkdirs();
    LockFile lf = new LockFile(dst, FS.DETECTED);
    if (!lf.lock()) {
      throw new IOException("Cannot lock " + dst);
    }
    try {
      final OutputStream out = lf.getOutputStream();
      try {
        final byte[] tmp = new byte[4096];
        while (0 < buf.remaining()) {
          int n = Math.min(buf.remaining(), tmp.length);
          buf.get(tmp, 0, n);
          out.write(tmp, 0, n);
        }
      } finally {
        out.close();
      }
      if (!lf.commit()) {
        throw new IOException("Cannot commit " + dst);
      }
    } finally {
      lf.unlock();
    }
  }

  static URI toURI(String url) throws URISyntaxException {
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

  static boolean isAnyAddress(final URI u) {
    return u.getHost() == null
        && (u.getAuthority().equals("*") || u.getAuthority().startsWith("*:"));
  }

  static int portOf(final URI uri) {
    int port = uri.getPort();
    if (port < 0) {
      port = "https".equals(uri.getScheme()) ? 443 : 80;
    }
    return port;
  }

  private InitUtil() {
  }
}
