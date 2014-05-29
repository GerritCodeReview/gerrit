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

package com.google.gerrit.httpd.auth.github;

import com.google.common.collect.Iterators;

import java.util.Arrays;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

class AuthenticatedHttpRequest extends HttpServletRequestWrapper {
  private final String usernameHeader;
  private final String username;

  AuthenticatedHttpRequest(HttpServletRequest request,
      String usernameHeader, String username) {
    super(request);
    this.usernameHeader = usernameHeader;
    this.username = username;
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    return Iterators.asEnumeration(
        Iterators.concat(Iterators.forEnumeration(super.getHeaderNames()),
            Arrays.asList(usernameHeader).iterator()));
  }

  @Override
  public String getHeader(String name) {
    if (name.equalsIgnoreCase(usernameHeader)) {
      return username;
    } else {
      return super.getHeader(name);
    }
  }
}
