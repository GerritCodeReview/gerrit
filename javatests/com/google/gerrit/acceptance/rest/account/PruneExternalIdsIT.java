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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdHistoryPruner;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIdReader;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class PruneExternalIdsIT extends AbstractDaemonTest {
  private static AllUsersName ALL_USERS = new AllUsersName(AllUsersNameProvider.DEFAULT);

  @Inject private ExternalIdReader externalIdReader;
  @Inject private ExternalIdFactory externalIdFactory;
  @Inject private ExternalIdHistoryPruner externalIdHistoryPruner;

  @Before
  public void setup() throws Exception {
    // Reset all external IDs by deleting refs/meta/external-ids
    try (Repository repo = repoManager.openRepository(ALL_USERS)) {
      RefUpdate u = repo.updateRef(RefNames.REFS_EXTERNAL_IDS);
      u.setForceUpdate(true);
      RefUpdate.Result result = u.delete();
      checkState(result == Result.FORCED);
    }
  }

  @Test
  public void removesSingleCommit() throws Exception {
    insertExternalId(age(31), 1, 1);
    insertExternalId(age(1), 2, 1);
    insertExternalId(age(1), 3, 1);
    insertExternalId(age(1), 4, 1);

    assertThat(numCommitsInExternalIdRef()).isEqualTo(4);
    assertThat(externalIdReader.all().size()).isEqualTo(4);

    externalIdHistoryPruner.prune();

    assertThat(numCommitsInExternalIdRef()).isEqualTo(3);
    assertThat(externalIdReader.all().size()).isEqualTo(4);
  }

  @Test
  public void removesMultipleCommits() throws Exception {
    insertExternalId(age(32), 1, 1);
    insertExternalId(age(31), 2, 1);
    insertExternalId(age(1), 3, 1);
    insertExternalId(age(1), 4, 1);
    insertExternalId(age(1), 5, 1);

    assertThat(numCommitsInExternalIdRef()).isEqualTo(5);
    assertThat(externalIdReader.all().size()).isEqualTo(5);

    externalIdHistoryPruner.prune();

    assertThat(numCommitsInExternalIdRef()).isEqualTo(3);
    assertThat(externalIdReader.all().size()).isEqualTo(5);
  }

  @Test
  public void singleOldCommitUntouched() throws Exception {
    // We don't want to constantly rewrite commits of external ID branches that
    // have not been updated within our retention window.
    insertExternalId(age(32), 1, 1);
    ObjectId oldRev = externalIdRev();

    assertThat(numCommitsInExternalIdRef()).isEqualTo(1);
    assertThat(externalIdReader.all().size()).isEqualTo(1);

    externalIdHistoryPruner.prune();

    assertThat(numCommitsInExternalIdRef()).isEqualTo(1);
    assertThat(externalIdReader.all().size()).isEqualTo(1);
    assertThat(oldRev).isEqualTo(externalIdRev()); // No change
  }

  @Test
  public void multipleOldCommitsPruned() throws Exception {
    // If all commits are outside of our retention window and there is more than
    // a single commit on the branch, we do want to prune once.
    insertExternalId(age(32), 1, 1);
    insertExternalId(age(31), 2, 1);

    assertThat(numCommitsInExternalIdRef()).isEqualTo(2);
    assertThat(externalIdReader.all().size()).isEqualTo(2);

    externalIdHistoryPruner.prune();

    assertThat(numCommitsInExternalIdRef()).isEqualTo(1);
    assertThat(externalIdReader.all().size()).isEqualTo(2);
  }

  @Test
  public void noOpOnMissingExternalIdRef() throws Exception {
    assertThat(numCommitsInExternalIdRef()).isEqualTo(0);
    externalIdHistoryPruner.prune();
    assertThat(numCommitsInExternalIdRef()).isEqualTo(0);
  }

  @Test
  public void treeRemainsUnchangedWhenPruned() throws Exception {
    insertExternalId(age(31), 1, 1);
    insertExternalId(age(1), 2, 1);
    insertExternalId(age(1), 3, 1);
    insertExternalId(age(1), 4, 1);
    ObjectId treeOfTipCommit = treeOfTipCommit();

    assertThat(numCommitsInExternalIdRef()).isEqualTo(4);
    assertThat(externalIdReader.all().size()).isEqualTo(4);

    externalIdHistoryPruner.prune();

    assertThat(numCommitsInExternalIdRef()).isEqualTo(3);
    assertThat(externalIdReader.all().size()).isEqualTo(4);
    assertThat(treeOfTipCommit).isEqualTo(treeOfTipCommit());
  }

  private static Instant age(int days) {
    return Instant.now().minus(Duration.ofDays(days));
  }

  private ObjectId externalIdRev() throws IOException {
    try (Repository repo = repoManager.openRepository(ALL_USERS)) {
      return repo.exactRef(RefNames.REFS_EXTERNAL_IDS).getObjectId();
    }
  }

  private int numCommitsInExternalIdRef() throws IOException {
    try (Repository repo = repoManager.openRepository(ALL_USERS);
        RevWalk walk = new RevWalk(repo);
        ObjectInserter ins = repo.newObjectInserter()) {
      Ref externalIdRef = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
      if (externalIdRef == null) {
        return 0;
      }
      walk.markStart(walk.parseCommit(externalIdRef.getObjectId()));
      int numCommits = 0;
      while (walk.next() != null) {
        numCommits++;
      }
      return numCommits;
    }
  }

  private ObjectId treeOfTipCommit() throws IOException {
    try (Repository repo = repoManager.openRepository(ALL_USERS);
        RevWalk walk = new RevWalk(repo)) {
      return walk.parseCommit(repo.exactRef(RefNames.REFS_EXTERNAL_IDS).getObjectId())
          .getTree()
          .toObjectId();
    }
  }

  private ObjectId insertExternalId(Instant when, int key, int accountId) throws Exception {
    return performExternalIdUpdate(
        when,
        u -> {
          try {
            u.insert(externalId(key, accountId));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private ExternalId externalId(int key, int accountId) {
    return externalIdFactory.create("fooschema", "bar" + key, Account.id(accountId));
  }

  private ObjectId performExternalIdUpdate(Instant when, Consumer<ExternalIdNotes> update)
      throws Exception {
    try (Repository repo = repoManager.openRepository(ALL_USERS)) {
      PersonIdent updater = new PersonIdent("Foo bar", "foo@bar.com", when, ZoneId.systemDefault());
      ExternalIdNotes extIdNotes = ExternalIdNotes.load(ALL_USERS, repo, externalIdFactory, false);
      update.accept(extIdNotes);
      try (MetaDataUpdate metaDataUpdate =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, null, repo)) {
        metaDataUpdate.getCommitBuilder().setAuthor(updater);
        metaDataUpdate.getCommitBuilder().setCommitter(updater);
        return extIdNotes.commit(metaDataUpdate).getId();
      }
    }
  }
}
