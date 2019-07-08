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

import com.google.gerrit.server.validators.ValidationException;
import java.util.Collections;
import java.util.List;

public class CommitValidationException extends ValidationException {
  private static final long serialVersionUID = 1L;
  private final List<CommitValidationMessage> messages;

  public CommitValidationException(String reason, List<CommitValidationMessage> messages) {
    super(reason);
    this.messages = messages;
  }

  public CommitValidationException(String reason) {
    super(reason);
    this.messages = Collections.emptyList();
  }

  public CommitValidationException(String reason, Throwable why) {
    super(reason, why);
    this.messages = Collections.emptyList();
  }

  public List<CommitValidationMessage> getMessages() {
    return messages;
  }

  /** @return the reason string along with all validation messages. */
  public String getFullMessage() {
    StringBuilder sb = new StringBuilder(getMessage());
    if (!messages.isEmpty()) {
      sb.append(':');
      for (CommitValidationMessage msg : messages) {
        sb.append("\n  ").append(msg.getMessage());
      }
    }
    return sb.toString();
  }
}
