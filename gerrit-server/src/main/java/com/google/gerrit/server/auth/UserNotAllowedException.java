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
 * An authentication exception that is thrown when the user credentials are valid, but not allowed
 * to authenticate for other reasons i.e. account disabled.
 */
public class UserNotAllowedException extends AuthException {
  private static final long serialVersionUID = -1531411999932922558L;

  public UserNotAllowedException() {}

  public UserNotAllowedException(String msg) {
    super(msg);
  }

  public UserNotAllowedException(Throwable ex) {
    super(ex);
  }

  public UserNotAllowedException(String msg, Throwable ex) {
    super(msg, ex);
  }
}
