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

package com.google.gerrit.client.openid;

import java.util.Map;

public final class DiscoveryResult {
  public boolean validProvider;
  public String providerUrl;
  public Map<String, String> providerArgs;

  protected DiscoveryResult() {
  }

  public DiscoveryResult(final boolean valid, final String redirect,
      final Map<String, String> args) {
    validProvider = valid;
    providerUrl = redirect;
    providerArgs = args;
  }

  public DiscoveryResult(final boolean fail) {
    this(false, null, null);
  }
}
