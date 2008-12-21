// Copyright 2008 Google Inc.
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

package com.google.gerrit.server;

import com.dyuproject.openid.Discovery;
import com.dyuproject.openid.OpenIdContext;
import com.dyuproject.openid.OpenIdUser;

import java.util.HashMap;
import java.util.Map;

/** Discovery support for Google Accounts and other standard OpenID providers */
public class GoogleAccountDiscovery implements Discovery {
  /** OpenID discovery end-point for Google Accounts */
  public static final String GOOGLE_ACCOUNT =
      "https://www.google.com/accounts/o8/id";

  private final Discovery base;

  public GoogleAccountDiscovery(final Discovery base) {
    this.base = base;
  }

  public OpenIdUser discover(final String claimedId, final OpenIdContext context)
      throws Exception {
    if (GOOGLE_ACCOUNT.equals(claimedId)) {
      // TODO We shouldn't hard-code the XRDS discovery result.
      //
      final Map<String, String> m = new HashMap<String, String>();
      m.put("ci", claimedId);
      m.put("os", "https://www.google.com/accounts/o8/ud");

      final OpenIdUser u = new OpenIdUser();
      u.fromJSON(m);
      return u;
    }

    return base.discover(claimedId, context);
  }
}
