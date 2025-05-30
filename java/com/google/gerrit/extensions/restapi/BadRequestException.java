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

/** Request could not be parsed as sent (HTTP 400 Bad Request). */
public class BadRequestException extends RestApiException {
  private static final long serialVersionUID = 1L;

  /**
   * @param msg error text for client describing how request is bad.
   */
  public BadRequestException(String msg) {
    super(msg);
  }

  /**
   * @param msg error text for client describing how request is bad.
   * @param cause cause of this exception.
   */
  public BadRequestException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
