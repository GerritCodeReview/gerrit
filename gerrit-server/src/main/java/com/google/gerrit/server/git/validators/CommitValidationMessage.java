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

package com.google.gerrit.server.git.validators;

public class CommitValidationMessage {
  private final String message;
  private final boolean isError;

  public CommitValidationMessage(final String message, final boolean isError) {
    this.message = message;
    this.isError = isError;
  }

  public String getMessage() {
    return message;
  }

  public boolean isError() {
    return isError;
  }
}
