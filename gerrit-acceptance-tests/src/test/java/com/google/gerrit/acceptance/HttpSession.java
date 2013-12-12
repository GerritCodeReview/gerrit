// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.base.CharMatcher;

import org.apache.http.Header;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.net.URI;

public class HttpSession {

  protected final String url;
  private final TestAccount account;
  private DefaultHttpClient client;

  public HttpSession(GerritServer server, TestAccount account) {
    this.url = CharMatcher.is('/').trimTrailingFrom(server.getUrl());
    this.account = account;
  }

  public HttpResponse get(String path, Header... header) throws IOException {
    HttpGet get = new HttpGet(url + path);
    for (Header h : header) {
      get.setHeader(h);
    }
    return new HttpResponse(getClient().execute(get));
  }

  protected DefaultHttpClient getClient() {
    if (client == null) {
      URI uri = URI.create(url);
      client = new DefaultHttpClient();
      client.getCredentialsProvider().setCredentials(
          new AuthScope(uri.getHost(), uri.getPort()),
          new UsernamePasswordCredentials(account.username, account.httpPassword));
    }
    return client;
  }
}
