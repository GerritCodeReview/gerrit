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
import com.google.gerrit.common.Nullable;
import java.io.IOException;
import java.net.URI;
import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;

public class HttpSession {
  protected TestAccount account;
  protected final String url;
  private final Executor executor;

  public HttpSession(GerritServer server, @Nullable TestAccount account) {
    this.url = CharMatcher.is('/').trimTrailingFrom(server.getUrl());
    URI uri = URI.create(url);
    this.executor = Executor.newInstance();
    this.account = account;
    if (account != null) {
      executor.auth(
          new HttpHost(uri.getHost(), uri.getPort()), account.username, account.httpPassword);
    }
  }

  public String url() {
    return url;
  }

  public RestResponse execute(Request request) throws IOException {
    return new RestResponse(executor.execute(request).returnResponse());
  }
}
