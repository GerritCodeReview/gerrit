// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.project.constraints;

/**
 * Thrown when an "allow" key of an "access" (sub)section in gerrit.config
 * contains an invalid value, like for example an non-parseable lower/upper
 * range number.
 */
public class InvalidConstraintException extends Exception {

  private static final long serialVersionUID = 1L;

  public static final String MESSAGE = "Invalid allow constraint: ";

  private String invalidAllowValue;

  public InvalidConstraintException(String invalidAllowValue, Throwable cause) {
    super(MESSAGE + invalidAllowValue, cause);
    this.invalidAllowValue = invalidAllowValue;
  }

  public String getInvalidAllowValue() {
    return invalidAllowValue;
  }
}
