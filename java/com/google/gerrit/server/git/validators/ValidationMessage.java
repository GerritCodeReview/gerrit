// Copyright (C) 2014 The Android Open Source Project
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

public class ValidationMessage {
  public enum Type {
    ERROR("ERROR: "),
    WARNING("WARNING: "),
    HINT("hint: "),
    OTHER("");

    private final String prefix;

    Type(String prefix) {
      this.prefix = prefix;
    }

    public String getPrefix() {
      return prefix;
    }
  }

  private final String message;
  private final Type type;

  public ValidationMessage(String message, Type type) {
    this.message = message;
    this.type = type;
  }

  public ValidationMessage(String message, boolean isError) {
    this.message = message;
    this.type = (isError ? Type.ERROR : Type.OTHER);
  }

  public String getMessage() {
    return message;
  }

  public Type getType() {
    return type;
  }

  public boolean isError() {
    return type == Type.ERROR;
  }
}
