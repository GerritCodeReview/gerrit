// Copyright (C) 2017 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
abstract class CherryPickDestination {
  /**
   * The {@code ObjectId} of the cherry-pick destination. It's the tip of a branch or the {@code
   * ObjectId} of a change revision.
   */
  abstract ObjectId mergeTip();

  /**
   * The full name of the destination branch for the cherry-pick. If the cherry-pick destination is
   * a change, then it will be the change's destination branch.
   */
  abstract String targetRef();

  /**
   * This field will be true if the cherry-pick destination is a change. It will be false if the
   * destination is a branch.
   */
  abstract boolean targetAtChange();

  public static CherryPickDestination create(
      ObjectId mergeTip, String targetRef, boolean targetAtChange) {
    return new AutoValue_CherryPickDestination(mergeTip, targetRef, targetAtChange);
  }
}
