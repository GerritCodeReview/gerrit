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

import java.util.Collections;
import java.util.List;

public class ReceiveCommitValidationException extends Exception {
  private static final long serialVersionUID = 1L;
  private final List<ReceiveCommitValidationMessage> messages;

  public ReceiveCommitValidationException(String reason, List<ReceiveCommitValidationMessage> messages) {
    super(reason);
    this.messages = messages;
  }

  public ReceiveCommitValidationException(String reason) {
    super(reason);
    this.messages = Collections.emptyList();
  }

  public ReceiveCommitValidationException(String reason, Throwable why) {
    super(reason, why);
    this.messages = Collections.emptyList();
  }

  public List<ReceiveCommitValidationMessage> getMessages() {
    return messages;
  }
}
