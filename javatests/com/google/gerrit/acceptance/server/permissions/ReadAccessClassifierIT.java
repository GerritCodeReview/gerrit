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
import org.eclipse.jgit.lib.Ref;
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

    // The DENY section triggers a full ACL check; the full check correctly
    // returns invisible because the DENY suppresses the inherited ALLOW.
    assertDecision(classifier, "refs/heads/secret", Decision.NEEDS_FULL_CHECK);
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

    // The BLOCK section triggers a full ACL check; the full check correctly
    // returns invisible because the BLOCK rule applies.
    assertDecision(classifier, "refs/heads/locked", Decision.NEEDS_FULL_CHECK);
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

    assertDecision(classifier, "refs/heads/locked", Decision.NEEDS_FULL_CHECK);
    assertDecision(classifier, "refs/heads/main", Decision.VISIBLE);
  }

  @Test
  public void allowCancelsBlockOnSamePattern_branchIsVisible() throws Exception {
    // When BLOCK READ and ALLOW READ are on the same ref pattern for the same
    // group, Gerrit's ACL semantics let the ALLOW cancel the BLOCK (they are in
    // the same Permission object / AccessSection). The classifier must not treat
    // the branch as INVISIBLE just because a BLOCK rule is present.
    projectOperations
        .project(testProject)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/master").group(testGroup))
        .add(block(READ).ref("refs/heads/master").group(testGroup))
        .update();

    ReadAccessClassifier classifier = classifierFor(testProject);

    assertDecision(classifier, "refs/heads/master", Decision.NEEDS_FULL_CHECK);
  }

  @Test
  public void exclusiveAllowInChild_overridesParentBlock_branchIsVisible() throws Exception {
    // An exclusive ALLOW in the child project overrides a BLOCK in the parent
    // project (RefControl.isBlocked() skips the whole parent-project block list
    // when an exclusive ALLOW is found in the child). The classifier must not
    // return INVISIBLE based on the parent BLOCK alone.
    Project.NameKey parentProject = projectOperations.newProject().create();
    Project.NameKey childProject =
        projectOperations.newProject().parent(parentProject).create();

    projectOperations
        .project(parentProject)
        .forUpdate()
        .add(block(READ).ref("refs/heads/master").group(testGroup))
        .update();
    projectOperations
        .project(childProject)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/master").group(testGroup))
        .setExclusiveGroup(permissionKey(READ).ref("refs/heads/master"), true)
        .update();

    ReadAccessClassifier classifier = classifierFor(childProject);

    assertDecision(classifier, "refs/heads/master", Decision.NEEDS_FULL_CHECK);
  }

  @Test
  public void denyInChild_preventsInheritedAllow_branchIsInvisible() throws Exception {
    // A DENY rule in the child project prevents the inherited ALLOW from the
    // parent from applying (SeenRule mechanism in PermissionCollection marks the
    // (section, group) pair as seen so the parent ALLOW is skipped). The
    // classifier must return INVISIBLE (or NEEDS_FULL_CHECK) — not VISIBLE.
    Project.NameKey parentProject = projectOperations.newProject().create();
    Project.NameKey childProject =
        projectOperations.newProject().parent(parentProject).create();

    projectOperations
        .project(parentProject)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/master").group(testGroup))
        .update();
    projectOperations
        .project(childProject)
        .forUpdate()
        .add(deny(READ).ref("refs/heads/master").group(testGroup))
        .update();

    ReadAccessClassifier classifier = classifierFor(childProject);

    // The branch is invisible because the child DENY cancels the parent ALLOW.
    assertDecision(classifier, "refs/heads/master", Decision.NEEDS_FULL_CHECK);
  }

  @Test
  public void exclusiveAllowInIntermediateParent_suppressesGrandparentAllow() throws Exception {
    // Hierarchy: childProject -> parentProject -> All-Projects
    //
    // All-Projects: ALLOW READ refs/* for REGISTERED_USERS (broad default access)
    // parentProject: ALLOW READ refs/heads/secret/* for testGroup, EXCLUSIVE
    //   The exclusive flag means: for refs matching refs/heads/secret/*, only
    //   testGroup has access; the broad All-Projects ALLOW is suppressed.
    // childProject: inherits
    //
    // A user in REGISTERED_USERS but NOT in testGroup must NOT see
    // refs/heads/secret/feature. The classifier must return NEEDS_FULL_CHECK
    // (not VISIBLE) so the full ACL check, which honours the exclusive break
    // in PermissionCollection.calculateAllowRules(), returns the correct
    // INVISIBLE result.
    //
    // This catches the bug where exclusiveMatchers only tracked ownProject
    // sections, allowing parentAllowMatchers from All-Projects to leak through
    // for refs covered by an exclusive section in an intermediate parent.
    Project.NameKey parentProject = projectOperations.newProject().create();
    Project.NameKey childProject =
        projectOperations.newProject().parent(parentProject).create();

    projectOperations
        .project(parentProject)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/secret/*").group(testGroup))
        .setExclusiveGroup(permissionKey(READ).ref("refs/heads/secret/*"), true)
        .update();

    // The test user is in REGISTERED_USERS but NOT in testGroup.
    // Without the fix, the classifier would check exclusiveMatchers only for
    // ownProject (childProject, which has none), then return VISIBLE for
    // refs/heads/secret/feature via All-Projects' refs/heads/* ALLOW in
    // parentAllowMatchers. With the fix, the parent's exclusive section is
    // tracked and refs/heads/secret/feature correctly gets NEEDS_FULL_CHECK.
    ReadAccessClassifier classifier = classifierFor(childProject);

    assertDecision(classifier, "refs/heads/secret/feature", Decision.NEEDS_FULL_CHECK);
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
