// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.git.CommitMergeStatus;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class SubmitOnPushIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GroupCache groupCache;

  @Inject
  private @GerritPersonIdent PersonIdent serverIdent;

  private TestAccount admin;
  private Project.NameKey project;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin =
        accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");

    project = new Project.NameKey("p");
    initSsh(admin);
    SshSession sshSession = new SshSession(admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();

    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void submitOnPush() throws GitAPIException, OrmException,
      IOException, ConfigInvalidException {
    grantSubmit(project, "refs/for/master");
    PushOneCommit.Result r = pushTo("refs/for/master%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/heads/master");
  }

  @Test
  public void submitOnPushToRefsMetaConfig() throws GitAPIException,
      OrmException, IOException, ConfigInvalidException {
    grantSubmit(project, "refs/for/refs/meta/config");

    git.fetch().setRefSpecs(new RefSpec("refs/meta/config:refs/meta/config")).call();
    ObjectId objectId = git.getRepository().getRef("refs/meta/config").getObjectId();
    git.checkout().setName(objectId.getName()).call();

    PushOneCommit.Result r = pushTo("refs/for/refs/meta/config%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/meta/config");
  }

  @Test
  public void submitOnPushMergeConflict() throws GitAPIException, OrmException,
      IOException, ConfigInvalidException {
    String master = "refs/heads/master";
    ObjectId objectId = git.getRepository().getRef(master).getObjectId();
    push(master, "one change", "a.txt", "some content");
    git.checkout().setName(objectId.getName()).call();

    grantSubmit(project, "refs/for/master");
    PushOneCommit.Result r =
        push("refs/for/master%submit", "other change", "a.txt", "other content");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null, admin);
    r.assertMessage(CommitMergeStatus.PATH_CONFLICT.getMessage());
  }

  @Test
  public void submitOnPushSuccessfulMerge() throws GitAPIException, OrmException,
      IOException, ConfigInvalidException {
    String master = "refs/heads/master";
    ObjectId objectId = git.getRepository().getRef(master).getObjectId();
    push(master, "one change", "a.txt", "some content");
    git.checkout().setName(objectId.getName()).call();

    grantSubmit(project, "refs/for/master");
    PushOneCommit.Result r =
        push("refs/for/master%submit", "other change", "b.txt", "other content");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertMergeCommit(master, "other change");
  }

  @Test
  public void submitOnPushNewPatchSet() throws GitAPIException,
      OrmException, IOException, ConfigInvalidException {
    PushOneCommit.Result r =
        push("refs/for/master", PushOneCommit.SUBJECT, "a.txt", "some content");

    grantSubmit(project, "refs/for/master");
    r = push("refs/for/master%submit", PushOneCommit.SUBJECT, "a.txt",
        "other content", r.getChangeId());
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    Change c = Iterables.getOnlyElement(db.changes().byKey(
        new Change.Key(r.getChangeId())).toList());
    assertEquals(2, db.patchSets().byChange(c.getId()).toList().size());
    assertSubmitApproval(r.getPatchSetId());
    assertCommit(project, "refs/heads/master");
  }

  @Test
  public void submitOnPushNotAllowed_Error() throws GitAPIException,
      OrmException, IOException {
    PushOneCommit.Result r = pushTo("refs/for/master%submit");
    r.assertErrorStatus("submit not allowed");
  }

  @Test
  public void submitOnPushNewPatchSetNotAllowed_Error() throws GitAPIException,
      OrmException, IOException, ConfigInvalidException {
    PushOneCommit.Result r =
        push("refs/for/master", PushOneCommit.SUBJECT, "a.txt", "some content");

    r = push("refs/for/master%submit", PushOneCommit.SUBJECT, "a.txt",
        "other content", r.getChangeId());
    r.assertErrorStatus("submit not allowed");
  }

  @Test
  public void submitOnPushingDraft_Error() throws GitAPIException,
      OrmException, IOException {
    PushOneCommit.Result r = pushTo("refs/for/master%draft,submit");
    r.assertErrorStatus("cannot submit draft");
  }

  @Test
  public void submitOnPushToNonExistingBranch_Error() throws GitAPIException,
      OrmException, IOException {
    String branchName = "non-existing";
    PushOneCommit.Result r = pushTo("refs/for/" + branchName + "%submit");
    r.assertErrorStatus("branch " + branchName + " not found");
  }

  private void grantSubmit(Project.NameKey project, String ref)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    md.setMessage("Grant submit on " + ref);
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection(ref, true);
    Permission p = s.getPermission(Permission.SUBMIT, true);
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Administrators"));
    p.add(new PermissionRule(config.resolve(adminGroup)));
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  private void assertSubmitApproval(PatchSet.Id patchSetId) throws OrmException {
    List<PatchSetApproval> approvals = db.patchSetApprovals().byPatchSet(patchSetId).toList();
    assertEquals(1, approvals.size());
    PatchSetApproval a = approvals.get(0);
    assertEquals(PatchSetApproval.LabelId.SUBMIT.get(), a.getLabel());
    assertEquals(1, a.getValue());
    assertEquals(admin.id, a.getAccountId());
  }

  private void assertCommit(Project.NameKey project, String branch) throws IOException {
    Repository r = repoManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(r);
      try {
        RevCommit c = rw.parseCommit(r.getRef(branch).getObjectId());
        assertEquals(PushOneCommit.SUBJECT, c.getShortMessage());
        assertEquals(admin.email, c.getAuthorIdent().getEmailAddress());
        assertEquals(admin.email, c.getCommitterIdent().getEmailAddress());
      } finally {
        rw.release();
      }
    } finally {
      r.close();
    }
  }

  private void assertMergeCommit(String branch, String subject) throws IOException {
    Repository r = repoManager.openRepository(project);
    try {
      RevWalk rw = new RevWalk(r);
      try {
        RevCommit c = rw.parseCommit(r.getRef(branch).getObjectId());
        assertEquals(2, c.getParentCount());
        assertEquals("Merge \"" + subject + "\"", c.getShortMessage());
        assertEquals(admin.email, c.getAuthorIdent().getEmailAddress());
        assertEquals(serverIdent.getEmailAddress(), c.getCommitterIdent().getEmailAddress());
      } finally {
        rw.release();
      }
    } finally {
      r.close();
    }
  }

  private PushOneCommit.Result pushTo(String ref) throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, ref);
  }

  private PushOneCommit.Result push(String ref, String subject,
      String fileName, String content) throws GitAPIException, IOException {
    PushOneCommit push =
        new PushOneCommit(db, admin.getIdent(), subject, fileName, content);
    return push.to(git, ref);
  }

  private PushOneCommit.Result push(String ref, String subject,
      String fileName, String content, String changeId) throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent(), subject,
        fileName, content, changeId);
    return push.to(git, ref);
  }
}
