// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.server.auth.AuthRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HttpAuthRequest extends AuthRequest {

  private final HttpServletRequest req;
  private final HttpServletResponse resp;

  public HttpAuthRequest(String username, String password, HttpServletRequest req, HttpServletResponse resp) {
    super(username, password);
    this.req = checkNotNull(req);
    this.resp = checkNotNull(resp);
  }

  /** @return the HttpServletRequest representing the AuthRequest. */
  public HttpServletRequest getRequest() {
    return req;
  }

  /** @return the HttpServletResponse representing the AuthRequest. */
  public HttpServletResponse getResponse() {
    return resp;
  }
}
