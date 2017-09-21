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

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.events.CommitReceivedEvent;
import java.util.List;

/**
 * Listener to provide validation on received commits.
 *
 * <p>Invoked by Gerrit when a new commit is received, has passed basic Gerrit validation and can be
 * then subject to extra validation checks.
 */
@ExtensionPoint
public interface CommitValidationListener {
  /**
   * Commit validation.
   *
   * @param receiveEvent commit event details
   * @return list of validation messages
   * @throws CommitValidationException if validation fails
   */
  List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException;
}
