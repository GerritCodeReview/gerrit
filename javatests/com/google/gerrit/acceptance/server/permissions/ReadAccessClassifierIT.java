// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.permissions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.entities.Permission.READ;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectControl;
import com.google.gerrit.server.permissions.ReadAccessClassifier;
import com.google.gerrit.server.permissions.ReadAccessClassifier.Decision;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link ReadAccessClassifier}.
 *
 * <p>Each test configures ACLs on a fresh project and verifies that {@link
 * ReadAccessClassifier#classify} returns the right {@link Decision} for representative ref names,
 * covering the three outcomes: {@link Decision#VISIBLE}, {@link Decision#INVISIBLE}, and {@link
 * Decision#NEEDS_FULL_CHECK}.
 */
public class ReadAccessClassifierIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private ProjectControl.Factory projectControlFactory;
  @Inject private PermissionBackend permissionBackend;

  private final TestMetricMaker testMetricMaker = TestMetricMaker.getInstance();

  private Project.NameKey testProject;
  private AccountGroup.UUID testGroup;

  @Before
  public void setUpProjectAndGroup() throws Exception {
    testProject = projectOperations.newProject().create();
    testGroup = AccountGroup.uuid(gApi.groups().create(name("test-group")).get().id);
    gApi.groups().id(testGroup.get()).addMembers(user.username());
    projectOperations
        .project(allProjects)
        .forUpdate()
        .remove(permissionKey(READ).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .remove(permissionKey(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
  }

  @Test
  public void allowOnRefsStar_plainBranchIsVisible() throws Exception {
    projectOperations
        .project(testProject)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(testGroup))
        .update();

    ReadAccessClassifier classifier = classifierFor(testProject);

    assertDecision(classifier, "refs/heads/main", Decision.VISIBLE);
  }

  @Test
  public void noAllowRule_plainBranchIsInvisible() throws Exception {
    ReadAccessClassifier classifier = classifierFor(testProject);

    assertDecision(classifier, "refs/heads/main", Decision.INVISIBLE);
  }

  @Test
  public void allowOnRefsStar_userNotInGroup_isInvisible() throws Exception {
    AccountGroup.UUID otherGroup =
        AccountGroup.uuid(gApi.groups().create(name("other-group")).get().id);
    projectOperations
        .project(testProject)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(otherGroup))
        .update();

    ReadAccessClassifier classifier = classifierFor(testProject);

    assertDecision(classifier, "refs/heads/main", Decision.INVISIBLE);
  }

  @Test
  public void allowOnRefsStar_tagIsVisible() throws Exception {
    // refs/tags/* matches the refs/* allow pattern, so the classifier returns
    // VISIBLE. In DefaultRefFilter, tag refs go through the reachability walk
    // rather than the classifier, so this result is not used in production for
    // tags.  The test documents classifier behaviour for completeness.
    projectOperations
        .project(testProject)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(testGroup))
        .update();

    ReadAccessClassifier classifier = classifierFor(testProject);

    assertDecision(classifier, "refs/tags/v1.0", Decision.VISIBLE);
  }

  @Test
  public void denyOnBranch_thatBranchIsInvisible() throws Exception {
    projectOperations
        .project(testProject)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(testGroup))
        .add(deny(READ).ref("refs/heads/secret").group(testGroup))
        .update();

    ReadAccessClassifier classifier = classifierFor(testProject);

    assertDecision(classifier, "refs/heads/secret", Decision.INVISIBLE);
    assertDecision(classifier, "refs/heads/main", Decision.VISIBLE);
  }

  @Test
  public void blockOnBranch_thatBranchIsInvisible() throws Exception {
    projectOperations
        .project(testProject)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(block(READ).ref("refs/heads/locked").group(REGISTERED_USERS))
        .update();

    ReadAccessClassifier classifier = classifierFor(testProject);

    assertDecision(classifier, "refs/heads/locked", Decision.INVISIBLE);
    assertDecision(classifier, "refs/heads/main", Decision.VISIBLE);
  }

  @Test
  public void perUserPattern_needsFullCheck() throws Exception {
    projectOperations
        .project(testProject)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/users/${username}/*").group(testGroup))
        .update();

    ReadAccessClassifier classifier = classifierFor(testProject);

    assertDecision(classifier, "refs/heads/users/alice/feature", Decision.NEEDS_FULL_CHECK);
    assertDecision(classifier, "refs/heads/main", Decision.INVISIBLE);
  }

  @Test
  public void filterRefs_classifierShortcutsNonGerritRefs() throws Exception {
    // ALLOW READ refs/* + DENY on one branch: neither existing fast path fires
    // (allRefsAreVisible=false, hasReadOnRefsStar=false due to the deny), so
    // the full per-ref loop runs.  The classifier should short-circuit every
    // refs/heads/* ref, incrementing the shortcut counter instead of calling
    // controlForRef() for each one.
    projectOperations
        .project(testProject)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(block(READ).ref("refs/heads/secret").group(REGISTERED_USERS))
        .update();
    for (String branch : ImmutableList.of("main", "feature", "release")) {
      gApi.projects().name(testProject.get()).branch(branch).create(new BranchInput());
    }
    gApi.projects().name(testProject.get()).branch("secret").create(new BranchInput());

    testMetricMaker.reset();
    try (Repository repo = repoManager.openRepository(testProject)) {
      @SuppressWarnings("unused")
      var unused =
          permissionBackend
              .user(identifiedUserFactory.create(user.id()))
              .project(testProject)
              .filter(
                  repo.getRefDatabase().getRefs(),
                  repo,
                  PermissionBackend.RefFilterOptions.defaults());
    }

    assertThat(testMetricMaker.getCount("permissions/ref_filter/classifier_shortcut_count"))
        .isGreaterThan(0);
  }

  @Test
  public void exclusiveReadOnBranch_parentAllowDoesNotApply() throws Exception {
    // Parent project grants ALLOW READ refs/* to REGISTERED_USERS.
    // Child project marks READ on refs/heads/locked as exclusive for a
    // different group (otherGroup), so REGISTERED_USERS cannot read it.
    // The classifier must honour the exclusive flag and return INVISIBLE,
    // not VISIBLE based on the inherited refs/* allow.
    Project.NameKey parentProject = projectOperations.newProject().create();
    Project.NameKey childProject = projectOperations.newProject().parent(parentProject).create();
    AccountGroup.UUID otherGroup =
        AccountGroup.uuid(gApi.groups().create(name("other-group")).get().id);

    projectOperations
        .project(parentProject)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(testGroup))
        .update();
    projectOperations
        .project(childProject)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/locked").group(otherGroup))
        .setExclusiveGroup(permissionKey(READ).ref("refs/heads/locked"), true)
        .update();

    ReadAccessClassifier classifier = classifierFor(childProject);

    assertDecision(classifier, "refs/heads/locked", Decision.INVISIBLE);
    assertDecision(classifier, "refs/heads/main", Decision.VISIBLE);
  }

  private ReadAccessClassifier classifierFor(Project.NameKey key) throws Exception {
    ProjectState state =
        projectCache
            .get(key)
            .orElseThrow(() -> new IllegalStateException("project not found: " + key));
    ProjectControl control =
        projectControlFactory.create(identifiedUserFactory.create(user.id()), state);
    return new ReadAccessClassifier(control);
  }

  private static void assertDecision(
      ReadAccessClassifier classifier, String refName, Decision expected) {
    assertWithMessage("classify(%s)", refName)
        .that(classifier.classify(refName))
        .isEqualTo(expected);
  }
}
