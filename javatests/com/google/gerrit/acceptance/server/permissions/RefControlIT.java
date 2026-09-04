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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.entities.Permission.READ;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for ref-based permission settings.
 *
 * <p>Tests use {@link PermissionBackend#filter} directly to verify which refs are visible, covering
 * scenarios not already tested by {@link com.google.gerrit.acceptance.git.RefAdvertisementIT}
 * (which uses the git wire protocol) or {@link com.google.gerrit.server.permissions.RefControlTest}
 * (which tests at the unit level).
 */
public class RefControlIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private PermissionBackend permissionBackend;

  private AccountGroup.UUID privileged;
  private TestAccount privilegedUser;

  @Before
  public void setUpGroups() throws Exception {
    privileged = AccountGroup.uuid(gApi.groups().create(name("privileged")).get().id);
    privilegedUser = accountCreator.create(name("privileged-user"), "priv@test.com", "Priv", null);
    gApi.groups().id(privileged.get()).addMembers(privilegedUser.username());
    // Remove All-Projects default READ grants so each test controls ACLs precisely.
    projectOperations
        .project(allProjects)
        .forUpdate()
        .remove(permissionKey(READ).ref("refs/heads/*").group(ANONYMOUS_USERS))
        .remove(permissionKey(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .remove(permissionKey(READ).ref("refs/meta/version").group(ANONYMOUS_USERS))
        .update();
  }

  @Test
  public void perProjectDeny_hidesProjectOnPublicServer() throws Exception {
    // Simulate a public server: All-Projects grants READ to Anonymous Users.
    // The per-project DENY on refs/* makes the project invisible to everyone
    // except users with an explicit ALLOW in that project.
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(ANONYMOUS_USERS))
        .update();

    Project.NameKey hidden = projectOperations.newProject().create();
    gApi.projects().name(hidden.get()).branch("main").create(new BranchInput());

    // Deny read for anonymous (= everyone) in the project itself.
    projectOperations
        .project(hidden)
        .forUpdate()
        .add(deny(READ).ref("refs/*").group(ANONYMOUS_USERS))
        .update();

    // Regular user sees no refs.
    assertThat(visibleRefs(hidden, user)).isEmpty();

    // Granting READ explicitly in the same project still works.
    projectOperations
        .project(hidden)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(privileged))
        .update();
    assertThat(visibleRefs(hidden, privilegedUser)).contains("refs/heads/main");
  }

  @Test
  public void blockAnonymousUsers_blocksEveryone() throws Exception {
    // Blocking Anonymous Users blocks all users (registered too) since every
    // user is a member of Anonymous Users. Without an ALLOW in the same section,
    // no group can bypass the block.
    Project.NameKey p = projectOperations.newProject().create();
    gApi.projects().name(p.get()).branch("main").create(new BranchInput());
    projectOperations
        .project(p)
        .forUpdate()
        .add(block(READ).ref("refs/*").group(ANONYMOUS_USERS))
        .update();

    assertThat(visibleRefs(p, user)).isEmpty();
    assertThat(visibleRefs(p, privilegedUser)).isEmpty();
  }

  @Test
  public void blockAnonymous_allowPrivileged_inSameSection_unblocks() throws Exception {
    // ALLOW in the same AccessSection cancels the BLOCK for members of
    // the allowed group.
    Project.NameKey p = projectOperations.newProject().create();
    gApi.projects().name(p.get()).branch("main").create(new BranchInput());
    projectOperations
        .project(p)
        .forUpdate()
        .add(block(READ).ref("refs/*").group(ANONYMOUS_USERS))
        .add(allow(READ).ref("refs/*").group(privileged))
        .update();

    // Unprivileged registered user is still blocked (ALLOW is only for privileged group).
    assertThat(visibleRefs(p, user)).isEmpty();
    // Privileged user: ALLOW in same section cancels the BLOCK.
    assertThat(visibleRefs(p, privilegedUser)).contains("refs/heads/main");
  }

  @Test
  public void blockWithExclusiveAllowOnMoreSpecificRef_unblocks() throws Exception {
    // Documented example:
    //   [access "refs/*"]        read = block group X
    //   [access "refs/heads/*"]  exclusiveGroupPermissions = read
    //                            read = group X
    // Members of X can read refs/heads/* but not other refs.
    Project.NameKey p = projectOperations.newProject().create();

    projectOperations
        .project(p)
        .forUpdate()
        .add(block(READ).ref("refs/*").group(privileged))
        .add(allow(READ).ref("refs/heads/*").group(privileged))
        .setExclusiveGroup(permissionKey(READ).ref("refs/heads/*"), true)
        .update();

    ImmutableList<String> visible = visibleRefs(p, privilegedUser);
    // Branches are visible via the exclusive ALLOW.
    assertThat(visible).containsExactlyElementsIn(List.of("HEAD", "refs/heads/master"));
  }

  @Test
  public void blockWithNonExclusiveAllowOnMoreSpecificRef_doesNotUnblock() throws Exception {
    // Without the exclusive flag on refs/heads/*, the ALLOW on the more specific
    // ref does not override the parent BLOCK.
    Project.NameKey p = projectOperations.newProject().create();
    gApi.projects().name(p.get()).branch("main").create(new BranchInput());

    projectOperations
        .project(p)
        .forUpdate()
        .add(block(READ).ref("refs/*").group(privileged))
        .add(allow(READ).ref("refs/heads/*").group(privileged))
        // NOTE: no setExclusiveGroup — non-exclusive ALLOW cannot unblock
        .update();

    assertThat(visibleRefs(p, privilegedUser)).isEmpty();
  }

  @Test
  public void deny_onlyAffectsSpecificGroup_otherGroupUnaffected() throws Exception {
    // DENY for privileged group on refs/heads/secret, but user is NOT in privileged.
    // All-Projects ALLOW for REGISTERED_USERS still applies to the regular user.
    Project.NameKey p = projectOperations.newProject().create();
    gApi.projects().name(p.get()).branch("secret").create(new BranchInput());
    gApi.projects().name(p.get()).branch("main").create(new BranchInput());

    projectOperations
        .project(p)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(deny(READ).ref("refs/heads/secret").group(privileged))
        .update();

    // Regular user (not in privileged) can still see refs/heads/secret.
    assertThat(visibleRefs(p, user)).contains("refs/heads/secret");
    // Privileged user has the DENY on refs/heads/secret but ALLOW on refs/*.
    // The DENY cancels the ALLOW for the same (ref-pattern, group) via SeenRule,
    // but the ALLOW on refs/* has a different ref pattern so it still applies.
    assertThat(visibleRefs(p, privilegedUser)).contains("refs/heads/secret");
  }

  @Test
  public void deny_doesNotPreventAccessViaInheritedDifferentRefPattern() throws Exception {
    // Doc: "DENY/ALLOW example" — child DENY on refs/heads/secret for REGISTERED_USERS,
    // but the parent also has ALLOW on refs/heads/* for REGISTERED_USERS.
    // The DENY only cancels (refs/heads/secret, REGISTERED_USERS) via SeenRule,
    // but the parent ALLOW covers refs/heads/* which is a different ref pattern,
    // so access is still granted.
    Project.NameKey parent = projectOperations.newProject().create();
    Project.NameKey child = projectOperations.newProject().parent(parent).create();
    gApi.projects().name(child.get()).branch("secret").create(new BranchInput());

    projectOperations
        .project(parent)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(child)
        .forUpdate()
        .add(deny(READ).ref("refs/heads/secret").group(REGISTERED_USERS))
        .update();

    // The parent ALLOW on refs/heads/* (different pattern) still applies.
    assertThat(visibleRefs(child, user)).contains("refs/heads/secret");
  }

  @Test
  public void grantReadOnRefsTagsOnly_doesNotMakeTagsVisible() throws Exception {
    // Granting READ on refs/tags/* alone has no effect; tags are visible only
    // when reachable from a readable branch.
    Project.NameKey p = projectOperations.newProject().create();
    gApi.projects().name(p.get()).branch("main").create(new BranchInput());
    gApi.projects().name(p.get()).tag("v1.0").create(new TagInput());

    projectOperations
        .project(p)
        .forUpdate()
        .add(allow(READ).ref("refs/tags/*").group(REGISTERED_USERS))
        .update();

    // No branches are readable, so no tags are visible either.
    assertThat(visibleRefs(p, user)).containsNoneIn(ImmutableList.of("refs/tags/v1.0"));
  }

  @Test
  public void tagVisibleWhenReachableFromReadableBranch() throws Exception {
    // A tag is visible if and only if it is reachable from a branch the user can read.
    Project.NameKey p = projectOperations.newProject().create();
    gApi.projects().name(p.get()).branch("main").create(new BranchInput());

    projectOperations
        .project(p)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    // Create a tag pointing at HEAD
    gApi.projects().name(p.get()).tag("v1.0").create(new TagInput());

    assertThat(visibleRefs(p, user)).contains("refs/tags/v1.0");

    // Now restrict READ to a subset of branches that does not include main.
    // Remove the broad allow and grant only on refs/heads/other/*.
    projectOperations
        .project(p)
        .forUpdate()
        .remove(permissionKey(READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(READ).ref("refs/heads/other/*").group(REGISTERED_USERS))
        .update();

    // Tag is no longer reachable from any visible ref, so it becomes invisible.
    assertThat(visibleRefs(p, user)).doesNotContain("refs/tags/v1.0");
  }

  @Test
  public void blockInParent_childCannotUnblockWithExclusive() throws Exception {
    // An exclusive read access in a child project does not unblock
    // read access blocked in a parent repository
    Project.NameKey parent = projectOperations.newProject().create();
    Project.NameKey child = projectOperations.newProject().parent(parent).create();
    gApi.projects().name(child.get()).branch("main").create(new BranchInput());

    projectOperations
        .project(parent)
        .forUpdate()
        .add(block(READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(child)
        .forUpdate()
        .add(allow(READ).ref("refs/*").group(REGISTERED_USERS))
        .setExclusiveGroup(permissionKey(READ).ref("refs/*"), true)
        .update();

    // The parent's BLOCK cannot be overridden by the child's exclusive ALLOW.
    assertThat(visibleRefs(child, user)).isEmpty();
  }

  @Test
  public void regexRefPattern_matchesOnlyMatchingBranches() throws Exception {
    Project.NameKey p = projectOperations.newProject().create();
    gApi.projects().name(p.get()).branch("short").create(new BranchInput());
    gApi.projects().name(p.get()).branch("UPPERCASE").create(new BranchInput());
    gApi.projects().name(p.get()).branch("verylongbranchname").create(new BranchInput());

    // Allow read only on lowercase branches of 1-8 characters.
    projectOperations
        .project(p)
        .forUpdate()
        .add(allow(READ).ref("^refs/heads/[a-z]{1,8}").group(REGISTERED_USERS))
        .update();

    ImmutableList<String> visible = visibleRefs(p, user);
    assertThat(visible).contains("refs/heads/short");
    assertThat(visible).doesNotContain("refs/heads/UPPERCASE");
    assertThat(visible).doesNotContain("refs/heads/verylongbranchname");
  }

  @Test
  public void usernamePattern_userSeesOnlyOwnBranch() throws Exception {
    Project.NameKey p = projectOperations.newProject().create();
    // Branch matching the regular user's username.
    String userBranch = "sandbox/" + user.username() + "/feature";
    // Branch matching the privileged user's username.
    String privilegedBranch = "sandbox/" + privilegedUser.username() + "/feature";
    gApi.projects().name(p.get()).branch(userBranch).create(new BranchInput());
    gApi.projects().name(p.get()).branch(privilegedBranch).create(new BranchInput());

    projectOperations
        .project(p)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/sandbox/${username}/*").group(REGISTERED_USERS))
        .update();

    ImmutableList<String> visibleToUser = visibleRefs(p, user);
    assertThat(visibleToUser).contains("refs/heads/" + userBranch);
    assertThat(visibleToUser).doesNotContain("refs/heads/" + privilegedBranch);

    ImmutableList<String> visibleToPrivileged = visibleRefs(p, privilegedUser);
    assertThat(visibleToPrivileged).contains("refs/heads/" + privilegedBranch);
    assertThat(visibleToPrivileged).doesNotContain("refs/heads/" + userBranch);
  }

  @Test
  public void childAllow_moreSpecific_overridesNarrowerParentAllow() throws Exception {
    // Parent allows only refs/heads/main; child additionally allows refs/heads/feature/*.
    Project.NameKey parent = projectOperations.newProject().create();
    Project.NameKey child = projectOperations.newProject().parent(parent).create();
    gApi.projects().name(child.get()).branch("main").create(new BranchInput());
    gApi.projects().name(child.get()).branch("feature/foo").create(new BranchInput());

    projectOperations
        .project(parent)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/main").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(child)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/feature/*").group(REGISTERED_USERS))
        .update();

    ImmutableList<String> visible = visibleRefs(child, user);
    assertThat(visible).contains("refs/heads/main");
    assertThat(visible).contains("refs/heads/feature/foo");
  }

  @Test
  public void exclusiveAllow_preventsOtherGroupsFromInheritingAccess() throws Exception {
    // All-Projects ALLOW for REGISTERED_USERS (simulated via parent project).
    // Child marks READ on refs/heads/restricted/* exclusive for privileged group.
    // Regular registered users lose access to refs/heads/restricted/* because
    // the exclusive flag stops the upward search before reaching REGISTERED_USERS.
    Project.NameKey parent = projectOperations.newProject().create();
    Project.NameKey p = projectOperations.newProject().parent(parent).create();
    gApi.projects().name(p.get()).branch("main").create(new BranchInput());
    gApi.projects().name(p.get()).branch("restricted/secret").create(new BranchInput());

    // Parent grants broad READ to all registered users.
    projectOperations
        .project(parent)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();
    // Child grants exclusive READ on restricted/* only to privileged group.
    // The exclusive flag stops the search before the parent ALLOW is reached.
    projectOperations
        .project(p)
        .forUpdate()
        .add(allow(READ).ref("refs/heads/restricted/*").group(privileged))
        .setExclusiveGroup(permissionKey(READ).ref("refs/heads/restricted/*"), true)
        .update();

    // Regular user cannot see restricted branch (exclusive stops inherited ALLOW).
    assertThat(visibleRefs(p, user)).doesNotContain("refs/heads/restricted/secret");
    // Non-restricted branches are still accessible via inherited parent ALLOW.
    assertThat(visibleRefs(p, user)).contains("refs/heads/main");
    // Privileged user can see the restricted branch.
    assertThat(visibleRefs(p, privilegedUser)).contains("refs/heads/restricted/secret");
  }

  private ImmutableList<String> visibleRefs(Project.NameKey project, TestAccount account)
      throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      return permissionBackend
          .user(identifiedUserFactory.create(account.id()))
          .project(project)
          .filter(
              repo.getRefDatabase().getRefs(), repo, PermissionBackend.RefFilterOptions.defaults())
          .stream()
          .map(Ref::getName)
          .collect(toImmutableList());
    }
  }
}
