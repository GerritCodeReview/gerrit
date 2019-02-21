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

package com.google.gerrit.plugins.checks.acceptance;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

@SkipProjectClone
public class CheckerRefsIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private Sequences seq;
  @Inject private ChangeInserter.Factory changeInserterFactory;
  @Inject private BatchUpdate.Factory updateFactory;

  @Test
  public void cannotCreateCheckerRef() throws Exception {
    grant(allProjects, CheckerRef.REFS_CHECKERS + "*", Permission.CREATE);
    grant(allProjects, CheckerRef.REFS_CHECKERS + "*", Permission.PUSH);

    String checkerRef = CheckerRef.refsCheckers(CheckerUuid.make("my-checker"));

    TestRepository<InMemoryRepository> testRepo = cloneProject(allProjects);
    PushOneCommit.Result r = pushFactory.create(admin.getIdent(), testRepo).to(checkerRef);
    r.assertErrorStatus();
    assertThat(r.getMessage()).contains("Not allowed to create checker ref.");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      assertThat(repo.exactRef(checkerRef)).isNull();
    }
  }

  @Test
  public void cannotDeleteCheckerRef() throws Exception {
    grant(allProjects, CheckerRef.REFS_CHECKERS + "*", Permission.DELETE, true, REGISTERED_USERS);

    String checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = CheckerRef.refsCheckers(checkerUuid);

    TestRepository<InMemoryRepository> testRepo = cloneProject(allProjects);
    PushResult r = deleteRef(testRepo, checkerRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(checkerRef);
    assertThat(refUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(refUpdate.getMessage()).contains("Not allowed to delete checker ref.");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      assertThat(repo.exactRef(checkerRef)).isNotNull();
    }
  }

  @Test
  public void updateCheckerRefsByPushIsDisabled() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = CheckerRef.refsCheckers(checkerUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, checkerRef + ":checkerRef");
    repo.reset("checkerRef");

    grant(allProjects, CheckerRef.REFS_CHECKERS + "*", Permission.PUSH);
    PushOneCommit.Result r = pushFactory.create(admin.getIdent(), repo).to(checkerRef);
    r.assertErrorStatus();
    r.assertMessage("direct update of checker ref not allowed");
  }

  @Test
  public void submitToCheckerRefsIsDisabled() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = CheckerRef.refsCheckers(checkerUuid);

    String changeId = createChangeWithoutCommitValidation(checkerRef);

    grantLabel(
        "Code-Review",
        -2,
        2,
        allProjects,
        CheckerRef.REFS_CHECKERS + "*",
        false,
        adminGroupUuid(),
        false);
    approve(changeId);

    grant(allProjects, CheckerRef.REFS_CHECKERS + "*", Permission.SUBMIT);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("submit to checker ref not allowed");
    gApi.changes().id(changeId).current().submit();
  }

  @Test
  public void createChangeForCheckerRefsByPushIsDisabled() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = CheckerRef.refsCheckers(checkerUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, checkerRef + ":checkerRef");
    repo.reset("checkerRef");

    grant(allProjects, CheckerRef.REFS_CHECKERS + "*", Permission.PUSH);
    PushOneCommit.Result r =
        pushFactory.create(admin.getIdent(), repo).to("refs/for/" + checkerRef);
    r.assertErrorStatus();
    r.assertMessage("creating change for checker ref not allowed");
  }

  @Test
  public void createChangeForCheckerRefsViaApiIsDisabled() throws Exception {
    String checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = CheckerRef.refsCheckers(checkerUuid);

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, checkerRef + ":checkerRef");
    repo.reset("checkerRef");
    RevCommit head = getHead(repo.getRepository(), "HEAD");

    ChangeInput input = new ChangeInput();
    input.project = allProjects.get();
    input.branch = checkerRef;
    input.baseCommit = head.name();
    input.subject = "A change.";

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("creating change for checker ref not allowed");
    gApi.changes().create(input);
  }

  private String createChangeWithoutCommitValidation(String targetRef) throws Exception {
    try (Repository git = repoManager.openRepository(allProjects);
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader)) {
      RevCommit head = rw.parseCommit(git.exactRef(targetRef).getObjectId());
      RevCommit commit =
          new TestRepository<>(git)
              .commit()
              .author(admin.getIdent())
              .message("A change.")
              .insertChangeId()
              .parent(head)
              .create();

      Change.Id changeId = new Change.Id(seq.nextChangeId());
      ChangeInserter ins = changeInserterFactory.create(changeId, commit, targetRef);
      ins.setValidate(false);
      ins.setMessage(String.format("Uploaded patch set %s.", ins.getPatchSetId().get()));
      try (BatchUpdate bu =
          updateFactory.create(
              allProjects, identifiedUserFactory.create(admin.id), TimeUtil.nowTs())) {
        bu.setRepository(git, rw, oi);
        bu.insertChange(ins);
        bu.execute();
      }
      return changeId.toString();
    }
  }
}
