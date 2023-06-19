// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.httpd.restapi;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.httpd.restapi.RestApiServlet.isCacheableWithETag;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Cacheability;
import com.google.gerrit.extensions.restapi.ETagView;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestResource.HasETag;
import com.google.gerrit.extensions.restapi.RestResource.HasLastModified;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RestApiServletTest {
  private static final String[] httpMethods = {"GET", "HEAD", "POST", "PUT", "DELETE"};

  @Test
  public void anyHttpRequestIsNotCacheableWithETagByDefault() {
    for (String method : httpMethods) {
      assertThat(
              isCacheableWithETag(
                  new TestHttpRequest(method), new TestRestResource(), new TestRestView()))
          .isFalse();
    }
  }

  @Test
  public void getHeadHttpRequestIsNotCacheableWithETagView() {
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("GET"),
                new TestETagCacheableRestResource(),
                new TestETagView()))
        .isFalse();
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("HEAD"),
                new TestETagCacheableRestResource(),
                new TestETagView()))
        .isFalse();
  }

  @Test
  public void getHeadHttpRequestIsCacheableWithCacheableETagView() {
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("GET"),
                new TestETagCacheableRestResource(),
                new TestCacheableETagView()))
        .isTrue();
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("HEAD"),
                new TestETagCacheableRestResource(),
                new TestCacheableETagView()))
        .isTrue();
  }

  @Test
  public void getHeadHttpRequestIsNotCacheableWithETagResource() {
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("GET"),
                new TestETagRestResource(),
                new TestCacheableETagView()))
        .isFalse();
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("HEAD"),
                new TestETagRestResource(),
                new TestCacheableETagView()))
        .isFalse();
  }

  @Test
  public void getHeadHttpRequestIsCacheableWithCacheableETagResource() {
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("GET"),
                new TestETagCacheableRestResource(),
                new TestCacheableETagView()))
        .isTrue();
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("HEAD"),
                new TestETagCacheableRestResource(),
                new TestCacheableETagView()))
        .isTrue();
  }

  @Test
  public void postPutDeleteHttpRequestIsNotCacheableWithETagView() {
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("POST"), new TestRestResource(), new TestETagView()))
        .isFalse();
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("PUT"), new TestRestResource(), new TestETagView()))
        .isFalse();
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("DELETE"), new TestRestResource(), new TestETagView()))
        .isFalse();
  }

  @Test
  public void getHeadHttpIsNotCacheableWithNoCacheHeaders() {
    List<String[]> noCacheHeadrs =
        Arrays.asList(
            new String[] {"Pragma", "no-cache"},
            new String[] {"Cache-Control", "no-cache"},
            new String[] {"Cache-Control", "max-age=0"});

    for (String[] hdr : noCacheHeadrs) {

      assertThat(
              isCacheableWithETag(
                  new TestHttpRequest("GET").addHeader(hdr[0], hdr[1]),
                  new TestRestResource(),
                  new TestETagView()))
          .isFalse();
      assertThat(
              isCacheableWithETag(
                  new TestHttpRequest("HEAD").addHeader(hdr[0], hdr[1]),
                  new TestRestResource(),
                  new TestETagView()))
          .isFalse();
    }
  }

  @Test
  public void getHeadHttpIsNotCacheableWithNonCacheableResource() {

    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("GET"), new TestCacheableResource(false), new TestETagView()))
        .isFalse();
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("HEAD"), new TestCacheableResource(false), new TestETagView()))
        .isFalse();
  }

  @Test
  public void getHeadHttpIsNotCacheableWithLastModifiedResource() {

    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("GET"), new TestLastModifiedResource(), new TestETagView()))
        .isFalse();
    assertThat(
            isCacheableWithETag(
                new TestHttpRequest("HEAD"), new TestLastModifiedResource(), new TestETagView()))
        .isFalse();
  }

  static class TestHttpRequest extends FakeHttpServletRequest {
    private String method;

    public TestHttpRequest(String method) {
      this.method = method;
    }

    @Override
    public String getMethod() {
      return method;
    }
  }

  static class TestRestResource implements RestResource {}

  static class TestLastModifiedResource implements RestResource, HasLastModified {

    @Override
    public Timestamp getLastModified() {
      return new Timestamp(System.currentTimeMillis());
    }
  }

  static class TestETagRestResource implements RestResource, HasETag {

    @Override
    public String getETag() {
      return "etagvalue";
    }
  }

  static class TestETagCacheableRestResource extends TestETagRestResource implements Cacheability {

    @Override
    public boolean isCacheable() {
      return true;
    }
  }

  static class TestCacheableResource implements RestResource, Cacheability {
    private final boolean cacheable;

    public TestCacheableResource(boolean cacheable) {
      this.cacheable = cacheable;
    }

    @Override
    public boolean isCacheable() {
      return cacheable;
    }
  }

  static class TestRestView implements RestView<RestResource> {}

  static class TestETagView implements ETagView<RestResource> {

    @Override
    public Object apply(RestResource resource)
        throws AuthException, BadRequestException, ResourceConflictException, Exception {
      return null;
    }

    @Override
    public String getETag(RestResource rsrc) {
      return "etagvalue";
    }
  }

  static class TestCacheableETagView extends TestETagView implements Cacheability {

    @Override
    public boolean isCacheable() {
      return true;
    }
  }
}
