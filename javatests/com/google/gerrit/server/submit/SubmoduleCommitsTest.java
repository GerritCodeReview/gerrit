// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmoduleSubscription;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;
import org.mockito.Mock;

public class SubmoduleCommitsTest {

  private static final String MASTER = "refs/heads/master";
  private static final Project.NameKey superProject = Project.nameKey("superproject");
  private static final Project.NameKey subProject = Project.nameKey("subproject");

  private static final PersonIdent ident = new PersonIdent("submodule-test", "a@b.com");

  private InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
  private MergeOpRepoManager mergeOpRepoManager;

  @Mock ProjectCache mockProjectCache = mock(ProjectCache.class);
  @Mock ProjectState mockProjectState = mock(ProjectState.class);

  @Test
  public void createGitlinksCommit_subprojectMoved() throws Exception {
    createRepo(subProject, MASTER);
    createRepo(superProject, MASTER);

    when(mockProjectCache.get(any())).thenReturn(Optional.of(mockProjectState));
    mergeOpRepoManager = new MergeOpRepoManager(repoManager, mockProjectCache, null, null);

    ObjectId subprojectCommit = getTip(subProject, MASTER);
    RevCommit superprojectTip =
        directUpdateSubmodule(superProject, MASTER, Project.nameKey("dir-x"), subprojectCommit);
    assertThat(readGitLink(superProject, superprojectTip, "dir-x")).isEqualTo(subprojectCommit);

    RevCommit newSubprojectCommit = addCommit(subProject, MASTER);

    BranchNameKey superBranch = BranchNameKey.create(superProject, MASTER);
    BranchNameKey subBranch = BranchNameKey.create(subProject, MASTER);
    SubmoduleSubscription ss = new SubmoduleSubscription(superBranch, subBranch, "dir-x");
    SubmoduleCommits helper =
        new SubmoduleCommits(mergeOpRepoManager, ident, new Config(), new BranchTips());
    Optional<CodeReviewCommit> newGitLinksCommit =
        helper.composeGitlinksCommit(
            BranchNameKey.create(superProject, MASTER), ImmutableList.of(ss));

    assertThat(newGitLinksCommit).isPresent();
    assertThat(newGitLinksCommit.get().getParent(0)).isEqualTo(superprojectTip);
    assertThat(readGitLink(superProject, newGitLinksCommit.get(), "dir-x"))
        .isEqualTo(newSubprojectCommit);
  }

  @Test
  public void amendGitlinksCommit_subprojectMoved() throws Exception {
    createRepo(subProject, MASTER);
    createRepo(superProject, MASTER);

    when(mockProjectCache.get(any())).thenReturn(Optional.of(mockProjectState));
    mergeOpRepoManager = new MergeOpRepoManager(repoManager, mockProjectCache, null, null);

    ObjectId subprojectCommit = getTip(subProject, MASTER);
    CodeReviewCommit superprojectTip =
        directUpdateSubmodule(superProject, MASTER, Project.nameKey("dir-x"), subprojectCommit);
    assertThat(readGitLink(superProject, superprojectTip, "dir-x")).isEqualTo(subprojectCommit);

    RevCommit newSubprojectCommit = addCommit(subProject, MASTER);

    BranchNameKey superBranch = BranchNameKey.create(superProject, MASTER);
    BranchNameKey subBranch = BranchNameKey.create(subProject, MASTER);
    SubmoduleSubscription ss = new SubmoduleSubscription(superBranch, subBranch, "dir-x");
    SubmoduleCommits helper =
        new SubmoduleCommits(mergeOpRepoManager, ident, new Config(), new BranchTips());
    CodeReviewCommit amendedCommit =
        helper.amendGitlinksCommit(
            BranchNameKey.create(superProject, MASTER), superprojectTip, ImmutableList.of(ss));

    assertThat(amendedCommit.getParent(0)).isEqualTo(superprojectTip.getParent(0));
    assertThat(readGitLink(superProject, amendedCommit, "dir-x")).isEqualTo(newSubprojectCommit);
  }

  /** Create repo with a commit on refName */
  private void createRepo(Project.NameKey projectKey, String refName) throws Exception {
    Repository repo = repoManager.createRepository(projectKey);
    try (TestRepository<Repository> git = new TestRepository<>(repo)) {
      RevCommit newCommit = git.commit().message("Initial commit for " + projectKey).create();
      git.update(refName, newCommit);
    }
  }

  private ObjectId getTip(Project.NameKey projectKey, String refName)
      throws RepositoryNotFoundException, IOException {
    return repoManager.openRepository(projectKey).exactRef(refName).getObjectId();
  }

  private RevCommit addCommit(Project.NameKey projectKey, String refName) throws Exception {
    try (Repository serverRepo = repoManager.openRepository(projectKey);
        RevWalk rw = new RevWalk(serverRepo);
        TestRepository<Repository> git = new TestRepository<>(serverRepo, rw)) {
      Ref ref = serverRepo.exactRef(refName);
      assertWithMessage(refName).that(ref).isNotNull();

      RevCommit originalTip = rw.parseCommit(ref.getObjectId());
      RevCommit newTip =
          git.commit().parent(originalTip).message("Added commit to " + projectKey).create();
      git.update(refName, newTip);
      return newTip;
    }
  }

  private CodeReviewCommit directUpdateSubmodule(
      Project.NameKey project, String refName, Project.NameKey path, AnyObjectId id)
      throws Exception {
    OpenRepo or = mergeOpRepoManager.getRepo(project);
    Repository serverRepo = or.repo;
    ObjectInserter ins = or.ins;
    CodeReviewRevWalk rw = or.rw;
    Ref ref = serverRepo.exactRef(refName);
    assertWithMessage(refName).that(ref).isNotNull();
    ObjectId oldCommitId = ref.getObjectId();

    DirCache dc = DirCache.newInCore();
    DirCacheBuilder b = dc.builder();
    b.addTree(new byte[0], DirCacheEntry.STAGE_0, rw.getObjectReader(), rw.parseTree(oldCommitId));
    b.finish();
    DirCacheEditor e = dc.editor();
    e.add(
        new PathEdit(path.get()) {
          @Override
          public void apply(DirCacheEntry ent) {
            ent.setFileMode(FileMode.GITLINK);
            ent.setObjectId(id);
          }
        });
    e.finish();

    CommitBuilder cb = new CommitBuilder();
    cb.addParentId(oldCommitId);
    cb.setTreeId(dc.writeTree(ins));

    cb.setAuthor(ident);
    cb.setCommitter(ident);
    cb.setMessage("Direct update submodule " + path);
    ObjectId newCommitId = ins.insert(cb);
    ins.flush();

    RefUpdate ru = serverRepo.updateRef(refName);
    ru.setExpectedOldObjectId(oldCommitId);
    ru.setNewObjectId(newCommitId);
    assertThat(ru.update()).isEqualTo(RefUpdate.Result.FAST_FORWARD);
    return rw.parseCommit(newCommitId);
  }

  private ObjectId readGitLink(Project.NameKey projectKey, RevCommit commit, String path)
      throws IOException, NoSuchProjectException {
    // SubmoduleCommitHelper used mergeOpRepoManager to create the commit
    // Read the repo from mergeOpRepoManager to get also the RevWalk that created the commit
    return readGitLinkInCommit(mergeOpRepoManager.getRepo(projectKey).rw, commit, path);
  }

  private ObjectId readGitLinkInCommit(RevWalk rw, RevCommit commit, String path)
      throws IOException {
    DirCache dc = DirCache.newInCore();
    DirCacheBuilder b = dc.builder();
    b.addTree(
        new byte[0], // no prefix path
        DirCacheEntry.STAGE_0, // standard stage
        rw.getObjectReader(),
        commit.getTree());
    b.finish();
    DirCacheEntry entry = dc.getEntry(path);
    assertThat(entry.getFileMode()).isEqualTo(FileMode.GITLINK);
    return entry.getObjectId();
  }
}
