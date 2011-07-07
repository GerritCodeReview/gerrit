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

package com.google.gerrit.test;

class InvalidPropertyValueException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  InvalidPropertyValueException(final String propertyName) {
    this(propertyName, null, null);
  }

  InvalidPropertyValueException(final String propertyName, final Exception cause) {
    this(propertyName, null, cause);
  }

  InvalidPropertyValueException(final String propertyName,
      final String message, final Exception cause) {
    super("The system property '" + propertyName + "' is set incorrectly."
        + (message != null ? " " + message : ""), cause);
  }
}
