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

package com.google.gerrit.acceptance.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class NoteDbOnlyIT extends AbstractDaemonTest {
  @Inject private BatchUpdate.Factory batchUpdateFactory;

  @Before
  public void setUp() throws Exception {
    assume().that(NoteDbMode.get()).isEqualTo(NoteDbMode.DISABLE_CHANGE_REVIEW_DB);
  }

  @Test
  public void updateChangeFailureRollsBackRefUpdate() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    String master = "refs/heads/master";
    String backup = "refs/backup/master";
    ObjectId master1 = getRef(master).get();
    assertThat(getRef(backup)).isEmpty();

    // Toy op that copies the value of refs/heads/master to refs/backup/master.
    BatchUpdateOp backupMasterOp =
        new BatchUpdateOp() {
          ObjectId newId;

          @Override
          public void updateRepo(RepoContext ctx) throws IOException {
            ObjectId oldId = ctx.getRepoView().getRef(backup).orElse(ObjectId.zeroId());
            newId = ctx.getRepoView().getRef(master).get();
            ctx.addRefUpdate(oldId, newId, backup);
          }

          @Override
          public boolean updateChange(ChangeContext ctx) {
            ctx.getUpdate(ctx.getChange().currentPatchSetId())
                .setChangeMessage("Backed up master branch to " + newId.name());
            return true;
          }
        };

    try (BatchUpdate bu = newBatchUpdate()) {
      bu.addOp(id, backupMasterOp);
      bu.execute();
    }

    // Ensure backupMasterOp worked.
    assertThat(getRef(backup)).hasValue(master1);
    assertThat(getMessages(id)).contains("Backed up master branch to " + master1.name());

    // Advance master by submitting the change.
    gApi.changes().id(id.get()).current().review(ReviewInput.approve());
    gApi.changes().id(id.get()).current().submit();
    ObjectId master2 = getRef(master).get();
    assertThat(master2).isNotEqualTo(master1);
    int msgCount = getMessages(id).size();

    try (BatchUpdate bu = newBatchUpdate()) {
      // This time, we attempt to back up master, but we fail during updateChange.
      bu.addOp(id, backupMasterOp);
      String msg = "Change is bad";
      bu.addOp(
          id,
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) throws ResourceConflictException {
              throw new ResourceConflictException(msg);
            }
          });
      try {
        bu.execute();
        assert_().fail("expected ResourceConflictException");
      } catch (ResourceConflictException e) {
        assertThat(e).hasMessageThat().isEqualTo(msg);
      }
    }

    // If updateChange hadn't failed, backup would have been updated to master2.
    assertThat(getRef(backup)).hasValue(master1);
    assertThat(getMessages(id)).hasSize(msgCount);
  }

  private BatchUpdate newBatchUpdate() {
    return batchUpdateFactory.create(
        db, project, identifiedUserFactory.create(user.getId()), TimeUtil.nowTs());
  }

  private Optional<ObjectId> getRef(String name) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      return Optional.ofNullable(repo.exactRef(name)).map(Ref::getObjectId);
    }
  }

  private List<String> getMessages(Change.Id id) throws Exception {
    return gApi.changes()
        .id(id.get())
        .get(EnumSet.of(ListChangesOption.MESSAGES))
        .messages
        .stream()
        .map(m -> m.message)
        .collect(toList());
  }
}
