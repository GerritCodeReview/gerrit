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

import java.util.Objects;

/**
 * Message used as result of a validation that run during a git operation (for example {@code git
 * push}. Intended to be shown to users.
 */
public class ValidationMessage {
  public enum Type {
    FATAL("FATAL: "),
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

  /** @see ValidationMessage */
  public ValidationMessage(String message, Type type) {
    this.message = message;
    this.type = type;
  }

  // TODO: Remove and move callers to ValidationMessage(String message, Type type)
  public ValidationMessage(String message, boolean isError) {
    this.message = message;
    this.type = (isError ? Type.ERROR : Type.OTHER);
  }

  /** Returns the message to be shown to the user. */
  public String getMessage() {
    return message;
  }

  /**
   * Returns the {@link Type}. Used to as prefix for the message in the git CLI and to color
   * messages.
   */
  public Type getType() {
    return type;
  }

  /**
   * Returns {@code true} if this message is an error. Used to decide if the operation should be
   * aborted.
   */
  public boolean isError() {
    return type == Type.FATAL || type == Type.ERROR;
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, type);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ValidationMessage) {
      ValidationMessage other = (ValidationMessage) obj;
      return Objects.equals(message, other.message) && Objects.equals(type, other.type);
    }
    return false;
  }

  @Override
  public String toString() {
    return getType() + ": " + getMessage();
  }
}
