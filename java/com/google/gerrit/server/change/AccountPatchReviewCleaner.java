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

package com.google.gerrit.server.change;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.inject.Inject;
import java.util.Collection;

public class AccountPatchReviewCleaner {
  private final ChangeNotes.Factory notesFactory;
  private final PatchSetUtil psUtil;
  private final PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore;

  @Inject
  AccountPatchReviewCleaner(
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil,
      PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore) {
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
    this.accountPatchReviewStore = accountPatchReviewStore;
  }

  public void clean(Change change) {
    ChangeNotes notes = notesFactory.createChecked(change);
    Collection<PatchSet> patchSets = psUtil.byChange(notes);
    for (PatchSet ps : patchSets) {
      accountPatchReviewStore.run(s -> s.clearReviewed(ps.getId()));
    }
  }
}
