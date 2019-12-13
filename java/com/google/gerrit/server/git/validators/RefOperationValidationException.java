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

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.validators.ValidationException;

/**
 * Exception to be thrown when the validation of a ref operation fails and should be aborted.
 * Examples of a ref operations include creating or updating refs.
 */
public class RefOperationValidationException extends ValidationException {
  private static final long serialVersionUID = 1L;
  private final ImmutableList<ValidationMessage> messages;

  public RefOperationValidationException(String reason, ImmutableList<ValidationMessage> messages) {
    super(reason);
    this.messages = messages;
  }

  public ImmutableList<ValidationMessage> getMessages() {
    return messages;
  }

  @Override
  public String getMessage() {
    return messages.stream()
        .map(ValidationMessage::getMessage)
        .collect(joining("\n", super.getMessage() + "\n", ""));
  }
}
