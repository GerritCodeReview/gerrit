// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.extensions.restapi;

/** Method is not acceptable on the resource (HTTP 405 Method Not Allowed). */
public class MethodNotAllowedException extends RestApiException {
  private static final long serialVersionUID = 1L;

  /**
   * @param msg error text for client describing why the method is not allowed.
   */
  public MethodNotAllowedException(String msg) {
    super(msg);
  }

  /**
   * @param msg error text for client describing why the method is not allowed.
   * @param cause reason for the method not being allowed.
   */
  public MethodNotAllowedException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
