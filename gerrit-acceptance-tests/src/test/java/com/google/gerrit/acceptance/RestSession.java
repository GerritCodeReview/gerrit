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

import com.google.gson.Gson;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;

import java.io.IOException;

public class RestSession {

  private final TestAccount account;
  DefaultHttpClient client;

  public RestSession(TestAccount account) {
    this.account = account;
  }

  public RestResponse get(String endPoint) throws IOException {
    HttpGet get = new HttpGet("http://localhost:8080/a" + endPoint);
    return new RestResponse(getClient().execute(get));
  }

  public RestResponse put(String endPoint) throws IOException {
    return put(endPoint, null);
  }

  public RestResponse put(String endPoint, Object content) throws IOException {
    HttpPut put = new HttpPut("http://localhost:8080/a" + endPoint);
    if (content != null) {
      put.addHeader(new BasicHeader("Content-Type", "application/json"));
      put.setEntity(new StringEntity((new Gson()).toJson(content), HTTP.UTF_8));
    }
    return new RestResponse(getClient().execute(put));
  }

  public RestResponse post(String endPoint) throws IOException {
    return post(endPoint, null);
  }

  public RestResponse post(String endPoint, Object content) throws IOException {
    HttpPost post = new HttpPost("http://localhost:8080/a" + endPoint);
    if (content != null) {
      post.addHeader(new BasicHeader("Content-Type", "application/json"));
      post.setEntity(new StringEntity((new Gson()).toJson(content), HTTP.UTF_8));
    }
    return new RestResponse(getClient().execute(post));
  }

  public RestResponse delete(String endPoint) throws IOException {
    HttpDelete delete = new HttpDelete("http://localhost:8080/a" + endPoint);
    return new RestResponse(getClient().execute(delete));
  }

  private DefaultHttpClient getClient() {
    if (client == null) {
      client = new DefaultHttpClient();
      client.getCredentialsProvider().setCredentials(
          new AuthScope("localhost", 8080),
          new UsernamePasswordCredentials(account.username, account.httpPassword));
    }
    return client;
  }
}
