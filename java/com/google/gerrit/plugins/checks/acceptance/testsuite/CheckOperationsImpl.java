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

package com.google.gerrit.plugins.checks.acceptance.testsuite;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public final class CheckOperationsImpl implements CheckOperations {
  private final Checks checks;
  private final ChecksUpdate checksUpdate;
  private final GitRepositoryManager repoManager;

  @Inject
  public CheckOperationsImpl(
      Checks checks, GitRepositoryManager repoManager, @ServerInitiated ChecksUpdate checksUpdate) {
    this.checks = checks;
    this.repoManager = repoManager;
    this.checksUpdate = checksUpdate;
  }

  @Override
  public PerCheckOperations check(TestCheckKey key) {
    return new PerCheckOperationsImpl(key);
  }

  @Override
  public TestCheckUpdate.Builder newChecker(TestCheckKey key) {
    return TestCheckUpdate.builder(key)
        .setCheckUpdater(u -> checksUpdate.createCheck(toCheckKey(key), toCheckUpdate(u)));
  }

  final class PerCheckOperationsImpl implements PerCheckOperations {
    private final TestCheckKey key;

    PerCheckOperationsImpl(TestCheckKey key) {
      this.key = key;
    }

    @Override
    public boolean exists() throws Exception {
      return checks.getCheck(toCheckKey(key)).isPresent();
    }

    @Override
    public TestCheck get() throws Exception {
      return toTestCheck(checks.getCheck(toCheckKey(key)).get());
    }

    @Override
    public RevCommit commit() throws Exception {
      throw new UnsupportedOperationException("bla bla");
    }

    @Override
    public Map<RevId, String> notesAsText() throws Exception {
      try (Repository repo = repoManager.openRepository(key.project());
          RevWalk rw = new RevWalk(repo)) {
        Ref checkRef =
            repo.getRefDatabase().exactRef(CheckerRef.checksRef(key.patchSet().changeId));
        checkNotNull(checkRef);

        ObjectReader reader = repo.newObjectReader();

        NoteMap notes = NoteMap.read(reader, rw.parseCommit(checkRef.getObjectId()));
        Map<RevId, String> raw = Maps.newHashMap();

        Iterator<Note> n = notes.iterator();
        while (n.hasNext()) {
          Note note = n.next();
          raw.put(
              new RevId(note.name()),
              new String(notes.getCachedBytes(note.toObjectId(), Integer.MAX_VALUE)));
        }
        return ImmutableMap.copyOf(raw);
      }
    }

    @Override
    public CheckInfo asInfo() {
      throw new UnsupportedOperationException("todo");
    }

    @Override
    public TestCheckUpdate.Builder forUpdate() {
      throw new UnsupportedOperationException("todo");
    }
  }

  private static CheckKey toCheckKey(TestCheckKey key) {
    return CheckKey.create(key.project(), key.patchSet(), key.checkerUUID());
  }

  private static CheckUpdate toCheckUpdate(TestCheckUpdate testUpdate) {
    CheckUpdate.Builder update = CheckUpdate.builder();
    testUpdate.state().ifPresent(update::setState);
    testUpdate.started().ifPresent(update::setStarted);
    testUpdate.finished().ifPresent(update::setFinished);
    testUpdate.url().ifPresent(update::setUrl);
    return update.build();
  }

  private static TestCheck toTestCheck(Check check) {
    TestCheck.Builder testCheck =
        TestCheck.builder(check.key())
            .setCreated(check.created())
            .setUpdated(check.updated())
            .setState(check.state());
    check.started().ifPresent(testCheck::setStarted);
    check.finished().ifPresent(testCheck::setFinished);
    return testCheck.build();
  }
}
