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
import static com.google.gerrit.httpd.restapi.RestApiServlet.isCacheableWithEtag;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Cacheable;
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
              isCacheableWithEtag(
                  new TestHttpRequest(method), new TestRestResource(), new TestRestView()))
          .isFalse();
    }
  }

  @Test
  public void getHeadHttpRequestIsCacheableWithETagView() {
    assertThat(
            isCacheableWithEtag(
                new TestHttpRequest("GET"), new TestRestResource(), new TestETagView()))
        .isTrue();
    assertThat(
            isCacheableWithEtag(
                new TestHttpRequest("HEAD"), new TestRestResource(), new TestETagView()))
        .isTrue();
  }

  @Test
  public void getHeadHttpRequestIsCacheableWithETagResource() {
    assertThat(
            isCacheableWithEtag(
                new TestHttpRequest("GET"), new TestETagRestResource(), new TestRestView()))
        .isTrue();
    assertThat(
            isCacheableWithEtag(
                new TestHttpRequest("HEAD"), new TestETagRestResource(), new TestRestView()))
        .isTrue();
  }

  @Test
  public void postPutDeleteHttpRequestIsNotCacheableWithETagView() {
    assertThat(
            isCacheableWithEtag(
                new TestHttpRequest("POST"), new TestRestResource(), new TestETagView()))
        .isFalse();
    assertThat(
            isCacheableWithEtag(
                new TestHttpRequest("PUT"), new TestRestResource(), new TestETagView()))
        .isFalse();
    assertThat(
            isCacheableWithEtag(
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
              isCacheableWithEtag(
                  new TestHttpRequest("GET").addHeader(hdr[0], hdr[1]),
                  new TestRestResource(),
                  new TestETagView()))
          .isFalse();
      assertThat(
              isCacheableWithEtag(
                  new TestHttpRequest("HEAD").addHeader(hdr[0], hdr[1]),
                  new TestRestResource(),
                  new TestETagView()))
          .isFalse();
    }
  }

  @Test
  public void getHeadHttpIsNotCacheableWithNonCacheableResource() {

    assertThat(
            isCacheableWithEtag(
                new TestHttpRequest("GET"), new TestCacheableResource(false), new TestETagView()))
        .isFalse();
    assertThat(
            isCacheableWithEtag(
                new TestHttpRequest("HEAD"), new TestCacheableResource(false), new TestETagView()))
        .isFalse();
  }

  @Test
  public void getHeadHttpIsNotCacheableWithLastModifiedResource() {

    assertThat(
            isCacheableWithEtag(
                new TestHttpRequest("GET"), new TestLastModifiedResource(), new TestETagView()))
        .isFalse();
    assertThat(
            isCacheableWithEtag(
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

  static class TestCacheableResource implements RestResource, Cacheable {
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
}
