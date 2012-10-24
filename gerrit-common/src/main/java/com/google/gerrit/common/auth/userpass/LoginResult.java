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

package com.google.gerrit.common.auth.userpass;


public class LoginResult {
  public boolean success;
  public boolean isNew;

  protected String authType;
  protected Error error;

  protected LoginResult() {
  }

  public LoginResult(final String authType) {
    this.authType = authType;
  }

  public String getAuthType() {
    return authType;
  }

  public void setError(final Error error) {
    this.error = error;
    success = error == null;
  }

  public Error getError() {
    return error;
  }

  public static enum Error {
    /** Username or password are invalid */
    INVALID_LOGIN,

    /** The authentication server is unavailable or the query to it timed out */
    AUTHENTICATION_UNAVAILABLE
  }
}
