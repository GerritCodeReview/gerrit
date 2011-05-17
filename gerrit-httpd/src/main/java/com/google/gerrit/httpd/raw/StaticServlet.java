// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import com.google.gerrit.server.config.SitePaths;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.util.IO;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Sends static content from the site 's <code>static/</code> subdirectory. */
@SuppressWarnings("serial")
@Singleton
public class StaticServlet extends HttpServlet {
  private static final long MAX_AGE = 12 * 60 * 60 * 1000L/* milliseconds */;
  private static final String CACHE_CTRL =
      "public, max-age=" + (MAX_AGE / 1000L);

  private static final HashMap<String, String> MIME_TYPES =
      new HashMap<String, String>();
  static {
    MIME_TYPES.put("html", "text/html");
    MIME_TYPES.put("htm", "text/html");
    MIME_TYPES.put("js", "application/x-javascript");
    MIME_TYPES.put("css", "text/css");
    MIME_TYPES.put("rtf", "text/rtf");
    MIME_TYPES.put("txt", "text/plain");
    MIME_TYPES.put("text", "text/plain");
    MIME_TYPES.put("pdf", "application/pdf");
    MIME_TYPES.put("jpeg", "image/jpeg");
    MIME_TYPES.put("jpg", "image/jpeg");
    MIME_TYPES.put("gif", "image/gif");
    MIME_TYPES.put("png", "image/png");
    MIME_TYPES.put("tiff", "image/tiff");
    MIME_TYPES.put("tif", "image/tiff");
    MIME_TYPES.put("svg", "image/svg+xml");
  }

  private static String contentType(final String name) {
    final int dot = name.lastIndexOf('.');
    final String ext = 0 < dot ? name.substring(dot + 1) : "";
    final String type = MIME_TYPES.get(ext);
    return type != null ? type : "application/octet-stream";
  }

  private static byte[] readFile(final File p) throws IOException {
    final FileInputStream in = new FileInputStream(p);
    try {
      final byte[] r = new byte[(int) in.getChannel().size()];
      IO.readFully(in, r, 0, r.length);
      return r;
    } finally {
      in.close();
    }
  }

  private static byte[] compress(final byte[] raw) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final GZIPOutputStream gz = new GZIPOutputStream(out);
    gz.write(raw);
    gz.finish();
    gz.flush();
    return out.toByteArray();
  }

  private final File staticBase;
  private final String staticBasePath;

  @Inject
  StaticServlet(final SitePaths site) {
    File f;
    try {
      f = site.static_dir.getCanonicalFile();
    } catch (IOException e) {
      f = site.static_dir.getAbsoluteFile();
    }
    staticBase = f;
    staticBasePath = staticBase.getPath() + File.separator;
  }

  private File local(final HttpServletRequest req) {
    final String name = req.getPathInfo();
    if (name.length() < 2 || !name.startsWith("/") || isUnreasonableName(name)) {
      // Too short to be a valid file name, or doesn't start with
      // the path info separator like we expected.
      //
      return null;
    }

    final File p = new File(staticBase, name.substring(1));

    // Ensure that the requested file is *actually* within the static dir base.
    try {
      if (!p.getCanonicalFile().getPath().startsWith(staticBasePath))
        return null;
    } catch (IOException e) {
        return null;
    }

    return p.isFile() ? p : null;
  }

  private static boolean isUnreasonableName(String name) {
    if (name.charAt(name.length() -1) == '/') return true; // no suffix
    if (name.indexOf('\\') >= 0) return true; // no windows/dos stlye paths
    if (name.startsWith("../")) return true; // no "l../etc/passwd"
    if (name.contains("/../")) return true; // no "foo/../etc/passwd"
    if (name.contains("/./")) return true; // "foo/./foo" is insane to ask
    if (name.contains("//")) return true; // windows UNC path can be "//..."

    return false; // is a reasonable name
  }

  @Override
  protected long getLastModified(final HttpServletRequest req) {
    final File p = local(req);
    return p != null ? p.lastModified() : -1;
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    final File p = local(req);
    if (p == null) {
      rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    final String type = contentType(p.getName());
    final byte[] tosend;
    if (!type.equals("application/x-javascript")
        && RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader("Content-Encoding", "gzip");
      tosend = compress(readFile(p));
    } else {
      tosend = readFile(p);
    }

    rsp.setHeader("Cache-Control", CACHE_CTRL);
    rsp.setDateHeader("Expires", System.currentTimeMillis() + MAX_AGE);
    rsp.setDateHeader("Last-Modified", p.lastModified());
    rsp.setContentType(type);
    rsp.setContentLength(tosend.length);
    final OutputStream out = rsp.getOutputStream();
    try {
      out.write(tosend);
    } finally {
      out.close();
    }
  }
}
