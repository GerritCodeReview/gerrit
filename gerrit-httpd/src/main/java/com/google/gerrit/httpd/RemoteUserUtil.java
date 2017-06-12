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

package com.google.gerrit.httpd;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;

import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.util.Base64;

public class RemoteUserUtil {
  /**
   * Tries to get username from a request with following strategies:
   *
   * <ul>
   *   <li>ServletRequest#getRemoteUser
   *   <li>HTTP 'Authorization' header
   *   <li>Custom HTTP header
   * </ul>
   *
   * @param req request to extract username from.
   * @param loginHeader name of header which is used for extracting username.
   * @return the extracted username or null.
   */
  public static String getRemoteUser(HttpServletRequest req, String loginHeader) {
    if (AUTHORIZATION.equals(loginHeader)) {
      String user = emptyToNull(req.getRemoteUser());
      if (user != null) {
        // The container performed the authentication, and has the user
        // identity already decoded for us. Honor that as we have been
        // configured to honor HTTP authentication.
        return user;
      }

      // If the container didn't do the authentication we might
      // have done it in the front-end web server. Try to split
      // the identity out of the Authorization header and honor it.
      String auth = req.getHeader(AUTHORIZATION);
      return extractUsername(auth);
    }
    // Nonstandard HTTP header. We have been told to trust this
    // header blindly as-is.
    return emptyToNull(req.getHeader(loginHeader));
  }

  /**
   * Extracts username from an HTTP Basic or Digest authentication header.
   *
   * @param auth header value which is used for extracting.
   * @return username if available or null.
   */
  public static String extractUsername(String auth) {
    auth = emptyToNull(auth);

    if (auth == null) {
      return null;

    } else if (auth.startsWith("Basic ")) {
      auth = auth.substring("Basic ".length());
      auth = new String(Base64.decode(auth));
      final int c = auth.indexOf(':');
      return c > 0 ? auth.substring(0, c) : null;

    } else if (auth.startsWith("Digest ")) {
      final int u = auth.indexOf("username=\"");
      if (u <= 0) {
        return null;
      }
      auth = auth.substring(u + 10);
      final int e = auth.indexOf('"');
      return e > 0 ? auth.substring(0, e) : null;

    } else {
      return null;
    }
  }
}
