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

package com.google.gerrit.server.auth.openid;

import com.google.gerrit.reviewdb.client.AccountExternalId;

public class OpenIdProviderPattern {
  public static OpenIdProviderPattern create(String pattern) {
    OpenIdProviderPattern r = new OpenIdProviderPattern();
    r.regex = pattern.startsWith("^") && pattern.endsWith("$");
    r.pattern = pattern;
    return r;
  }

  protected boolean regex;
  protected String pattern;

  protected OpenIdProviderPattern() {}

  public boolean matches(String id) {
    return regex ? id.matches(pattern) : id.startsWith(pattern);
  }

  public boolean matches(AccountExternalId id) {
    return matches(id.getExternalId());
  }

  @Override
  public String toString() {
    return pattern;
  }
}
