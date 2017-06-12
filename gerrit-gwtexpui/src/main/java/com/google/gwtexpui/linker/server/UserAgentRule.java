// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gwtexpui.linker.server;

import static java.util.regex.Pattern.compile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

/**
 * Selects the value for the {@code user.agent} property.
 *
 * <p>Examines the {@code User-Agent} HTTP request header, and tries to match it to known {@code
 * user.agent} values.
 *
 * <p>Ported from JavaScript in {@code com.google.gwt.user.UserAgent.gwt.xml}.
 */
public class UserAgentRule {
  private static final Pattern msie = compile(".*msie ([0-11]+)\\.([0-11]+).*");
  private static final Pattern gecko = compile(".*rv:([0-9]+)\\.([0-9]+).*");

  public String getName() {
    return "user.agent";
  }

  public String select(HttpServletRequest req) {
    String ua = req.getHeader("User-Agent");
    if (ua == null) {
      return null;
    }

    ua = ua.toLowerCase();

    if (ua.contains("opera")) {
      return "opera";

    } else if (ua.contains("webkit")) {
      return "safari";

    } else if (ua.contains("msie")) {
      // GWT 2.0 uses document.documentMode here, which we can't do
      // on the server side.

      Matcher m = msie.matcher(ua);
      if (m.matches() && m.groupCount() == 2) {
        int v = makeVersion(m);
        if (v >= 11000) {
          return "ie11";
        }
        if (v >= 10000) {
          return "ie10";
        }
        if (v >= 9000) {
          return "ie9";
        }
        if (v >= 8000) {
          return "ie8";
        }
      }
      return null;

    } else if (ua.contains("edge")) {
      return "edge";
    } else if (ua.contains("gecko")) {
      Matcher m = gecko.matcher(ua);
      if (m.matches() && m.groupCount() == 2) {
        if (makeVersion(m) >= 1008) {
          return "gecko1_8";
        }
      }
      return "gecko";
    }

    return null;
  }

  private int makeVersion(Matcher result) {
    return (Integer.parseInt(result.group(1)) * 1000) + Integer.parseInt(result.group(2));
  }
}
