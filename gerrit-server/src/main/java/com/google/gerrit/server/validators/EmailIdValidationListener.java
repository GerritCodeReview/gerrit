// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.validators;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;


/**
 *
 * Listener to provide validation for committer and author email-ids
 *
 * Invoked by Gerrit when creating the committer/author identities for new
 * commits created during change edit, rebase, revert and submit operations.
 *
 */
@ExtensionPoint
public interface EmailIdValidationListener {
  /**
   * Validate if an email-id can be used as committer email when creating
   * new commits
   *
   * @param project The project against which the email-id is verified
   * @param email The email-id to be validated
   * @param user The user as IdentifiedUser
   * @return A boolean value based on the validation done
   */
  boolean isCommitterEmailIdValid(Project.NameKey project, String email,
      IdentifiedUser user);

  /**
   * Validate if an email-id can be used as author email when creating
   * new commits
   *
   * @param project The project against which the email-id is verified
   * @param email The email-id to be validated
   * @param user The user as IdentifiedUser
   * @return A boolean value based on the validation done
   */
  boolean isAuthorEmailIdValid(Project.NameKey project, String email,
      IdentifiedUser user);
}