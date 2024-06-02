// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Base class for different tests for implicit merge.
 *
 * <p>Provides shared methods for tests changes and branches manipulations.
 */
public abstract class AbstractImplicitMergeTest extends AbstractDaemonTest {

  /** Creates and pushes a simple approved changes without files and with specified parents. */
  protected PushOneCommit.Result createApprovedChange(String targetBranch, RevCommit... parents)
      throws Exception {
    PushOneCommit.Result result = pushTo("refs/for/" + targetBranch, ImmutableMap.of(), parents);
    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.approve());
    return result;
  }

  /** Creates and pushes simple approved changes without files and with specified parents. */
  protected PushOneCommit.Result createApprovedChange(
      String targetBranch, PushOneCommit.Result... parents) throws Exception {
    return createApprovedChange(
        targetBranch,
        Arrays.stream(parents).map(PushOneCommit.Result::getCommit).toArray(RevCommit[]::new));
  }

  /** Creates and pushes a commit with specified files and parents. */
  protected PushOneCommit.Result pushTo(
      String ref, ImmutableMap<String, String> files, RevCommit... parents) throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, "Some commit", files);
    push.setParents(List.of(parents));
    PushOneCommit.Result result = push.to(ref);
    result.assertOkStatus();
    return result;
  }

  /**
   * Creates a change in the in-memory repository but doesn't push it to gerrit.
   *
   * <p>The method can be used to create chain of changes. The last change in the chain can be
   * created using {@link #createApprovedChange} or {@link #pushTo} methods - these method will push
   * the whole chain to gerrit as a single git push operations.
   */
  protected RevCommit createChangeWithoutPush(
      String changeId, ImmutableMap<String, String> files, RevCommit... parents) throws Exception {
    TestRepository<?>.CommitBuilder commitBuilder =
        testRepo
            .commit()
            .message("Change " + changeId)
            // The passed changeId starts with 'I', but insertChangeId expects id without 'I'.
            .insertChangeId(changeId.substring(1));
    for (RevCommit parent : parents) {
      commitBuilder.parent(parent);
    }
    for (Map.Entry<String, String> entry : files.entrySet()) {
      commitBuilder.add(entry.getKey(), entry.getValue());
    }

    return commitBuilder.create();
  }

  protected void setRejectImplicitMerges() throws Exception {
    setRejectImplicitMerges(/* reject= */ true);
  }

  protected void setRejectImplicitMerges(boolean reject) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig()
          .updateProject(
              p ->
                  p.setBooleanConfig(
                      BooleanProjectConfig.REJECT_IMPLICIT_MERGES,
                      reject ? InheritableBoolean.TRUE : InheritableBoolean.FALSE));
      u.save();
    }
  }

  protected void setSubmitType(SubmitType submitType) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateProject(p -> p.setSubmitType(submitType));
      u.save();
    }
  }

  protected ImmutableMap<String, String> getRemoteBranchRootPathContent(String refName)
      throws Exception {
    String revision = gApi.projects().name(project.get()).branch(refName).get().revision;
    testRepo.git().fetch().setRemote("origin").call();
    RevTree revTree =
        testRepo.getRepository().parseCommit(testRepo.getRepository().resolve(revision)).getTree();
    try (TreeWalk tw = new TreeWalk(testRepo.getRepository())) {
      tw.setFilter(TreeFilter.ALL);
      tw.setRecursive(false);
      tw.reset(revTree);
      ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
      while (tw.next()) {
        String path = tw.getPathString();
        String content =
            RawParseUtils.decode(
                testRepo.getRepository().open(tw.getObjectId(0)).getCachedBytes(Integer.MAX_VALUE));
        builder.put(path, content);
      }
      return builder.buildOrThrow();
    }
  }

  protected PushOneCommit.Result push(String ref, String subject, String fileName, String content)
      throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, fileName, content);
    return push.to(ref);
  }
}
