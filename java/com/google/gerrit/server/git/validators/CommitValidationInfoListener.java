// Copyright (C) 2024 The Android Open Source Project
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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.events.CommitReceivedEvent;

/**
 * Extension point that is invoked after a commit has passed the validations that are done by {@code
 * CommitValidationListener}'s.
 *
 * <p>If any {@code CommitValidationListener} rejects the commit (by throwing a {@code
 * CommitValidationException}) this extension point is not invoked.
 */
@ExtensionPoint
public interface CommitValidationInfoListener {
  /**
   * Invoked after a commit has passed the validation that is done by {@code
   * CommitValidationListener}'s
   *
   * <p>Not invoked if any {@code CommitValidationListener} rejects the commit (by throwing a {@code
   * CommitValidationException}).
   *
   * @param validationInfoByValidator Map that maps a validator name to a {@link
   *     CommitValidationInfo} (result of running the validator).
   * @param receiveEvent The receive event for which the validation was done. Contains data about
   *     which commit was validated and what is the updated ref.
   * @param patchSetId if the validation was done for a patch set, the ID of the patch set,
   *     otherwise {@code null}
   */
  void commitValidated(
      ImmutableMap<String, CommitValidationInfo> validationInfoByValidator,
      CommitReceivedEvent receiveEvent,
      @Nullable PatchSet.Id patchSetId);
}
