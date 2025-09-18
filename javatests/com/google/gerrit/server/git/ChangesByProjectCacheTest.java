// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableTable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.git.ChangesByProjectCacheImpl.CachedProjectChanges;
import com.google.gerrit.server.git.ChangesByProjectCacheImpl.CachedProjectChangesSerializer;
import com.google.gerrit.server.git.ChangesByProjectCacheImpl.PrivateChange;
import com.google.gerrit.server.notedb.AbstractChangeNotesTest;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.query.change.ChangeData;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public final class ChangesByProjectCacheTest extends AbstractChangeNotesTest {
  @Test
  public void cachedProjectChangesSerializer() throws Exception {
    ChangeData cd1 = createChange(false, true);
    ChangeData cd2 = createChange(true, true);
    ChangeData cd3 = createChange(true, false);
    CachedProjectChanges cachedProjectChanges = new CachedProjectChanges(List.of(cd1, cd2, cd3));

    CachedProjectChangesSerializer s = CachedProjectChangesSerializer.INSTANCE;
    CachedProjectChanges deserialized = s.deserialize(s.serialize(cachedProjectChanges));
    assertThat(deserialized.metaObjectIdByNonPrivateChangeByBranch)
        .isEqualTo(cachedProjectChanges.metaObjectIdByNonPrivateChangeByBranch);
    for (Map.Entry<Change.Id, PrivateChange> e : deserialized.privateChangeById.entrySet()) {
      PrivateChange deserializedChange = e.getValue();
      PrivateChange origChange = cachedProjectChanges.privateChangeById.get(e.getKey());
      assertThat(deserializedChange.metaRevision()).isEqualTo(origChange.metaRevision());
      assertThat(deserializedChange.reviewers()).isEqualTo(origChange.reviewers());
      assertThat(deserializedChange.change().getChangeId())
          .isEqualTo(origChange.change().getChangeId());
      assertThat(deserializedChange.weigh()).isEqualTo(origChange.weigh());
    }
  }

  private ChangeData createChange(boolean isPrivate, boolean hasReviewers) throws Exception {
    Change change = newChange();
    if (isPrivate) {
      ChangeUpdate update = newUpdate(change, changeOwner);
      update.setPrivate(true);
      update.commit();
    }
    ChangeNotes notes = newNotes(change);
    ChangeData cd =
        ChangeData.createForTest(
            project,
            change.getId(),
            Objects.requireNonNull(change.currentPatchSetId()).get(),
            ObjectId.zeroId(),
            null,
            changeNotesFactory,
            notes);
    if (hasReviewers) {
      cd.setReviewers(
          ReviewerSet.fromTable(
              ImmutableTable.<ReviewerStateInternal, Account.Id, Instant>builder()
                  .put(ReviewerStateInternal.CC, Account.id(1001), Instant.ofEpochMilli(1212L))
                  .put(
                      ReviewerStateInternal.REVIEWER, Account.id(1002), Instant.ofEpochMilli(1213L))
                  .build()));
    } else {
      cd.setReviewers(ReviewerSet.empty());
    }
    return cd;
  }
}
