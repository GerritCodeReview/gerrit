// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.rules;

import static com.google.gerrit.rules.StoredValue.create;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.ChangeControl;

public final class StoredValues {
  public static final StoredValue<ReviewDb> REVIEW_DB = create(ReviewDb.class);
  public static final StoredValue<Change> CHANGE = create(Change.class);
  public static final StoredValue<PatchSet.Id> PATCH_SET_ID = create(PatchSet.Id.class);
  public static final StoredValue<ChangeControl> CHANGE_CONTROL = create(ChangeControl.class);

  private StoredValues() {
  }
}
