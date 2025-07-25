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

package com.google.gerrit.acceptance.testsuite.project;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.entities.AccessSection.GLOBAL_CAPABILITIES;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRange;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.server.config.AllProjectsName;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;

@AutoValue
public abstract class TestProjectUpdate {
  /** Starts a builder for allowing a capability. */
  public static TestCapability.Builder allowCapability(String name) {
    return TestCapability.builder().name(name);
  }

  /** Records a global capability to be updated. */
  @AutoValue
  public abstract static class TestCapability {
    private static Builder builder() {
      return new AutoValue_TestProjectUpdate_TestCapability.Builder();
    }

    abstract String name();

    abstract AccountGroup.UUID group();

    abstract int min();

    abstract int max();

    /** Builder for {@link TestCapability}. */
    @AutoValue.Builder
    public abstract static class Builder {
      /** Sets the name of the capability. */
      public abstract Builder name(String name);

      abstract String name();

      /** Sets the group to which the capability applies. */
      public abstract Builder group(AccountGroup.UUID group);

      abstract Builder min(int min);

      abstract Optional<Integer> min();

      abstract Builder max(int max);

      abstract Optional<Integer> max();

      /** Sets the minimum and maximum values for the capability. */
      @CanIgnoreReturnValue
      public Builder range(int min, int max) {
        checkNonInvertedRange(min, max);
        return min(min).max(max);
      }

      /** Builds the {@link TestCapability}. */
      abstract TestCapability autoBuild();

      public TestCapability build() {
        PermissionRange.WithDefaults withDefaults = GlobalCapability.getRange(name());
        if (withDefaults != null) {
          int min = min().orElse(withDefaults.getDefaultMin());
          int max = max().orElse(withDefaults.getDefaultMax());
          range(min, max);
          // Don't enforce range is nonempty; this is allowed for e.g. batchChangesLimit.
        } else {
          checkArgument(
              !min().isPresent() && !max().isPresent(),
              "capability %s does not support ranges",
              name());
          range(0, 0);
        }

        return autoBuild();
      }
    }
  }

  /** Starts a builder for allowing a permission. */
  public static TestPermission.Builder allow(String name) {
    return TestPermission.builder().name(name).action(PermissionRule.Action.ALLOW);
  }

  /** Starts a builder for denying a permission. */
  public static TestPermission.Builder deny(String name) {
    return TestPermission.builder().name(name).action(PermissionRule.Action.DENY);
  }

  /** Starts a builder for blocking a permission. */
  public static TestPermission.Builder block(String name) {
    return TestPermission.builder().name(name).action(PermissionRule.Action.BLOCK);
  }

  /**
   * Records a permission to be updated.
   *
   * <p>Not used for permissions that have ranges (label permissions) or global capabilities.
   */
  @AutoValue
  public abstract static class TestPermission {
    private static Builder builder() {
      return new AutoValue_TestProjectUpdate_TestPermission.Builder().force(false);
    }

    abstract String name();

    abstract String ref();

    abstract AccountGroup.UUID group();

    abstract PermissionRule.Action action();

    abstract boolean force();

    /** Builder for {@link TestPermission}. */
    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder name(String name);

      /** Sets the ref pattern used on the permission. */
      public abstract Builder ref(String ref);

      /** Sets the group to which the permission applies. */
      public abstract Builder group(AccountGroup.UUID groupUuid);

      abstract Builder action(PermissionRule.Action action);

      /** Sets whether the permission is a force permission. */
      public abstract Builder force(boolean force);

      /** Builds the {@link TestPermission}. */
      public abstract TestPermission build();
    }
  }

  /** Starts a builder for allowing a label permission. */
  public static TestLabelPermission.Builder allowLabel(String name) {
    return TestLabelPermission.builder()
        .name(name)
        .isAddPermission(true)
        .action(PermissionRule.Action.ALLOW);
  }

  /** Starts a builder for denying a label permission. */
  public static TestLabelPermission.Builder blockLabel(String name) {
    return TestLabelPermission.builder()
        .name(name)
        .isAddPermission(true)
        .action(PermissionRule.Action.BLOCK);
  }

  /** Starts a builder for allowing a remove-label permission. */
  public static TestLabelPermission.Builder allowLabelRemoval(String name) {
    return TestLabelPermission.builder()
        .name(name)
        .isAddPermission(false)
        .action(PermissionRule.Action.ALLOW);
  }

  /** Starts a builder for denying a remove-label permission. */
  public static TestLabelPermission.Builder blockLabelRemoval(String name) {
    return TestLabelPermission.builder()
        .name(name)
        .isAddPermission(false)
        .action(PermissionRule.Action.BLOCK);
  }

  /** Records a label permission to be updated. */
  @AutoValue
  public abstract static class TestLabelPermission {
    private static Builder builder() {
      return new AutoValue_TestProjectUpdate_TestLabelPermission.Builder().impersonation(false);
    }

    abstract String name();

    abstract String ref();

    abstract AccountGroup.UUID group();

    abstract PermissionRule.Action action();

    abstract int min();

    abstract int max();

    abstract boolean impersonation();

    abstract boolean isAddPermission();

    /** Builder for {@link TestLabelPermission}. */
    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder name(String name);

      /** Sets the ref pattern used on the permission. */
      public abstract Builder ref(String ref);

      /** Sets the group to which the permission applies. */
      public abstract Builder group(AccountGroup.UUID group);

      abstract Builder action(PermissionRule.Action action);

      abstract Builder min(int min);

      abstract Builder max(int max);

      abstract Builder isAddPermission(boolean isAddPermission);

      /** Sets the minimum and maximum values for the permission. */
      public Builder range(int min, int max) {
        checkArgument(min != 0 || max != 0, "empty range");
        checkNonInvertedRange(min, max);
        return min(min).max(max);
      }

      /** Sets whether this permission should be for impersonating another user's votes. */
      public abstract Builder impersonation(boolean impersonation);

      abstract TestLabelPermission autoBuild();

      /** Builds the {@link TestPermission}. */
      public TestLabelPermission build() {
        TestLabelPermission result = autoBuild();
        checkLabelName(result.name());
        return result;
      }
    }
  }

  /**
   * Starts a builder for describing a permission key for deletion. Not for label permissions or
   * global capabilities.
   */
  public static TestPermissionKey.Builder permissionKey(String name) {
    return TestPermissionKey.builder().name(name);
  }

  /** Starts a builder for describing a label permission key for deletion. */
  public static TestPermissionKey.Builder labelPermissionKey(String name) {
    checkLabelName(name);
    return TestPermissionKey.builder().name(Permission.forLabel(name));
  }

  /** Starts a builder for describing a label removal permission key for deletion. */
  public static TestPermissionKey.Builder labelRemovalPermissionKey(String name) {
    checkLabelName(name);
    return TestPermissionKey.builder().name(Permission.forRemoveLabel(name));
  }

  /** Starts a builder for describing a capability key for deletion. */
  public static TestPermissionKey.Builder capabilityKey(String name) {
    return TestPermissionKey.builder().name(name).section(GLOBAL_CAPABILITIES);
  }

  /** Records the key of a permission (of any type) for deletion. */
  @AutoValue
  public abstract static class TestPermissionKey {
    private static Builder builder() {
      return new AutoValue_TestProjectUpdate_TestPermissionKey.Builder();
    }

    abstract String section();

    abstract String name();

    abstract Optional<AccountGroup.UUID> group();

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder section(String section);

      abstract Optional<String> section();

      /** Sets the ref pattern used on the permission. Not for global capabilities. */
      public Builder ref(String ref) {
        requireNonNull(ref);
        checkArgument(ref.startsWith(Constants.R_REFS), "must be a ref: %s", ref);
        checkArgument(
            !section().isPresent() || !section().get().equals(GLOBAL_CAPABILITIES),
            "can't set ref on global capability");
        return section(ref);
      }

      abstract Builder name(String name);

      /** Sets the group to which the permission applies. */
      public abstract Builder group(AccountGroup.UUID group);

      /** Builds the {@link TestPermissionKey}. */
      public abstract TestPermissionKey build();
    }
  }

  static Builder builder(
      Project.NameKey nameKey,
      AllProjectsName allProjectsName,
      ThrowingConsumer<TestProjectUpdate> projectUpdater) {
    return new AutoValue_TestProjectUpdate.Builder()
        .nameKey(nameKey)
        .allProjectsName(allProjectsName)
        .projectUpdater(projectUpdater)
        .removeAllAccessSections(false);
  }

  /** Builder for {@link TestProjectUpdate}. */
  @AutoValue.Builder
  public abstract static class Builder {
    abstract Builder nameKey(Project.NameKey project);

    abstract Builder allProjectsName(AllProjectsName allProjects);

    abstract ImmutableList.Builder<TestPermission> addedPermissionsBuilder();

    abstract ImmutableList.Builder<TestLabelPermission> addedLabelPermissionsBuilder();

    abstract ImmutableList.Builder<TestCapability> addedCapabilitiesBuilder();

    abstract ImmutableList.Builder<TestPermissionKey> removedPermissionsBuilder();

    abstract ImmutableMap.Builder<TestPermissionKey, Boolean> exclusiveGroupPermissionsBuilder();

    abstract Builder removeAllAccessSections(boolean value);

    abstract ImmutableMap.Builder<BooleanProjectConfig, InheritableBoolean>
        booleanProjectConfigsBuilder();

    public Builder useContributorAgreements(InheritableBoolean useContributorAgreements) {
      booleanProjectConfigsBuilder()
          .put(BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS, useContributorAgreements);
      return this;
    }

    public Builder useContributorAgreements() {
      return useContributorAgreements(InheritableBoolean.TRUE);
    }

    public Builder useSignedOffBy(InheritableBoolean useSignedOffBy) {
      booleanProjectConfigsBuilder().put(BooleanProjectConfig.USE_SIGNED_OFF_BY, useSignedOffBy);
      return this;
    }

    public Builder useSignedOffBy() {
      return useSignedOffBy(InheritableBoolean.TRUE);
    }

    public Builder useContentMerge(InheritableBoolean useContentMerge) {
      booleanProjectConfigsBuilder().put(BooleanProjectConfig.USE_CONTENT_MERGE, useContentMerge);
      return this;
    }

    public Builder useContentMerge() {
      return useContentMerge(InheritableBoolean.TRUE);
    }

    public Builder requireChangeId(InheritableBoolean requireChangeId) {
      booleanProjectConfigsBuilder().put(BooleanProjectConfig.REQUIRE_CHANGE_ID, requireChangeId);
      return this;
    }

    public Builder requireChangeId() {
      return requireChangeId(InheritableBoolean.TRUE);
    }

    public Builder createNewChangeForAllNotInTarget(
        InheritableBoolean createNewChangeForAllNotInTarget) {
      booleanProjectConfigsBuilder()
          .put(
              BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET,
              createNewChangeForAllNotInTarget);
      return this;
    }

    public Builder createNewChangeForAllNotInTarget() {
      return createNewChangeForAllNotInTarget(InheritableBoolean.TRUE);
    }

    public Builder enableSignedPush(InheritableBoolean enableSignedPush) {
      booleanProjectConfigsBuilder().put(BooleanProjectConfig.ENABLE_SIGNED_PUSH, enableSignedPush);
      return this;
    }

    public Builder enableSignedPush() {
      return enableSignedPush(InheritableBoolean.TRUE);
    }

    public Builder requireSignedPush(InheritableBoolean requireSignedPush) {
      booleanProjectConfigsBuilder()
          .put(BooleanProjectConfig.REQUIRE_SIGNED_PUSH, requireSignedPush);
      return this;
    }

    public Builder requireSignedPush() {
      return requireSignedPush(InheritableBoolean.TRUE);
    }

    public Builder rejectImplicitMerges(InheritableBoolean rejectImplicitMerges) {
      booleanProjectConfigsBuilder()
          .put(BooleanProjectConfig.REJECT_IMPLICIT_MERGES, rejectImplicitMerges);
      return this;
    }

    public Builder rejectImplicitMerges() {
      return rejectImplicitMerges(InheritableBoolean.TRUE);
    }

    public Builder privateByDefault(InheritableBoolean privateByDefault) {
      booleanProjectConfigsBuilder().put(BooleanProjectConfig.PRIVATE_BY_DEFAULT, privateByDefault);
      return this;
    }

    public Builder privateByDefault() {
      return privateByDefault(InheritableBoolean.TRUE);
    }

    public Builder enableReviewerByEmail(InheritableBoolean enableReviewerByEmail) {
      booleanProjectConfigsBuilder()
          .put(BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL, enableReviewerByEmail);
      return this;
    }

    public Builder enableReviewerByEmail() {
      return enableReviewerByEmail(InheritableBoolean.TRUE);
    }

    public Builder matchAuthorToCommitterDate(InheritableBoolean matchAuthorToCommitterDate) {
      booleanProjectConfigsBuilder()
          .put(BooleanProjectConfig.MATCH_AUTHOR_TO_COMMITTER_DATE, matchAuthorToCommitterDate);
      return this;
    }

    public Builder matchAuthorToCommitterDate() {
      return matchAuthorToCommitterDate(InheritableBoolean.TRUE);
    }

    public Builder rejectEmptyCommit(InheritableBoolean rejectEmptyCommit) {
      booleanProjectConfigsBuilder()
          .put(BooleanProjectConfig.REJECT_EMPTY_COMMIT, rejectEmptyCommit);
      return this;
    }

    public Builder rejectEmptyCommit() {
      return rejectEmptyCommit(InheritableBoolean.TRUE);
    }

    public Builder workInProgressByDefault(InheritableBoolean workInProgressByDefault) {
      booleanProjectConfigsBuilder()
          .put(BooleanProjectConfig.WORK_IN_PROGRESS_BY_DEFAULT, workInProgressByDefault);
      return this;
    }

    public Builder workInProgressByDefault() {
      return workInProgressByDefault(InheritableBoolean.TRUE);
    }

    public Builder skipAddingAuthorAndCommitterAsReviewers(
        InheritableBoolean skipAddingAuthorAndCommitterAsReviewers) {
      booleanProjectConfigsBuilder()
          .put(
              BooleanProjectConfig.SKIP_ADDING_AUTHOR_AND_COMMITTER_AS_REVIEWERS,
              skipAddingAuthorAndCommitterAsReviewers);
      return this;
    }

    public Builder skipAddingAuthorAndCommitterAsReviewers() {
      return skipAddingAuthorAndCommitterAsReviewers(InheritableBoolean.TRUE);
    }

    /**
     * Removes all access sections. Useful when testing against a specific set of access sections or
     * permissions.
     */
    @CanIgnoreReturnValue
    public Builder removeAllAccessSections() {
      return removeAllAccessSections(true);
    }

    /** Adds a permission to be included in this update. */
    @CanIgnoreReturnValue
    public Builder add(TestPermission testPermission) {
      addedPermissionsBuilder().add(testPermission);
      return this;
    }

    /** Adds a permission to be included in this update. */
    @CanIgnoreReturnValue
    public Builder add(TestPermission.Builder testPermissionBuilder) {
      return add(testPermissionBuilder.build());
    }

    /** Adds a label permission to be included in this update. */
    @CanIgnoreReturnValue
    public Builder add(TestLabelPermission testLabelPermission) {
      addedLabelPermissionsBuilder().add(testLabelPermission);
      return this;
    }

    /** Adds a label permission to be included in this update. */
    @CanIgnoreReturnValue
    public Builder add(TestLabelPermission.Builder testLabelPermissionBuilder) {
      return add(testLabelPermissionBuilder.build());
    }

    /** Adds a capability to be included in this update. */
    @CanIgnoreReturnValue
    public Builder add(TestCapability testCapability) {
      addedCapabilitiesBuilder().add(testCapability);
      return this;
    }

    /** Adds a capability to be included in this update. */
    @CanIgnoreReturnValue
    public Builder add(TestCapability.Builder testCapabilityBuilder) {
      return add(testCapabilityBuilder.build());
    }

    /** Removes a permission, label permission, or capability as part of this update. */
    @CanIgnoreReturnValue
    public Builder remove(TestPermissionKey testPermissionKey) {
      removedPermissionsBuilder().add(testPermissionKey);
      return this;
    }

    /** Removes a permission, label permission, or capability as part of this update. */
    @CanIgnoreReturnValue
    public Builder remove(TestPermissionKey.Builder testPermissionKeyBuilder) {
      return remove(testPermissionKeyBuilder.build());
    }

    /** Sets the exclusive bit bit for the given permission key. */
    @CanIgnoreReturnValue
    public Builder setExclusiveGroup(
        TestPermissionKey.Builder testPermissionKeyBuilder, boolean exclusive) {
      return setExclusiveGroup(testPermissionKeyBuilder.build(), exclusive);
    }

    /** Sets the exclusive bit bit for the given permission key. */
    @CanIgnoreReturnValue
    public Builder setExclusiveGroup(TestPermissionKey testPermissionKey, boolean exclusive) {
      checkArgument(
          !testPermissionKey.group().isPresent(),
          "do not specify group for setExclusiveGroup: %s",
          testPermissionKey);
      checkArgument(
          !testPermissionKey.section().equals(GLOBAL_CAPABILITIES),
          "setExclusiveGroup not valid for global capabilities: %s",
          testPermissionKey);
      exclusiveGroupPermissionsBuilder().put(testPermissionKey, exclusive);
      return this;
    }

    abstract Builder projectUpdater(ThrowingConsumer<TestProjectUpdate> projectUpdater);

    abstract TestProjectUpdate autoBuild();

    TestProjectUpdate build() {
      TestProjectUpdate projectUpdate = autoBuild();
      if (projectUpdate.hasCapabilityUpdates()) {
        checkArgument(
            projectUpdate.nameKey().equals(projectUpdate.allProjectsName()),
            "cannot update global capabilities on %s, only %s: %s",
            projectUpdate.nameKey(),
            projectUpdate.allProjectsName(),
            projectUpdate);
      }
      return projectUpdate;
    }

    /** Executes the update, updating the underlying project. */
    public void update() {
      TestProjectUpdate projectUpdate = build();
      projectUpdate.projectUpdater().acceptAndThrowSilently(projectUpdate);
    }
  }

  abstract Project.NameKey nameKey();

  abstract AllProjectsName allProjectsName();

  abstract ImmutableList<TestPermission> addedPermissions();

  abstract ImmutableList<TestLabelPermission> addedLabelPermissions();

  abstract ImmutableList<TestCapability> addedCapabilities();

  abstract ImmutableList<TestPermissionKey> removedPermissions();

  abstract ImmutableMap<TestPermissionKey, Boolean> exclusiveGroupPermissions();

  abstract ThrowingConsumer<TestProjectUpdate> projectUpdater();

  abstract boolean removeAllAccessSections();

  abstract ImmutableMap<BooleanProjectConfig, InheritableBoolean> booleanProjectConfigs();

  boolean hasCapabilityUpdates() {
    return !addedCapabilities().isEmpty()
        || removedPermissions().stream().anyMatch(k -> k.section().equals(GLOBAL_CAPABILITIES));
  }

  private static void checkLabelName(String name) {
    // "label-Code-Review" is technically a valid label name, and we don't prevent users from
    // using it in production, but specifying it in a test is programmer error.
    checkArgument(!Permission.isLabel(name), "expected label name, got permission name: %s", name);
    LabelType.checkName(name);
  }

  private static void checkNonInvertedRange(int min, int max) {
    checkArgument(min <= max, "inverted range: %s > %s", min, max);
  }
}
