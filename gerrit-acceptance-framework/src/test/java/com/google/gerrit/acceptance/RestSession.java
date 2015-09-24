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

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.server.OutputFormat;

import org.apache.http.Header;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class RestSession extends HttpSession {

  public RestSession(GerritServer server, TestAccount account) {
    super(server, account);
  }

  public RestResponse get(String endPoint) throws IOException {
    return getWithHeader(endPoint, null);
  }

  public RestResponse getJsonAccept(String endPoint) throws IOException {
    return getWithHeader(endPoint,
        new BasicHeader(HttpHeaders.ACCEPT, "application/json"));
  }

  private RestResponse getWithHeader(String endPoint, Header header)
      throws IOException {
    Request get = Request.Get(url + "/a" + endPoint);
    if (header != null) {
      get.addHeader(header);
    }
    return execute(get);
  }

  public RestResponse put(String endPoint) throws IOException {
    return put(endPoint, null);
  }

  public RestResponse put(String endPoint, Object content) throws IOException {
    return putWithHeader(endPoint, null, content);
  }

  public RestResponse putWithHeader(String endPoint, Header header)
      throws IOException {
    return putWithHeader(endPoint, header, null);
  }

  public RestResponse putWithHeader(String endPoint, Header header,
      Object content) throws IOException {
    Request put = Request.Put(url + "/a" + endPoint);
    if (header != null) {
      put.addHeader(header);
    }
    if (content != null) {
      put.addHeader(new BasicHeader("Content-Type", "application/json"));
      put.body(new StringEntity(
          OutputFormat.JSON_COMPACT.newGson().toJson(content),
          Charsets.UTF_8.name()));
    }
    return execute(put);
  }

  public RestResponse putRaw(String endPoint, RawInput stream) throws IOException {
    Preconditions.checkNotNull(stream);
    Request put = Request.Put(url + "/a" + endPoint);
    put.addHeader(new BasicHeader("Content-Type", stream.getContentType()));
    put.body(new BufferedHttpEntity(
        new InputStreamEntity(
            stream.getInputStream(),
            stream.getContentLength())));
    return execute(put);
  }

  public RestResponse post(String endPoint) throws IOException {
    return post(endPoint, null);
  }

  public RestResponse post(String endPoint, Object content) throws IOException {
    Request post = Request.Post(url + "/a" + endPoint);
    if (content != null) {
      post.addHeader(new BasicHeader("Content-Type", "application/json"));
      post.body(new StringEntity(
          OutputFormat.JSON_COMPACT.newGson().toJson(content),
          Charsets.UTF_8.name()));
    }
    return execute(post);
  }

  public RestResponse delete(String endPoint) throws IOException {
    return execute(Request.Delete(url + "/a" + endPoint));
  }


  public static RawInput newRawInput(String content) {
    return newRawInput(content.getBytes(StandardCharsets.UTF_8));
  }

  public static RawInput newRawInput(final byte[] bytes) {
    Preconditions.checkNotNull(bytes);
    Preconditions.checkArgument(bytes.length > 0);
    return new RawInput() {
      @Override
      public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(bytes);
      }

      @Override
      public String getContentType() {
        return "application/octet-stream";
      }

      @Override
      public long getContentLength() {
        return bytes.length;
      }
    };
  }
}
