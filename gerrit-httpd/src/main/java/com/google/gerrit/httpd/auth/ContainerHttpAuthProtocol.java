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


package com.google.gerrit.httpd.auth;

import com.google.gerrit.server.auth.PasswordCredentials;
import com.google.inject.Inject;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

@Singleton
public class ContainerHttpAuthProtocol
    implements HttpAuthProtocol.CredentialsExtractor<PasswordCredentials> {

  @Inject
  ContainerHttpAuthProtocol() {
  }

  @Override
  public PasswordCredentials extractCredentials(HttpServletRequest req)
      throws AuthProtocolException {
    String username = req.getRemoteUser();
    if (username == null) {
      return null;
    }
    // TODO: should this be subclassed?
    return new PasswordCredentials(username, "") {
      @Override
      public void checkPassword(String pwd) {
      }
    };
  }
}
