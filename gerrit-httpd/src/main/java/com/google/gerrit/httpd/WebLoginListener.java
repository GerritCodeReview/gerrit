// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.IdentifiedUser;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Allows to listen and override the reponse to login/logout web actions.
 *
 * <p>Allows to intercept and act when a Gerrit user logs in or logs out of the Web interface to
 * perform actions or to override the output response status code.
 *
 * <p>Typical use can be multi-factor authentication (on login) or global sign-out from SSO systems
 * (on logout).
 */
@ExtensionPoint
public interface WebLoginListener {

  /**
   * Invoked after a user's web login.
   *
   * @param userId logged in user
   * @param request request of the latest login action
   * @param response response of the latest login action
   */
  void onLogin(IdentifiedUser userId, HttpServletRequest request, HttpServletResponse response)
      throws IOException;

  /**
   * Invoked after a user's web logout.
   *
   * @param userId logged out user
   * @param request request of the latest logout action
   * @param response response of the latest logout action
   */
  void onLogout(IdentifiedUser userId, HttpServletRequest request, HttpServletResponse response)
      throws IOException;
}
