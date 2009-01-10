// Copyright 2009 Google Inc.
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

class AllowFrameImpl {
  boolean permit(final String url) {
    if (OpenIdUtil.URL_GOOGLE.equals(url)
        || url.startsWith(OpenIdUtil.URL_GOOGLE + "?")) {
      return true;
    }
    if (is_claimID(url)) {
      return true;
    }
    return false;
  }

  protected static boolean is_claimID(final String url) {
    return url.contains(".claimid.com/");
  }
}
