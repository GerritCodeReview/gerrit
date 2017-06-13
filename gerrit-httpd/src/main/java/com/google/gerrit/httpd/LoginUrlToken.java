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

package com.google.gerrit.httpd;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.gerrit.common.PageLinks;
import javax.servlet.http.HttpServletRequest;

public class LoginUrlToken {
  private static final String DEFAULT_TOKEN = '#' + PageLinks.MINE;

  public static String getToken(HttpServletRequest req) {
    String token = req.getPathInfo();
    if (Strings.isNullOrEmpty(token)) {
      return DEFAULT_TOKEN;
    }
    return CharMatcher.is('/').trimLeadingFrom(token);
  }

  private LoginUrlToken() {}
}
