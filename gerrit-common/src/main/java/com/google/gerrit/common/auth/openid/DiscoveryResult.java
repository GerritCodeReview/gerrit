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

package com.google.gerrit.common.auth.openid;

import java.util.Map;

public final class DiscoveryResult {
  public static enum Status {
    /** Provider was discovered and {@code providerUrl} is valid. */
    VALID,

    /** The identifier is not allowed to be used, by site configuration. */
    NOT_ALLOWED,

    /** Identifier isn't for an OpenID provider. */
    NO_PROVIDER,

    /** The provider was discovered, but something else failed. */
    ERROR;
  }

  public Status status;
  public String statusMessage;
  public String providerUrl;
  public Map<String, String> providerArgs;

  protected DiscoveryResult() {
  }

  public DiscoveryResult(final String redirect, final Map<String, String> args) {
    status = Status.VALID;
    providerUrl = redirect;
    providerArgs = args;
  }

  public DiscoveryResult(final Status s) {
    status = s;
  }

  public DiscoveryResult(final Status s, final String message) {
    status = s;
    statusMessage = message;
  }
}
