// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Change;
import com.google.gerrit.exceptions.StorageException;

/**
 * Exception indicating that the change has received too many updates. Further actions apart from
 * {@code abandon} or {@code submit} are blocked.
 */
public class TooManyUpdatesException extends StorageException {
  @VisibleForTesting
  public static String message(Change.Id id, int maxUpdates) {
    return "Change "
        + id
        + " may not exceed "
        + maxUpdates
        + " updates. It may still be abandoned or submitted. To continue working on this "
        + "change, recreate it with a new Change-Id, then abandon this one.";
  }

  private static final long serialVersionUID = 1L;

  TooManyUpdatesException(Change.Id id, int maxUpdates) {
    super(message(id, maxUpdates));
  }
}
