// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import java.util.Map;
import java.util.Optional;

/** Extension point that is invoked on post review. */
@ExtensionPoint
public interface OnPostReview {
  /**
   * Allows implementors to return a message that should be included into the change message that is
   * posted on post review.
   *
   * @param user the user that posts the review
   * @param changeNotes the change on which post review is performed
   * @param patchSet the patch set on which post review is performed
   * @param oldApprovals old approvals that changed as result of post review
   * @param approvals all current approvals
   * @return message that should be included into the change message that is posted on post review,
   *     {@link Optional#empty()} if the change message should not be extended
   */
  default Optional<String> getChangeMessageAddOn(
      IdentifiedUser user,
      ChangeNotes changeNotes,
      PatchSet patchSet,
      Map<String, Short> oldApprovals,
      Map<String, Short> approvals) {
    return Optional.empty();
  }
}
