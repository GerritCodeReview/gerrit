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
import static org.junit.Assert.assertNotNull;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class InlineEditIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  private TestAccount admin;

  private Git git;
  private ReviewDb db;
  private IdentifiedUser u;

  private final static String FILE_NAME = "foo";
  private final static String CONTENT_OLD = "bar";
  private final static String CONTENT_NEW = "baz";

  @Before
  public void setUp() throws Exception {
    admin = accounts.admin();
    initSsh(admin);
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    db = reviewDbProvider.open();
    u = identifiedUserFactory.create(Providers.of(db), admin.getId());
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void writeInlineEdit() throws GitAPIException,
      IOException, OrmException {
    String changeId = newChange();
    Change change = getChange(changeId);
    PatchSet ps = getCurrentPatchSet(changeId);
    assertNotNull(ps);

    RevisionEdit edit = new RevisionEdit(u, ps.getId());

    final Repository repo;
    try {
      repo = repoManager.openRepository(change.getProject());
    } catch (RepositoryNotFoundException noGitRepository) {
      throw new IllegalStateException(noGitRepository);
    }
    try {
      RevWalk rw = new RevWalk(repo);
      ObjectInserter ins = repo.newObjectInserter();
      try {
        RevCommit prevEdit = edit.get(repo, rw);
        RevCommit base = rw.parseCommit(ObjectId.fromString(
            ps.getRevision().get()));
        if (prevEdit == null) {
          prevEdit = base;
        }

        ObjectId tree = editTree(FILE_NAME, repo, ins, CONTENT_NEW, prevEdit);
        if (ObjectId.equals(tree, prevEdit.getTree())) {
          throw new IllegalStateException("tree are equals");
        }

        CommitBuilder builder = new CommitBuilder();
        builder.setTreeId(tree);
        builder.setParentIds(base.getParents());
        builder.setAuthor(prevEdit.getAuthorIdent());
        builder.setCommitter(getCommitterIdent());
        builder.setMessage(prevEdit.getFullMessage());
        ObjectId newEdit = ins.insert(builder);

        RefUpdate ru = repo.updateRef(edit.toRefName());
        ru.setExpectedOldObjectId(
            prevEdit == base ? ObjectId.zeroId() : prevEdit);
        ru.setNewObjectId(newEdit);
        ru.setRefLogIdent(getRefLogIdent());
        ru.setForceUpdate(true);
        ru.update(rw);
      } finally {
        rw.release();
        ins.release();
      }
    } finally {
      repo.close();
    }
  }

  private ObjectId editTree(String fileName, Repository repo,
      ObjectInserter ins, String content, RevCommit prevEdit)
      throws IOException {
    // TODO(dborowitz): Handle non-UTF-8 paths.
    byte[] path = fileName.getBytes(Charsets.UTF_8);
    DirCache dc = DirCache.newInCore();
    DirCacheBuilder dcb = dc.builder();
    TreeWalk tw = new TreeWalk(repo);
    tw.reset();
    tw.setRecursive(true);
    tw.addTree(prevEdit.getTree());
    // TODO(dborowitz): This is awful. Surely we can do this without
    // iterating the entire tree. Take a closer look at ResolveMerger to
    // figure this out.
    boolean found = false;
    while (tw.next()) {
      DirCacheEntry dce = new DirCacheEntry(tw.getRawPath());
      dce.setFileMode(tw.getFileMode(0));
      if (tw.isPathPrefix(path, path.length) == 0
          && tw.getPathLength() == path.length) {
        if (tw.isSubtree()) {
          throw new IllegalArgumentException("invalid PUT on directory");
        }
        dce.setObjectId(insert(ins, content));
        found = true;
      } else {
        dce.setObjectId(tw.getObjectId(0));
      }
      dcb.add(dce);
    }
    if (!found) {
      // TODO(dborowitz): Use path compare above to insert in order.
      DirCacheEntry dce = new DirCacheEntry(path);
      dce.setFileMode(FileMode.REGULAR_FILE);
      dce.setObjectId(insert(ins, content));
      dcb.add(dce);
    }
    dcb.finish();
    return dc.writeTree(ins);
  }

  private ObjectId insert(ObjectInserter ins, String content) throws IOException {
    return ins.insert(Constants.OBJ_BLOB, Constants.encode(content));
  }

  private PersonIdent getCommitterIdent() {
    return admin.getIdent();
  }

  private PersonIdent getRefLogIdent() {
    return admin.getIdent();
  }

  private String newChange() throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db,
        admin.getIdent(), PushOneCommit.SUBJECT,
        FILE_NAME, CONTENT_OLD);
    return push.to(git, "refs/for/master").getChangeId();
  }

  private Change getChange(String changeId) throws OrmException {
    return Iterables.getOnlyElement(db.changes()
        .byKey(new Change.Key(changeId)));
  }

  private PatchSet getCurrentPatchSet(String changeId) throws OrmException {
    return db.patchSets()
        .get(getChange(changeId).currentPatchSetId());
  }
}
