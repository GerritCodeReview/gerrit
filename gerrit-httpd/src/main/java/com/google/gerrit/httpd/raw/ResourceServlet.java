// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;
import static com.google.common.net.HttpHeaders.ETAG;
import static com.google.common.net.HttpHeaders.IF_NONE_MATCH;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;

import com.google.common.base.CharMatcher;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtjsonrpc.server.RPCServletUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Base class for serving static resources.
 * <p>
 * Supports caching, ETags, basic content type detection, and limited gzip
 * compression.
 */
public abstract class ResourceServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final Logger log =
      LoggerFactory.getLogger(ResourceServlet.class);

  private static final String JS = "application/x-javascript";
  private static final ImmutableMap<String, String> MIME_TYPES =
      ImmutableMap.<String, String> builder()
        .put("css", "text/css")
        .put("gif", "image/gif")
        .put("htm", "text/html")
        .put("html", "text/html")
        .put("jpeg", "image/jpeg")
        .put("jpg", "image/jpeg")
        .put("js", JS)
        .put("pdf", "application/pdf")
        .put("png", "image/png")
        .put("rtf", "text/rtf")
        .put("svg", "image/svg+xml")
        .put("text", "text/plain")
        .put("tif", "image/tiff")
        .put("tiff", "image/tiff")
        .put("txt", "text/plain")
        .build();

  protected static String contentType(final String name) {
    int dot = name.lastIndexOf('.');
    String ext = 0 < dot ? name.substring(dot + 1) : "";
    String type = MIME_TYPES.get(ext);
    return type != null ? type : "application/octet-stream";
  }

  private final Cache<Path, Resource> cache;
  private boolean refresh;

  protected ResourceServlet(Cache<Path, Resource> cache, boolean refresh) {
    this.cache = cache;
    this.refresh = refresh;
  }

  /**
   * Get the resource path on the filesystem that should be served for this
   * request.
   *
   * @param pathInfo result of {@link HttpServletRequest#getPathInfo()}.
   * @return path where static content can be found.
   */
  protected abstract Path getResourcePath(String pathInfo);

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    Resource r;
    try {
      r = getResource(req);
    } catch (ExecutionException e) {
      log.warn(String.format(
          "Cannot load static resource %s",
          req.getPathInfo()), e);
      CacheHeaders.setNotCacheable(rsp);
      rsp.setStatus(SC_INTERNAL_SERVER_ERROR);
      return;
    }

    String e = req.getParameter("e");
    if (r == Resource.NOT_FOUND || (e != null && !r.etag.equals(e))) {
      CacheHeaders.setNotCacheable(rsp);
      rsp.setStatus(SC_NOT_FOUND);
      return;
    } else if (r.etag.equals(req.getHeader(IF_NONE_MATCH))) {
      rsp.setStatus(SC_NOT_MODIFIED);
      return;
    }

    byte[] tosend = r.raw;
    if (!r.contentType.equals(JS) && RPCServletUtils.acceptsGzipEncoding(req)) {
      byte[] gz = HtmlDomUtil.compress(tosend);
      if ((gz.length + 24) < tosend.length) {
        rsp.setHeader(CONTENT_ENCODING, "gzip");
        tosend = gz;
      }
    }
    if (!CacheHeaders.hasCacheHeader(rsp)) {
      if (e != null && r.etag.equals(e)) {
        CacheHeaders.setCacheable(req, rsp, 360, DAYS, false);
      } else {
        CacheHeaders.setCacheable(req, rsp, 15, MINUTES, refresh);
      }
    }
    rsp.setHeader(ETAG, r.etag);
    rsp.setContentType(r.contentType);
    rsp.setContentLength(tosend.length);
    try (OutputStream out = rsp.getOutputStream()) {
      out.write(tosend);
    }
  }

  @Nullable
  Resource getResource(String name) {
    try {
      Path p = getResourcePath(name);
      return cache.get(p, newLoader(name, p));
    } catch (ExecutionException e) {
      log.warn(String.format("Cannot load static resource %s", name), e);
      return null;
    }
  }

  private Resource getResource(HttpServletRequest req)
      throws ExecutionException {
    String name = CharMatcher.is('/').trimFrom(req.getPathInfo());
    if (isUnreasonableName(name)) {
      return Resource.NOT_FOUND;
    }
    Path p = getResourcePath(name);
    if (p == null) {
      return Resource.NOT_FOUND;
    }

    Callable<Resource> loader = newLoader(name, p);
    Resource r = cache.get(p, loader);
    if (r == Resource.NOT_FOUND) {
      return Resource.NOT_FOUND;
    }

    if (refresh && r.isStale(p)) {
      cache.invalidate(p);
      r = cache.get(p, loader);
    }
    return r;
  }

  private static boolean isUnreasonableName(String name) {
    return name.length() < 1
      || name.contains("\\") // no windows/dos style paths
      || name.startsWith("../") // no "../etc/passwd"
      || name.contains("/../") // no "foo/../etc/passwd"
      || name.contains("/./") // "foo/./foo" is insane to ask
      || name.contains("//"); // windows UNC path can be "//..."
  }

  private Callable<Resource> newLoader(final String name, final Path p) {
    return new Callable<Resource>() {
      @Override
      public Resource call() throws IOException {
        return new Resource(
            Files.getLastModifiedTime(p),
            contentType(name),
            Files.readAllBytes(p));
      }
    };
  }

  static class Resource {
    static final Resource NOT_FOUND =
        new Resource(FileTime.fromMillis(0), "", new byte[] {});

    final FileTime lastModified;
    final String contentType;
    final String etag;
    final byte[] raw;

    Resource(FileTime lastModified, String contentType, byte[] raw) {
      this.lastModified = lastModified;
      this.contentType = contentType;
      this.etag = Hashing.md5().hashBytes(raw).toString();
      this.raw = raw;
    }

    boolean isStale(Path p) {
      try {
        return !lastModified.equals(Files.getLastModifiedTime(p));
      } catch (IOException e) {
        return true;
      }
    }
  }

  static class Weigher
      implements com.google.common.cache.Weigher<Path, Resource> {
    @Override
    public int weigh(Path p, Resource r) {
      return 2 * p.toString().length() + r.raw.length;
    }
  }
}
