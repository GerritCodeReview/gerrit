// Copyright (C) 2013 The Android Open Source Project
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

public class RestSession extends HttpSession {

  public RestSession(GerritServer server, @Nullable TestAccount account) {
    super(server, account);
  }

  public RestResponse get(String endPoint) throws IOException {
    return getWithHeaders(endPoint);
  }

  public RestResponse getJsonAccept(String endPoint) throws IOException {
    return getWithHeaders(endPoint, new BasicHeader(ACCEPT, "application/json"));
  }

  public RestResponse getWithHeaders(String endPoint, Header... headers) throws IOException {
    Request get = Request.Get(getUrl(endPoint));
    if (headers != null) {
      get.setHeaders(headers);
    }
    return execute(get);
  }

  public RestResponse head(String endPoint) throws IOException {
    return execute(Request.Head(getUrl(endPoint)));
  }

  public RestResponse put(String endPoint) throws IOException {
    return put(endPoint, /* content = */ null);
  }

  public RestResponse put(String endPoint, Object content) throws IOException {
    return putWithHeaders(endPoint, content);
  }

  public RestResponse putWithHeaders(String endPoint, Header... headers) throws IOException {
    return putWithHeaders(endPoint, /* content= */ null, headers);
  }

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

  public RestResponse putRaw(String endPoint, RawInput stream) throws IOException {
    requireNonNull(stream);
    Request put = Request.Put(getUrl(endPoint));
    put.addHeader(new BasicHeader(CONTENT_TYPE, stream.getContentType()));
    put.body(
        new BufferedHttpEntity(
            new InputStreamEntity(stream.getInputStream(), stream.getContentLength())));
    return execute(put);
  }

  public RestResponse post(String endPoint) throws IOException {
    return post(endPoint, /* content = */ null);
  }

  public RestResponse post(String endPoint, Object content) throws IOException {
    return postWithHeaders(endPoint, content);
  }

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

  public RestResponse delete(String endPoint) throws IOException {
    return execute(Request.Delete(getUrl(endPoint)));
  }

  public RestResponse deleteWithHeaders(String endPoint, Header... headers) throws IOException {
    Request delete = Request.Delete(getUrl(endPoint));
    if (headers != null) {
      delete.setHeaders(headers);
    }
    return execute(delete);
  }

  public String getUrl(String endPoint) {
    return url + (account != null ? "/a" : "") + endPoint;
  }
}
