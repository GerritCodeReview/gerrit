// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubscribeSection;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
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
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Before;

public abstract class AbstractSubmoduleSubscription extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;

  protected TestRepository<?> superRepo;
  protected Project.NameKey superKey;
  protected TestRepository<?> subRepo;
  protected Project.NameKey subKey;

  protected SubmitType getSubmitType() {
    return cfg.getEnum("project", null, "submitType", SubmitType.MERGE_IF_NECESSARY);
  }

  protected static Config submitByMergeAlways() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    cfg.setEnum("project", null, "submitType", SubmitType.MERGE_ALWAYS);
    return cfg;
  }

  protected static Config submitByMergeIfNecessary() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    cfg.setEnum("project", null, "submitType", SubmitType.MERGE_IF_NECESSARY);
    return cfg;
  }

  protected static Config submitByCherryPickConfig() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    cfg.setEnum("project", null, "submitType", SubmitType.CHERRY_PICK);
    return cfg;
  }

  protected static Config submitByRebaseAlwaysConfig() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    cfg.setEnum("project", null, "submitType", SubmitType.REBASE_ALWAYS);
    return cfg;
  }

  protected static Config submitByRebaseIfNecessaryConfig() {
    Config cfg = new Config();
    cfg.setBoolean("change", null, "submitWholeTopic", true);
    cfg.setEnum("project", null, "submitType", SubmitType.REBASE_IF_NECESSARY);
    return cfg;
  }

  protected void grantPush(Project.NameKey project) throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/*").group(adminGroupUuid()))
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/*").group(adminGroupUuid()))
        .update();
  }

  protected Project.NameKey createProjectForPush(SubmitType submitType) throws Exception {
    Project.NameKey project = projectOperations.newProject().submitType(submitType).create();
    grantPush(project);
    return project;
  }

  private static AtomicInteger contentCounter = new AtomicInteger(0);

  @Before
  public void setUp() throws Exception {
    superKey = createProjectForPush(getSubmitType());
    subKey = createProjectForPush(getSubmitType());
    superRepo = cloneProject(superKey);
    subRepo = cloneProject(subKey);
  }

  protected ObjectId pushChangeTo(
      TestRepository<?> repo, String ref, String file, String content, String message, String topic)
      throws Exception {
    ObjectId ret =
        repo.branch("HEAD").commit().insertChangeId().message(message).add(file, content).create();

    String pushedRef = ref;
    if (!topic.isEmpty()) {
      pushedRef += "%topic=" + name(topic);
    }
    String refspec = "HEAD:" + pushedRef;

    Iterable<PushResult> res =
        repo.git().push().setRemote("origin").setRefSpecs(new RefSpec(refspec)).call();

    RemoteRefUpdate u = Iterables.getOnlyElement(res).getRemoteUpdate(pushedRef);
    assertThat(u).isNotNull();
    assertThat(u.getStatus()).isEqualTo(Status.OK);
    assertThat(u.getNewObjectId()).isEqualTo(ret);

    return ret;
  }

  protected ObjectId pushChangeTo(TestRepository<?> repo, String ref, String message, String topic)
      throws Exception {
    return pushChangeTo(
        repo, ref, "a.txt", "a contents: " + contentCounter.incrementAndGet(), message, topic);
  }

  protected ObjectId pushChangeTo(TestRepository<?> repo, String branch) throws Exception {
    return pushChangeTo(repo, "refs/heads/" + branch, "some change", "");
  }

  protected ObjectId pushChangesTo(TestRepository<?> repo, String branch, int numChanges)
      throws Exception {
    for (int i = 0; i < numChanges; i++) {
      repo.branch("HEAD")
          .commit()
          .insertChangeId()
          .message("Message " + i)
          .add(name("file"), "content" + i)
          .create();
    }
    String remoteBranch = "refs/heads/" + branch;
    Iterable<PushResult> res =
        repo.git()
            .push()
            .setRemote("origin")
            .setRefSpecs(new RefSpec("HEAD:" + remoteBranch))
            .call();
    List<Status> status =
        StreamSupport.stream(res.spliterator(), false)
            .map(r -> r.getRemoteUpdate(remoteBranch).getStatus())
            .collect(toList());
    assertThat(status).containsExactly(Status.OK);
    return Iterables.getLast(res).getRemoteUpdate(remoteBranch).getNewObjectId();
  }

  protected void allowMatchingSubmoduleSubscription(
      Project.NameKey submodule, String subBranch, Project.NameKey superproject, String superBranch)
      throws Exception {
    allowSubmoduleSubscription(submodule, subBranch, superproject, superBranch, true);
  }

  protected void allowSubmoduleSubscription(
      Project.NameKey submodule,
      String subBranch,
      Project.NameKey superproject,
      String superBranch,
      boolean match)
      throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(submodule)) {
      md.setMessage("Added superproject subscription");
      SubscribeSection.Builder s;
      ProjectConfig pc = projectConfigFactory.read(md);
      if (pc.getSubscribeSections().containsKey(superproject)) {
        s = pc.getSubscribeSections().get(superproject).toBuilder();
      } else {
        s = SubscribeSection.builder(superproject);
      }
      String refspec;
      if (superBranch == null) {
        refspec = subBranch;
      } else {
        refspec = subBranch + ":" + superBranch;
      }
      if (match) {
        s.addMatchingRefSpec(refspec);
      } else {
        s.addMultiMatchRefSpec(refspec);
      }
      pc.addSubscribeSection(s.build());
      ObjectId oldId = pc.getRevision();
      ObjectId newId = pc.commit(md);
      assertThat(newId).isNotEqualTo(oldId);
      projectCache.evict(pc.getProject());
    }
  }

  protected void createSubmoduleSubscription(
      TestRepository<?> repo,
      String branch,
      Project.NameKey subscribeToRepo,
      String subscribeToBranch)
      throws Exception {
    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subscribeToRepo, subscribeToBranch);
    pushSubmoduleConfig(repo, branch, config);
  }

  protected void createRelativeSubmoduleSubscription(
      TestRepository<?> repo,
      String branch,
      String subscribeToRepoPrefix,
      Project.NameKey subscribeToRepo,
      String subscribeToBranch)
      throws Exception {
    Config config = new Config();
    prepareRelativeSubmoduleConfigEntry(
        config, subscribeToRepoPrefix, subscribeToRepo, subscribeToBranch);
    pushSubmoduleConfig(repo, branch, config);
  }

  protected void prepareRelativeSubmoduleConfigEntry(
      Config config,
      String subscribeToRepoPrefix,
      Project.NameKey subscribeToRepo,
      String subscribeToBranch) {
    String url = subscribeToRepoPrefix + subscribeToRepo.get();
    config.setString("submodule", subscribeToRepo.get(), "path", subscribeToRepo.get());
    config.setString("submodule", subscribeToRepo.get(), "url", url);
    if (subscribeToBranch != null) {
      config.setString("submodule", subscribeToRepo.get(), "branch", subscribeToBranch);
    }
  }

  protected void prepareSubmoduleConfigEntry(
      Config config, Project.NameKey subscribeToRepo, String subscribeToBranch) {
    // The submodule subscription module checks for gerrit.canonicalWebUrl to
    // detect if it's configured for automatic updates. It doesn't matter if
    // it serves from that URL.
    prepareSubmoduleConfigEntry(config, subscribeToRepo, subscribeToRepo, subscribeToBranch);
  }

  protected void prepareSubmoduleConfigEntry(
      Config config,
      Project.NameKey subscribeToRepo,
      Project.NameKey subscribeToRepoPath,
      String subscribeToBranch) {
    // The submodule subscription module checks for gerrit.canonicalWebUrl to
    // detect if it's configured for automatic updates. It doesn't matter if
    // it serves from that URL.
    String url = cfg.getString("gerrit", null, "canonicalWebUrl") + "/" + subscribeToRepo;
    config.setString("submodule", subscribeToRepoPath.get(), "path", subscribeToRepoPath.get());
    config.setString("submodule", subscribeToRepoPath.get(), "url", url);
    if (subscribeToBranch != null) {
      config.setString("submodule", subscribeToRepoPath.get(), "branch", subscribeToBranch);
    }
  }

  protected void pushSubmoduleConfig(TestRepository<?> repo, String branch, Config config)
      throws Exception {

    repo.branch("HEAD")
        .commit()
        .insertChangeId()
        .message("subject: adding new subscription")
        .add(".gitmodules", config.toText())
        .create();

    repo.git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/" + branch))
        .call();
  }

  protected void expectToHaveSubmoduleState(
      TestRepository<?> repo,
      String branch,
      Project.NameKey submodule,
      TestRepository<?> subRepo,
      String subBranch)
      throws Exception {

    ObjectId commitId =
        repo.git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/" + branch)
            .getObjectId();

    ObjectId subHead =
        subRepo
            .git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/" + subBranch)
            .getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    rw.parseBody(c.getTree());

    RevTree tree = c.getTree();
    RevObject actualId = repo.get(tree, submodule.get());

    assertThat(actualId).isEqualTo(subHead);
  }

  protected void expectToHaveSubmoduleState(
      TestRepository<?> repo, String branch, Project.NameKey submodule, ObjectId expectedId)
      throws Exception {
    ObjectId commitId =
        repo.git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/" + branch)
            .getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    rw.parseBody(c.getTree());

    RevTree tree = c.getTree();
    RevObject actualId = repo.get(tree, submodule.get());

    assertThat(actualId).isEqualTo(expectedId);
  }

  protected void deleteAllSubscriptions(TestRepository<?> repo, String branch) throws Exception {
    repo.git().fetch().setRemote("origin").call();
    repo.reset("refs/remotes/origin/" + branch);

    ObjectId expectedId =
        repo.branch("HEAD")
            .commit()
            .insertChangeId()
            .message("delete contents in .gitmodules")
            .add(".gitmodules", "") // Just remove the contents of the file!
            .create();
    repo.git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/" + branch))
        .call();

    ObjectId actualId =
        repo.git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/master")
            .getObjectId();
    assertThat(actualId).isEqualTo(expectedId);
  }

  protected void deleteGitModulesFile(TestRepository<?> repo, String branch) throws Exception {
    repo.git().fetch().setRemote("origin").call();
    repo.reset("refs/remotes/origin/" + branch);

    ObjectId expectedId =
        repo.branch("HEAD")
            .commit()
            .insertChangeId()
            .message("delete .gitmodules")
            .rm(".gitmodules")
            .create();
    repo.git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/" + branch))
        .call();

    ObjectId actualId =
        repo.git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/master")
            .getObjectId();
    assertThat(actualId).isEqualTo(expectedId);
  }

  protected boolean hasSubmodule(TestRepository<?> repo, String branch, Project.NameKey submodule)
      throws Exception {
    Ref branchTip =
        repo.git().fetch().setRemote("origin").call().getAdvertisedRef("refs/heads/" + branch);
    if (branchTip == null) {
      return false;
    }

    ObjectId commitId = branchTip.getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    rw.parseBody(c.getTree());

    RevTree tree = c.getTree();
    try {
      repo.get(tree, submodule.get());
      return true;
    } catch (AssertionError e) {
      return false;
    }
  }

  protected void expectToHaveCommitMessage(
      TestRepository<?> repo, String branch, String expectedMessage) throws Exception {

    ObjectId commitId =
        repo.git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/" + branch)
            .getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    String msg = c.getFullMessage();
    assertThat(msg).isEqualTo(expectedMessage);
  }

  protected PersonIdent getAuthor(TestRepository<?> repo, String branch) throws Exception {
    ObjectId commitId =
        repo.git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/" + branch)
            .getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    return c.getAuthorIdent();
  }

  protected void directUpdateSubmodule(
      Project.NameKey project, String refName, Project.NameKey path, AnyObjectId id)
      throws Exception {
    try (Repository serverRepo = repoManager.openRepository(project);
        ObjectInserter ins = serverRepo.newObjectInserter();
        RevWalk rw = new RevWalk(serverRepo)) {
      Ref ref = serverRepo.exactRef(refName);
      assertWithMessage(refName).that(ref).isNotNull();
      ObjectId oldCommitId = ref.getObjectId();

      DirCache dc = DirCache.newInCore();
      DirCacheBuilder b = dc.builder();
      b.addTree(
          new byte[0], DirCacheEntry.STAGE_0, rw.getObjectReader(), rw.parseTree(oldCommitId));
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
      PersonIdent ident = serverIdent.get();
      cb.setAuthor(ident);
      cb.setCommitter(ident);
      cb.setMessage("Direct update submodule " + path);
      ObjectId newCommitId = ins.insert(cb);
      ins.flush();

      RefUpdate ru = serverRepo.updateRef(refName);
      ru.setExpectedOldObjectId(oldCommitId);
      ru.setNewObjectId(newCommitId);
      assertThat(ru.update()).isEqualTo(RefUpdate.Result.FAST_FORWARD);
    }
  }
}
