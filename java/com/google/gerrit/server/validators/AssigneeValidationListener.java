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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;

/** Listener to provide validation of assignees. */
@ExtensionPoint
public interface AssigneeValidationListener {
  /**
   * Invoked by Gerrit before the assignee of a change is modified.
   *
   * @param change the change on which the assignee is changed
   * @param assignee the new assignee. Null if removed
   * @throws ValidationException if validation fails
   */
  void validateAssignee(Change change, Account assignee) throws ValidationException;
}
