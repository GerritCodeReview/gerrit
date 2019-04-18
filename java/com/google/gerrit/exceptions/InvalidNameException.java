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

package com.google.gerrit.exceptions;

/** Error indicating the entity name is invalid as supplied. */
public class InvalidNameException extends Exception {
  private static final long serialVersionUID = 1L;

  public static final String MESSAGE = "Invalid Name";

  public InvalidNameException() {
    super(MESSAGE);
  }

  public InvalidNameException(String invalidName) {
    super(MESSAGE + ": " + invalidName);
  }
}
