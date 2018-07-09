// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.restapi;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Implementing this extension point allows to adapt error 4xx and 5xx responses for REST API call.
 */
@ExtensionPoint
public interface RestApiErrorHandler {

  /**
   * Handles errors (4xx or 5xx) that occur for REST API calls.
   *
   * <p>This method controls which message should be returned to the client. In addition it may
   * change the status code of the response.
   *
   * @param req the HTTP servlet request
   * @param res the HTTP servlet response
   * @param msg the message that should be returned to the client
   * @param err the exception that caused the error, may be {@code null}
   * @return String the message that should be returned to the client
   */
  String handleError(
      HttpServletRequest req, HttpServletResponse res, String msg, @Nullable Throwable err);
}
