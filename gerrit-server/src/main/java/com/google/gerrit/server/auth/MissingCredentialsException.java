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

package com.google.gerrit.server.auth;

/**
 * An authentication exception that is thrown when the credentials are not present. This indicates
 * that the AuthBackend has none of the needed information in the request to perform authentication.
 * If parts of the authentication information is available to the backend, then a different
 * AuthException should be used.
 */
public class MissingCredentialsException extends AuthException {
  private static final long serialVersionUID = -6499866977513508051L;

  public MissingCredentialsException() {}

  public MissingCredentialsException(String msg) {
    super(msg);
  }

  public MissingCredentialsException(Throwable ex) {
    super(ex);
  }

  public MissingCredentialsException(String msg, Throwable ex) {
    super(msg, ex);
  }
}
