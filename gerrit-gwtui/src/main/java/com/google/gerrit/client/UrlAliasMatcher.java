// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gwt.regexp.shared.RegExp;
import java.util.HashMap;
import java.util.Map;

public class UrlAliasMatcher {
  private final Map<RegExp, String> userUrlAliases;
  private final Map<RegExp, String> globalUrlAliases;

  UrlAliasMatcher(Map<String, String> globalUrlAliases) {
    this.globalUrlAliases = compile(globalUrlAliases);
    this.userUrlAliases = new HashMap<>();
  }

  private static Map<RegExp, String> compile(Map<String, String> urlAliases) {
    Map<RegExp, String> compiledUrlAliases = new HashMap<>();
    if (urlAliases != null) {
      for (Map.Entry<String, String> e : urlAliases.entrySet()) {
        compiledUrlAliases.put(RegExp.compile(e.getKey()), e.getValue());
      }
    }
    return compiledUrlAliases;
  }

  void clearUserAliases() {
    this.userUrlAliases.clear();
  }

  void updateUserAliases(Map<String, String> userUrlAliases) {
    clearUserAliases();
    this.userUrlAliases.putAll(compile(userUrlAliases));
  }

  public String replace(String token) {
    for (Map.Entry<RegExp, String> e : userUrlAliases.entrySet()) {
      RegExp pat = e.getKey();
      if (pat.exec(token) != null) {
        return pat.replace(token, e.getValue());
      }
    }

    for (Map.Entry<RegExp, String> e : globalUrlAliases.entrySet()) {
      RegExp pat = e.getKey();
      if (pat.exec(token) != null) {
        return pat.replace(token, e.getValue());
      }
    }
    return token;
  }
}
