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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

class RestSession {

  private final UserAccount account;
  DefaultHttpClient client;

  RestSession(UserAccount account) {
    this.account = account;
  }

  Reader get(String endPoint) throws IOException {
    HttpGet get = new HttpGet("http://localhost:8080/a" + endPoint);
    HttpResponse response = getClient().execute(get);
    Reader reader = new InputStreamReader(response.getEntity().getContent());
    reader.skip(4);
    return reader;
  }

  Reader post(String endPoint) {
    // TODO
    return null;
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
