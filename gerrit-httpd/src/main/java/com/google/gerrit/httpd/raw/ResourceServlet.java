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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.net.HttpHeaders.CONTENT_ENCODING;
import static com.google.common.net.HttpHeaders.ETAG;
import static com.google.common.net.HttpHeaders.IF_MODIFIED_SINCE;
import static com.google.common.net.HttpHeaders.IF_NONE_MATCH;
import static com.google.common.net.HttpHeaders.LAST_MODIFIED;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.FileUtil;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for serving static resources.
 *
 * <p>Supports caching, ETags, basic content type detection, and limited gzip compression.
 */
public abstract class ResourceServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final Logger log = LoggerFactory.getLogger(ResourceServlet.class);

  private static final int CACHE_FILE_SIZE_LIMIT_BYTES = 100 << 10;

  private static final String JS = "application/x-javascript";
  private static final ImmutableMap<String, String> MIME_TYPES =
      ImmutableMap.<String, String>builder()
          .put("css", "text/css")
          .put("gif", "image/gif")
          .put("htm", "text/html")
          .put("html", "text/html")
          .put("ico", "image/x-icon")
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

  protected static String contentType(String name) {
    int dot = name.lastIndexOf('.');
    String ext = 0 < dot ? name.substring(dot + 1) : "";
    String type = MIME_TYPES.get(ext);
    return type != null ? type : "application/octet-stream";
  }

  private final Cache<Path, Resource> cache;
  private final boolean refresh;
  private final boolean cacheOnClient;
  private final int cacheFileSizeLimitBytes;

  protected ResourceServlet(Cache<Path, Resource> cache, boolean refresh) {
    this(cache, refresh, true, CACHE_FILE_SIZE_LIMIT_BYTES);
  }

  protected ResourceServlet(Cache<Path, Resource> cache, boolean refresh, boolean cacheOnClient) {
    this(cache, refresh, cacheOnClient, CACHE_FILE_SIZE_LIMIT_BYTES);
  }

  @VisibleForTesting
  ResourceServlet(
      Cache<Path, Resource> cache,
      boolean refresh,
      boolean cacheOnClient,
      int cacheFileSizeLimitBytes) {
    this.cache = checkNotNull(cache, "cache");
    this.refresh = refresh;
    this.cacheOnClient = cacheOnClient;
    this.cacheFileSizeLimitBytes = cacheFileSizeLimitBytes;
  }

  /**
   * Get the resource path on the filesystem that should be served for this request.
   *
   * @param pathInfo result of {@link HttpServletRequest#getPathInfo()}.
   * @return path where static content can be found.
   * @throws IOException if an error occurred resolving the resource.
   */
  protected abstract Path getResourcePath(String pathInfo) throws IOException;

  protected FileTime getLastModifiedTime(Path p) throws IOException {
    return Files.getLastModifiedTime(p);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    String name;
    if (req.getPathInfo() == null) {
      name = "/";
    } else {
      name = CharMatcher.is('/').trimFrom(req.getPathInfo());
    }
    if (isUnreasonableName(name)) {
      notFound(rsp);
      return;
    }
    Path p = getResourcePath(name);
    if (p == null) {
      notFound(rsp);
      return;
    }

    Resource r = cache.getIfPresent(p);
    try {
      if (r == null) {
        if (maybeStream(p, req, rsp)) {
          return; // Bypass cache for large resource.
        }
        r = cache.get(p, newLoader(p));
      }
      if (refresh && r.isStale(p, this)) {
        cache.invalidate(p);
        r = cache.get(p, newLoader(p));
      }
    } catch (ExecutionException e) {
      log.warn("Cannot load static resource " + req.getPathInfo(), e);
      CacheHeaders.setNotCacheable(rsp);
      rsp.setStatus(SC_INTERNAL_SERVER_ERROR);
      return;
    }
    if (r == Resource.NOT_FOUND) {
      notFound(rsp); // Cached not found response.
      return;
    }

    String e = req.getParameter("e");
    if (e != null && !r.etag.equals(e)) {
      CacheHeaders.setNotCacheable(rsp);
      rsp.setStatus(SC_NOT_FOUND);
      return;
    } else if (cacheOnClient && r.etag.equals(req.getHeader(IF_NONE_MATCH))) {
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

    if (cacheOnClient) {
      rsp.setHeader(ETAG, r.etag);
    } else {
      CacheHeaders.setNotCacheable(rsp);
    }
    if (!CacheHeaders.hasCacheHeader(rsp)) {
      if (e != null && r.etag.equals(e)) {
        CacheHeaders.setCacheable(req, rsp, 360, DAYS, false);
      } else {
        CacheHeaders.setCacheable(req, rsp, 15, MINUTES, refresh);
      }
    }
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
      if (p == null) {
        log.warn(String.format("Path doesn't exist %s", name));
        return null;
      }
      return cache.get(p, newLoader(p));
    } catch (ExecutionException | IOException e) {
      log.warn(String.format("Cannot load static resource %s", name), e);
      return null;
    }
  }

  private static void notFound(HttpServletResponse rsp) {
    rsp.setStatus(SC_NOT_FOUND);
    CacheHeaders.setNotCacheable(rsp);
  }

  /**
   * Maybe stream a path to the response, depending on the properties of the file and cache headers
   * in the request.
   *
   * @param p path to stream
   * @param req HTTP request.
   * @param rsp HTTP response.
   * @return true if the response was written (either the file contents or an error); false if the
   *     path is too small to stream and should be cached.
   */
  private boolean maybeStream(Path p, HttpServletRequest req, HttpServletResponse rsp)
      throws IOException {
    try {
      if (Files.size(p) < cacheFileSizeLimitBytes) {
        return false;
      }
    } catch (NoSuchFileException e) {
      cache.put(p, Resource.NOT_FOUND);
      notFound(rsp);
      return true;
    }

    long lastModified = FileUtil.lastModified(p);
    if (req.getDateHeader(IF_MODIFIED_SINCE) >= lastModified) {
      rsp.setStatus(SC_NOT_MODIFIED);
      return true;
    }

    if (lastModified > 0) {
      rsp.setDateHeader(LAST_MODIFIED, lastModified);
    }
    if (!CacheHeaders.hasCacheHeader(rsp)) {
      CacheHeaders.setCacheable(req, rsp, 15, MINUTES, refresh);
    }
    rsp.setContentType(contentType(p.toString()));

    OutputStream out = rsp.getOutputStream();
    GZIPOutputStream gz = null;
    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader(CONTENT_ENCODING, "gzip");
      gz = new GZIPOutputStream(out);
      out = gz;
    }
    Files.copy(p, out);
    if (gz != null) {
      gz.finish();
    }
    return true;
  }

  private static boolean isUnreasonableName(String name) {
    return name.length() < 1
        || name.contains("\\") // no windows/dos style paths
        || name.startsWith("../") // no "../etc/passwd"
        || name.contains("/../") // no "foo/../etc/passwd"
        || name.contains("/./") // "foo/./foo" is insane to ask
        || name.contains("//"); // windows UNC path can be "//..."
  }

  private Callable<Resource> newLoader(final Path p) {
    return () -> {
      try {
        return new Resource(
            getLastModifiedTime(p), contentType(p.toString()), Files.readAllBytes(p));
      } catch (NoSuchFileException e) {
        return Resource.NOT_FOUND;
      }
    };
  }

  public static class Resource {
    static final Resource NOT_FOUND = new Resource(FileTime.fromMillis(0), "", new byte[] {});

    final FileTime lastModified;
    final String contentType;
    final String etag;
    final byte[] raw;

    Resource(FileTime lastModified, String contentType, byte[] raw) {
      this.lastModified = checkNotNull(lastModified, "lastModified");
      this.contentType = checkNotNull(contentType, "contentType");
      this.raw = checkNotNull(raw, "raw");
      this.etag = Hashing.md5().hashBytes(raw).toString();
    }

    boolean isStale(Path p, ResourceServlet rs) throws IOException {
      FileTime t;
      try {
        t = rs.getLastModifiedTime(p);
      } catch (NoSuchFileException e) {
        return this != NOT_FOUND;
      }
      return t.toMillis() == 0 || lastModified.toMillis() == 0 || !lastModified.equals(t);
    }
  }

  public static class Weigher implements com.google.common.cache.Weigher<Path, Resource> {
    @Override
    public int weigh(Path p, Resource r) {
      return 2 * p.toString().length() + r.raw.length;
    }
  }
}
