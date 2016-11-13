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

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.SubscribeSection;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;

public abstract class AbstractSubmoduleSubscription extends AbstractDaemonTest {

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

  protected TestRepository<?> createProjectWithPush(
      String name,
      @Nullable Project.NameKey parent,
      boolean createEmptyCommit,
      SubmitType submitType)
      throws Exception {
    Project.NameKey project = createProject(name, parent, createEmptyCommit, submitType);
    grant(Permission.PUSH, project, "refs/heads/*");
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/*");
    return cloneProject(project);
  }

  protected TestRepository<?> createProjectWithPush(String name, @Nullable Project.NameKey parent)
      throws Exception {
    return createProjectWithPush(name, parent, true, getSubmitType());
  }

  protected TestRepository<?> createProjectWithPush(String name, boolean createEmptyCommit)
      throws Exception {
    return createProjectWithPush(name, null, createEmptyCommit, getSubmitType());
  }

  protected TestRepository<?> createProjectWithPush(String name) throws Exception {
    return createProjectWithPush(name, null, true, getSubmitType());
  }

  private static AtomicInteger contentCounter = new AtomicInteger(0);

  protected ObjectId pushChangeTo(
      TestRepository<?> repo, String ref, String file, String content, String message, String topic)
      throws Exception {
    ObjectId ret =
        repo.branch("HEAD").commit().insertChangeId().message(message).add(file, content).create();

    String pushedRef = ref;
    if (!topic.isEmpty()) {
      pushedRef += "/" + name(topic);
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

  protected void allowSubmoduleSubscription(
      String submodule, String subBranch, String superproject, String superBranch, boolean match)
      throws Exception {
    Project.NameKey sub = new Project.NameKey(name(submodule));
    Project.NameKey superName = new Project.NameKey(name(superproject));
    try (MetaDataUpdate md = metaDataUpdateFactory.create(sub)) {
      md.setMessage("Added superproject subscription");
      SubscribeSection s;
      ProjectConfig pc = ProjectConfig.read(md);
      if (pc.getSubscribeSections().containsKey(superName)) {
        s = pc.getSubscribeSections().get(superName);
      } else {
        s = new SubscribeSection(superName);
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
      pc.addSubscribeSection(s);
      ObjectId oldId = pc.getRevision();
      ObjectId newId = pc.commit(md);
      assertThat(newId).isNotEqualTo(oldId);
      projectCache.evict(pc.getProject());
    }
  }

  protected void allowMatchingSubmoduleSubscription(
      String submodule, String subBranch, String superproject, String superBranch)
      throws Exception {
    allowSubmoduleSubscription(submodule, subBranch, superproject, superBranch, true);
  }

  protected void createSubmoduleSubscription(
      TestRepository<?> repo, String branch, String subscribeToRepo, String subscribeToBranch)
      throws Exception {
    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subscribeToRepo, subscribeToBranch);
    pushSubmoduleConfig(repo, branch, config);
  }

  protected void createRelativeSubmoduleSubscription(
      TestRepository<?> repo,
      String branch,
      String subscribeToRepoPrefix,
      String subscribeToRepo,
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
      String subscribeToRepo,
      String subscribeToBranch) {
    subscribeToRepo = name(subscribeToRepo);
    String url = subscribeToRepoPrefix + subscribeToRepo;
    config.setString("submodule", subscribeToRepo, "path", subscribeToRepo);
    config.setString("submodule", subscribeToRepo, "url", url);
    if (subscribeToBranch != null) {
      config.setString("submodule", subscribeToRepo, "branch", subscribeToBranch);
    }
  }

  protected void prepareSubmoduleConfigEntry(
      Config config, String subscribeToRepo, String subscribeToBranch) {
    // The submodule subscription module checks for gerrit.canonicalWebUrl to
    // detect if it's configured for automatic updates. It doesn't matter if
    // it serves from that URL.
    prepareSubmoduleConfigEntry(config, subscribeToRepo, subscribeToRepo, subscribeToBranch);
  }

  protected void prepareSubmoduleConfigEntry(
      Config config, String subscribeToRepo, String subscribeToRepoPath, String subscribeToBranch) {
    subscribeToRepo = name(subscribeToRepo);
    subscribeToRepoPath = name(subscribeToRepoPath);
    // The submodule subscription module checks for gerrit.canonicalWebUrl to
    // detect if it's configured for automatic updates. It doesn't matter if
    // it serves from that URL.
    String url = cfg.getString("gerrit", null, "canonicalWebUrl") + "/" + subscribeToRepo;
    config.setString("submodule", subscribeToRepoPath, "path", subscribeToRepoPath);
    config.setString("submodule", subscribeToRepoPath, "url", url);
    if (subscribeToBranch != null) {
      config.setString("submodule", subscribeToRepoPath, "branch", subscribeToBranch);
    }
  }

  protected void pushSubmoduleConfig(TestRepository<?> repo, String branch, Config config)
      throws Exception {

    repo.branch("HEAD")
        .commit()
        .insertChangeId()
        .message("subject: adding new subscription")
        .add(".gitmodules", config.toText().toString())
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
      String submodule,
      TestRepository<?> subRepo,
      String subBranch)
      throws Exception {

    submodule = name(submodule);
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
    RevObject actualId = repo.get(tree, submodule);

    assertThat(actualId).isEqualTo(subHead);
  }

  protected void expectToHaveSubmoduleState(
      TestRepository<?> repo, String branch, String submodule, ObjectId expectedId)
      throws Exception {

    submodule = name(submodule);
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
    RevObject actualId = repo.get(tree, submodule);

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

  protected boolean hasSubmodule(TestRepository<?> repo, String branch, String submodule)
      throws Exception {

    submodule = name(submodule);
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
      repo.get(tree, submodule);
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
    assertThat(c.getFullMessage()).isEqualTo(expectedMessage);
  }
}
