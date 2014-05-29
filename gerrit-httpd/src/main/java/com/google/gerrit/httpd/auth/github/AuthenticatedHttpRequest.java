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

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import com.google.common.collect.Iterators;

public class AuthenticatedHttpRequest extends HttpServletRequestWrapper {
  private HashMap<String, String> headers = new HashMap<String, String>();

  public AuthenticatedHttpRequest(HttpServletRequest request,
      String... headerNamesValues) {
    super(request);

    for (int i = 0; i < headerNamesValues.length;) {
      String name = headerNamesValues[i++];
      String value = headerNamesValues[i++];
      if (name != null && value != null) {
        headers.put(name, value);
      }
    }
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    final Enumeration<String> wrappedHeaderNames = super.getHeaderNames();
    HashSet<String> headerNames = new HashSet<String>(headers.keySet());
    while (wrappedHeaderNames.hasMoreElements()) {
      headerNames.add(wrappedHeaderNames.nextElement());
    }
    return Iterators.asEnumeration(headerNames.iterator());
  }

  @Override
  public String getHeader(String name) {
    String headerValue = headers.get(name);
    if (headerValue != null) {
      return headerValue;
    } else {
      return super.getHeader(name);
    }
  }
}
