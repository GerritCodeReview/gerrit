// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;


public class ChangeMessages {
  public String revertChangeDefaultMessage = "Revert \"{0}\"\n\nThis reverts commit {1}.";
  public String revertSubmissionDefaultMessage = "This reverts commit {0}.";
  public String revertSubmissionUserMessage = "Revert \"{0}\"\n\n{1}";
  public String revertSubmissionOfRevertSubmissionUserMessage = "Revert^{0} \"{1}\"\n\n{2}";

  public String reviewerCantSeeChange = "{0} does not have permission to see this change";
  public String reviewerInvalid = "{0} is not a valid user identifier";
  public String reviewerNotFoundUserOrGroup = "{0} does not identify a registered user or group";

  public String groupRemovalIsNotAllowed =
      "Groups can't be removed from reviewers, so can't remove {0}.";
  public String groupIsNotAllowed = "The group {0} cannot be added as reviewer.";
  public String groupHasTooManyMembers =
      "The group {0} has too many members to add them all as reviewers.";
  public String groupManyMembersConfirmation =
      "The group {0} has {1} members. Do you want to add them all as reviewers?";
}
