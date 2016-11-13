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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.gerrit.httpd.raw.ResourceServlet.Resource;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;

public class ResourceServletTest {
  private static Cache<Path, Resource> newCache(int size) {
    return CacheBuilder.newBuilder().maximumSize(size).recordStats().build();
  }

  private static class Servlet extends ResourceServlet {
    private static final long serialVersionUID = 1L;

    private final FileSystem fs;

    private Servlet(FileSystem fs, Cache<Path, Resource> cache, boolean refresh) {
      super(cache, refresh);
      this.fs = fs;
    }

    private Servlet(
        FileSystem fs, Cache<Path, Resource> cache, boolean refresh, boolean cacheOnClient) {
      super(cache, refresh, cacheOnClient);
      this.fs = fs;
    }

    private Servlet(
        FileSystem fs, Cache<Path, Resource> cache, boolean refresh, int cacheFileSizeLimitBytes) {
      super(cache, refresh, true, cacheFileSizeLimitBytes);
      this.fs = fs;
    }

    private Servlet(
        FileSystem fs,
        Cache<Path, Resource> cache,
        boolean refresh,
        boolean cacheOnClient,
        int cacheFileSizeLimitBytes) {
      super(cache, refresh, cacheOnClient, cacheFileSizeLimitBytes);
      this.fs = fs;
    }

    @Override
    protected Path getResourcePath(String pathInfo) {
      return fs.getPath("/" + CharMatcher.is('/').trimLeadingFrom(pathInfo));
    }
  }

  private FileSystem fs;
  private AtomicLong ts;

  @Before
  public void setUp() {
    fs = Jimfs.newFileSystem(Configuration.unix());
    ts = new AtomicLong(ISODateTimeFormat.dateTime().parseMillis("2010-01-30T12:00:00.000-08:00"));
  }

  @Test
  public void notFoundWithoutRefresh() throws Exception {
    Cache<Path, Resource> cache = newCache(1);
    Servlet servlet = new Servlet(fs, cache, false);

    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.doGet(request("/notfound"), res);
    assertThat(res.getStatus()).isEqualTo(SC_NOT_FOUND);
    assertNotCacheable(res);
    assertCacheHits(cache, 0, 1);

    res = new FakeHttpServletResponse();
    servlet.doGet(request("/notfound"), res);
    assertThat(res.getStatus()).isEqualTo(SC_NOT_FOUND);
    assertNotCacheable(res);
    assertCacheHits(cache, 1, 1);
  }

  @Test
  public void notFoundWithRefresh() throws Exception {
    Cache<Path, Resource> cache = newCache(1);
    Servlet servlet = new Servlet(fs, cache, true);

    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.doGet(request("/notfound"), res);
    assertThat(res.getStatus()).isEqualTo(SC_NOT_FOUND);
    assertNotCacheable(res);
    assertCacheHits(cache, 0, 1);

    res = new FakeHttpServletResponse();
    servlet.doGet(request("/notfound"), res);
    assertThat(res.getStatus()).isEqualTo(SC_NOT_FOUND);
    assertNotCacheable(res);
    assertCacheHits(cache, 1, 1);
  }

  @Test
  public void smallFileWithRefresh() throws Exception {
    Cache<Path, Resource> cache = newCache(1);
    Servlet servlet = new Servlet(fs, cache, true);

    writeFile("/foo", "foo1");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.doGet(request("/foo"), res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getActualBodyString()).isEqualTo("foo1");
    assertCacheable(res, true);
    assertHasETag(res);
    // Miss on getIfPresent, miss on get.
    assertCacheHits(cache, 0, 2);

    res = new FakeHttpServletResponse();
    servlet.doGet(request("/foo"), res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getActualBodyString()).isEqualTo("foo1");
    assertCacheable(res, true);
    assertHasETag(res);
    assertCacheHits(cache, 1, 2);

    writeFile("/foo", "foo2");
    res = new FakeHttpServletResponse();
    servlet.doGet(request("/foo"), res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getActualBodyString()).isEqualTo("foo2");
    assertCacheable(res, true);
    assertHasETag(res);
    // Hit, invalidate, miss.
    assertCacheHits(cache, 2, 3);
  }

  @Test
  public void smallFileWithoutClientCache() throws Exception {
    Cache<Path, Resource> cache = newCache(1);
    Servlet servlet = new Servlet(fs, cache, false, false);

    writeFile("/foo", "foo1");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.doGet(request("/foo"), res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getActualBodyString()).isEqualTo("foo1");
    assertNotCacheable(res);

    // Miss on getIfPresent, miss on get.
    assertCacheHits(cache, 0, 2);

    res = new FakeHttpServletResponse();
    servlet.doGet(request("/foo"), res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getActualBodyString()).isEqualTo("foo1");
    assertNotCacheable(res);
    assertCacheHits(cache, 1, 2);

    writeFile("/foo", "foo2");
    res = new FakeHttpServletResponse();
    servlet.doGet(request("/foo"), res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getActualBodyString()).isEqualTo("foo1");
    assertNotCacheable(res);
    assertCacheHits(cache, 2, 2);
  }

  @Test
  public void smallFileWithoutRefresh() throws Exception {
    Cache<Path, Resource> cache = newCache(1);
    Servlet servlet = new Servlet(fs, cache, false);

    writeFile("/foo", "foo1");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.doGet(request("/foo"), res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getActualBodyString()).isEqualTo("foo1");
    assertCacheable(res, false);
    assertHasETag(res);
    // Miss on getIfPresent, miss on get.
    assertCacheHits(cache, 0, 2);

    res = new FakeHttpServletResponse();
    servlet.doGet(request("/foo"), res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getActualBodyString()).isEqualTo("foo1");
    assertCacheable(res, false);
    assertHasETag(res);
    assertCacheHits(cache, 1, 2);

    writeFile("/foo", "foo2");
    res = new FakeHttpServletResponse();
    servlet.doGet(request("/foo"), res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getActualBodyString()).isEqualTo("foo1");
    assertCacheable(res, false);
    assertHasETag(res);
    assertCacheHits(cache, 2, 2);
  }

  @Test
  public void verySmallFileDoesntBotherWithGzip() throws Exception {
    Cache<Path, Resource> cache = newCache(1);
    Servlet servlet = new Servlet(fs, cache, true);
    writeFile("/foo", "foo1");

    FakeHttpServletRequest req = request("/foo").addHeader("Accept-Encoding", "gzip");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.doGet(req, res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getHeader("Content-Encoding")).isNull();
    assertThat(res.getActualBodyString()).isEqualTo("foo1");
    assertHasETag(res);
    assertCacheable(res, true);
  }

  @Test
  public void smallFileWithGzip() throws Exception {
    Cache<Path, Resource> cache = newCache(1);
    Servlet servlet = new Servlet(fs, cache, true);
    String content = Strings.repeat("a", 100);
    writeFile("/foo", content);

    FakeHttpServletRequest req = request("/foo").addHeader("Accept-Encoding", "gzip");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.doGet(req, res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getHeader("Content-Encoding")).isEqualTo("gzip");
    assertThat(gunzip(res.getActualBody())).isEqualTo(content);
    assertHasETag(res);
    assertCacheable(res, true);
  }

  @Test
  public void largeFileBypassesCacheRegardlessOfRefreshParamter() throws Exception {
    for (boolean refresh : Lists.newArrayList(true, false)) {
      Cache<Path, Resource> cache = newCache(1);
      Servlet servlet = new Servlet(fs, cache, refresh, 3);

      writeFile("/foo", "foo1");
      FakeHttpServletResponse res = new FakeHttpServletResponse();
      servlet.doGet(request("/foo"), res);
      assertThat(res.getStatus()).isEqualTo(SC_OK);
      assertThat(res.getActualBodyString()).isEqualTo("foo1");
      assertThat(res.getHeader("Last-Modified")).isNotNull();
      assertCacheable(res, refresh);
      assertHasLastModified(res);
      assertCacheHits(cache, 0, 1);

      writeFile("/foo", "foo1");
      res = new FakeHttpServletResponse();
      servlet.doGet(request("/foo"), res);
      assertThat(res.getStatus()).isEqualTo(SC_OK);
      assertThat(res.getActualBodyString()).isEqualTo("foo1");
      assertThat(res.getHeader("Last-Modified")).isNotNull();
      assertCacheable(res, refresh);
      assertHasLastModified(res);
      assertCacheHits(cache, 0, 2);

      writeFile("/foo", "foo2");
      res = new FakeHttpServletResponse();
      servlet.doGet(request("/foo"), res);
      assertThat(res.getStatus()).isEqualTo(SC_OK);
      assertThat(res.getActualBodyString()).isEqualTo("foo2");
      assertThat(res.getHeader("Last-Modified")).isNotNull();
      assertCacheable(res, refresh);
      assertHasLastModified(res);
      assertCacheHits(cache, 0, 3);
    }
  }

  @Test
  public void largeFileWithGzip() throws Exception {
    Cache<Path, Resource> cache = newCache(1);
    Servlet servlet = new Servlet(fs, cache, true, 3);
    String content = Strings.repeat("a", 100);
    writeFile("/foo", content);

    FakeHttpServletRequest req = request("/foo").addHeader("Accept-Encoding", "gzip");
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    servlet.doGet(req, res);
    assertThat(res.getStatus()).isEqualTo(SC_OK);
    assertThat(res.getHeader("Content-Encoding")).isEqualTo("gzip");
    assertThat(gunzip(res.getActualBody())).isEqualTo(content);
    assertHasLastModified(res);
    assertCacheable(res, true);
  }

  // TODO(dborowitz): Check MIME type.
  // TODO(dborowitz): Test that JS is not gzipped.
  // TODO(dborowitz): Test ?e parameter.
  // TODO(dborowitz): Test If-None-Match behavior.
  // TODO(dborowitz): Test If-Modified-Since behavior.

  private void writeFile(String path, String content) throws Exception {
    Files.write(fs.getPath(path), content.getBytes(UTF_8));
    Files.setLastModifiedTime(fs.getPath(path), FileTime.fromMillis(ts.getAndIncrement()));
  }

  private static void assertCacheHits(Cache<?, ?> cache, int hits, int misses) {
    assertThat(cache.stats().hitCount()).named("hits").isEqualTo(hits);
    assertThat(cache.stats().missCount()).named("misses").isEqualTo(misses);
  }

  private static void assertCacheable(FakeHttpServletResponse res, boolean revalidate) {
    String header = res.getHeader("Cache-Control").toLowerCase();
    assertThat(header).contains("public");
    if (revalidate) {
      assertThat(header).contains("must-revalidate");
    } else {
      assertThat(header).doesNotContain("must-revalidate");
    }
  }

  private static void assertHasLastModified(FakeHttpServletResponse res) {
    assertThat(res.getHeader("Last-Modified")).isNotNull();
    assertThat(res.getHeader("ETag")).isNull();
  }

  private static void assertHasETag(FakeHttpServletResponse res) {
    assertThat(res.getHeader("ETag")).isNotNull();
    assertThat(res.getHeader("Last-Modified")).isNull();
  }

  private static void assertNotCacheable(FakeHttpServletResponse res) {
    assertThat(res.getHeader("Cache-Control")).contains("no-cache");
    assertThat(res.getHeader("ETag")).isNull();
    assertThat(res.getHeader("Last-Modified")).isNull();
  }

  private static FakeHttpServletRequest request(String path) {
    return new FakeHttpServletRequest().setPathInfo(path);
  }

  private static String gunzip(byte[] data) throws Exception {
    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
      return new String(ByteStreams.toByteArray(in), UTF_8);
    }
  }
}
