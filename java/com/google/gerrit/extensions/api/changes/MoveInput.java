// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.api.changes;

public class MoveInput {
  public String message;
  public String destinationBranch;

  /**
   * Whether or not to keep all votes in the destination branch. Keeping the votes can be confusing
   * in the context of the destination branch, see
   * https://gerrit-review.googlesource.com/c/gerrit/+/129171. That is why only the users with
   * {@link com.google.gerrit.server.permissions.GlobalPermission#ADMINISTRATE_SERVER} permissions
   * can use this option.
   *
   * <p>By default, only the veto votes that are blocking the change from submission are moved.
   */
  public boolean keepAllVotes;
}
