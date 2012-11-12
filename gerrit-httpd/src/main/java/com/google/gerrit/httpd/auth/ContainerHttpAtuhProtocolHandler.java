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

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.Locale;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ContainerHttpAtuhProtocolHandler implements
    HttpAuthProtocolHandler {

  private Config config;

  @Inject
  ContainerHttpAtuhProtocolHandler(@GerritServerConfig Config config) {
    this.config = config;
  }

  @Override
  public HttpAuthRequest handle(HttpServletRequest req, HttpServletResponse rsp)
      throws AuthProtocolException {
    String username = req.getRemoteUser();
    if (username == null) {
      try {
        rsp.sendError(SC_FORBIDDEN);
      } catch (IOException e) {
        // ignore
      }
      throw new AuthProtocolException("Access forbidden");
    }
    if (config.getBoolean("auth", "userNameToLowerCase", false)) {
      username = username.toLowerCase(Locale.US);
    }
    return new HttpAuthRequest(username, null, req, rsp);
  }


  @Override
  public HttpAuthRequest handleAnonymous(HttpServletRequest req,
      HttpServletResponse resp) {
    return new HttpAuthRequest(null, null, req, resp);
  }
}
