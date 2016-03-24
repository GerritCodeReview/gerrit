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

package com.google.gerrit.acceptance.server.notedb;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.testutil.GerritServerTests.isNoteDbTestEnabled;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.NoteDbLoadHook;
import com.google.gerrit.server.schema.DisabledChangesReviewDbWrapper;
import com.google.gerrit.testutil.NoteDbChecker;
import com.google.gerrit.testutil.TestTimeUtil;
import static com.google.gerrit.common.TimeUtil.roundToSecond;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class ChangeRebuilderIT extends AbstractDaemonTest {
  @Inject
  private NoteDbChecker checker;

  @Inject
  private DynamicItem<NoteDbLoadHook> hookItem;

  @Inject
  private LastUpdatedLoadHook hook;

  @Inject
  private Provider<ReviewDb> dbProvider;

  @Inject
  private PatchLineCommentsUtil plcUtil;

  private RegistrationHandle hookHandle;

  @Before
  public void setUp() {
    assume().that(isNoteDbTestEnabled()).isFalse();
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    notesMigration.setAllEnabled(false);
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
    if (hookHandle != null) {
      hookHandle.remove();
    }
  }

  @Test
  public void changeFields() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    gApi.changes().id(id.get()).topic(name("a-topic"));
    checker.checkChanges(id);
  }

  @Test
  public void patchSets() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    r = amendChange(r.getChangeId());
    checker.checkChanges(id);
  }

  @Test
  public void rebuildAutomaticallyWhenChangeOutOfDate() throws Exception {
    hookHandle = hookItem.set(hook, getClass().getSimpleName());
    notesMigration.setAllEnabled(true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    assertUpToDate(true, id);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    notesMigration.setAllEnabled(false);
    gApi.changes().id(id.get()).topic(name("a-topic"));
    assertUpToDate(false, id);

    // On next NoteDb read, the change is transparently rebuilt.
    notesMigration.setAllEnabled(true);
    assertThat(gApi.changes().id(id.get()).info().topic)
        .isEqualTo(name("a-topic"));
    assertUpToDate(true, id);

    // Check that the bundles are equal.
    ChangeBundle actual = ChangeBundle.fromNotes(
        plcUtil, notesFactory.create(dbProvider.get(), project, id));
    ChangeBundle expected = ChangeBundle.fromReviewDb(unwrapDb(dbProvider), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
  }

  private void assertUpToDate(boolean expected, Change.Id id) throws Exception {
    try (Repository repo = repoManager.openMetadataRepository(project)) {
      assertThat(hook.isChangeUpToDate(repo, id)).isEqualTo(expected);
    }
  }

  /**
   * Sample implementation of {@link NoteDbLoadHook} that compares the last
   * updated on timestamp of the change to the commit time of the tip of
   * NoteDb.
   * <p>
   * Note that this is not an appropriate real-world implementation, since it
   * won't detect a change that is newer by less than 1 second, and it doesn't
   * handle out-of-order commits or multiple commits at the same timestamp.
   */
  private static class LastUpdatedLoadHook implements NoteDbLoadHook {
    private final Provider<ReviewDb> dbProvider;

    @Inject
    LastUpdatedLoadHook(Provider<ReviewDb> dbProvider) {
      this.dbProvider = dbProvider;
    }

    @Override
    public boolean isChangeUpToDate(Repository repo, Change.Id id)
        throws IOException {
      Date changeTs = getChangeLastUpdated(id);
      Date refTs = getRefLastUpdated(repo, id);
      checkState(changeTs.compareTo(refTs) >= 0,
          "expected time from Change record %s to be at least time from ref %s",
          changeTs, refTs);
      boolean result = refTs.equals(changeTs);
      return result;
    }

    private Date getChangeLastUpdated(Change.Id id) throws IOException {
      try {
        Change c = unwrapDb(dbProvider).changes().get(id);
        return c != null
            ? new Date(roundToSecond(c.getLastUpdatedOn()).getTime())
            : new Date(0);
      } catch (OrmException e) {
        throw new IOException(e);
      }
    }

    private Date getRefLastUpdated(Repository repo, Change.Id id)
        throws IOException {
      Ref ref = repo.exactRef(ChangeNoteUtil.changeRefName(id));
      if (ref == null) {
        return new Date(0);
      }
      try (RevWalk rw = new RevWalk(repo)) {
        return rw.parseCommit(ref.getObjectId()).getCommitterIdent().getWhen();
      }
    }
  }

  private static ReviewDb unwrapDb(Provider<ReviewDb> dbProvider) {
    ReviewDb db = dbProvider.get();
    if (db instanceof DisabledChangesReviewDbWrapper) {
      db = ((DisabledChangesReviewDbWrapper) db).unsafeGetDelegate();
    }
    return db;
  }
}
