// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.gerrit.json.OutputFormat.JSON_COMPACT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.RawInput;
import java.io.IOException;
import org.apache.http.Header;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;

/** Sends requests to {@link GerritServer} as a specified user. */
public class GerritServerRestSession extends HttpSession implements RestSession {

  public GerritServerRestSession(GerritServer server, @Nullable TestAccount account) {
    super(server, account);
  }

  @Override
  public RestResponse get(String endPoint) throws IOException {
    return getWithHeaders(endPoint);
  }

  @Override
  public RestResponse getJsonAccept(String endPoint) throws IOException {
    return getWithHeaders(endPoint, new BasicHeader(ACCEPT, "application/json"));
  }

  @Override
  public RestResponse getWithHeaders(String endPoint, Header... headers) throws IOException {
    Request get = Request.Get(getUrl(endPoint));
    if (headers != null) {
      get.setHeaders(headers);
    }
    return execute(get);
  }

  @Override
  public RestResponse head(String endPoint) throws IOException {
    return execute(Request.Head(getUrl(endPoint)));
  }

  @Override
  public RestResponse put(String endPoint) throws IOException {
    return put(endPoint, /* content = */ null);
  }

  @Override
  public RestResponse put(String endPoint, Object content) throws IOException {
    return putWithHeaders(endPoint, content);
  }

  @Override
  public RestResponse putWithHeaders(String endPoint, Header... headers) throws IOException {
    return putWithHeaders(endPoint, /* content= */ null, headers);
  }

  @Override
  public RestResponse putWithHeaders(String endPoint, Object content, Header... headers)
      throws IOException {
    Request put = Request.Put(getUrl(endPoint));
    if (headers != null) {
      put.setHeaders(headers);
    }
    if (content != null) {
      addContentToRequest(put, content);
    }
    return execute(put);
  }

  @Override
  public RestResponse putRaw(String endPoint, RawInput stream) throws IOException {
    requireNonNull(stream);
    Request put = Request.Put(getUrl(endPoint));
    put.addHeader(new BasicHeader(CONTENT_TYPE, stream.getContentType()));
    put.body(
        new BufferedHttpEntity(
            new InputStreamEntity(stream.getInputStream(), stream.getContentLength())));
    return execute(put);
  }

  @Override
  public RestResponse post(String endPoint) throws IOException {
    return post(endPoint, /* content = */ null);
  }

  @Override
  public RestResponse post(String endPoint, Object content) throws IOException {
    return postWithHeaders(endPoint, content);
  }

  @Override
  public RestResponse postWithHeaders(String endPoint, Object content, Header... headers)
      throws IOException {
    Request post = Request.Post(getUrl(endPoint));
    if (headers != null) {
      post.setHeaders(headers);
    }
    if (content != null) {
      addContentToRequest(post, content);
    }
    return execute(post);
  }

  private static void addContentToRequest(Request request, Object content) {
    request.addHeader(new BasicHeader(CONTENT_TYPE, "application/json"));
    request.body(new StringEntity(JSON_COMPACT.newGson().toJson(content), UTF_8));
  }

  @Override
  public RestResponse delete(String endPoint) throws IOException {
    return execute(Request.Delete(getUrl(endPoint)));
  }

  @Override
  public RestResponse deleteWithHeaders(String endPoint, Header... headers) throws IOException {
    Request delete = Request.Delete(getUrl(endPoint));
    if (headers != null) {
      delete.setHeaders(headers);
    }
    return execute(delete);
  }

  @Override
  public String getUrl(String endPoint) {
    return url + (account != null ? "/a" : "") + endPoint;
  }
}
