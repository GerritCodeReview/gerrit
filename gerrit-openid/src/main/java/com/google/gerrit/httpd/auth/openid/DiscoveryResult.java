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

package com.google.gerrit.httpd.auth.openid;

import java.util.Map;

final class DiscoveryResult {
  enum Status {
    /** Provider was discovered and {@code providerUrl} is valid. */
    VALID,

    /** Identifier isn't for an OpenID provider. */
    NO_PROVIDER,

    /** The provider was discovered, but something else failed. */
    ERROR
  }

  Status status;
  String providerUrl;
  Map<String, String> providerArgs;

  DiscoveryResult() {}

  DiscoveryResult(String redirect, Map<String, String> args) {
    status = Status.VALID;
    providerUrl = redirect;
    providerArgs = args;
  }

  DiscoveryResult(Status s) {
    status = s;
  }
}
