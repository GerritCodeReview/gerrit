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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;

@AutoValue
public abstract class TestProjectUpdate {
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
    return TestLabelPermission.builder().name(name).action(PermissionRule.Action.ALLOW);
  }

  /** Starts a builder for denying a label permission. */
  public static TestLabelPermission.Builder blockLabel(String name) {
    return TestLabelPermission.builder().name(name).action(PermissionRule.Action.BLOCK);
  }

  /** Records a label permission to be updated. */
  @AutoValue
  public abstract static class TestLabelPermission {
    private static Builder builder() {
      return new AutoValue_TestProjectUpdate_TestLabelPermission.Builder().exclusive(false);
    }

    abstract String name();

    abstract String ref();

    abstract AccountGroup.UUID group();

    abstract PermissionRule.Action action();

    abstract int min();

    abstract int max();

    abstract boolean exclusive();

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

      /** Sets the minimum and maximum values for the permission. */
      public Builder range(int min, int max) {
        return min(min).max(max);
      }

      /** Adds the permission to the exclusive group permission set on the access section. */
      public abstract Builder exclusive(boolean exclusive);

      abstract TestLabelPermission autoBuild();

      /** Builds the {@link TestPermission}. */
      public TestLabelPermission build() {
        TestLabelPermission result = autoBuild();
        checkArgument(
            !Permission.isLabel(result.name()),
            "expected label name, got permission name: %s",
            result.name());
        LabelType.checkName(result.name());
        return result;
      }
    }
  }

  static Builder builder(ThrowingConsumer<TestProjectUpdate> projectUpdater) {
    return new AutoValue_TestProjectUpdate.Builder().projectUpdater(projectUpdater);
  }

  /** Builder for {@link TestProjectUpdate}. */
  @AutoValue.Builder
  public abstract static class Builder {
    abstract ImmutableList.Builder<TestPermission> addedPermissionsBuilder();

    abstract ImmutableList.Builder<TestLabelPermission> addedLabelPermissionsBuilder();

    /** Adds a permission to be included in this update. */
    public Builder add(TestPermission testPermission) {
      addedPermissionsBuilder().add(testPermission);
      return this;
    }

    /** Adds a permission to be included in this update. */
    public Builder add(TestPermission.Builder testPermissionBuilder) {
      return add(testPermissionBuilder.build());
    }

    /** Adds a label permission to be included in this update. */
    public Builder add(TestLabelPermission testLabelPermission) {
      addedLabelPermissionsBuilder().add(testLabelPermission);
      return this;
    }

    /** Adds a label permission to be included in this update. */
    public Builder add(TestLabelPermission.Builder testLabelPermissionBuilder) {
      return add(testLabelPermissionBuilder.build());
    }

    abstract Builder projectUpdater(ThrowingConsumer<TestProjectUpdate> projectUpdater);

    abstract TestProjectUpdate autoBuild();

    /** Executes the update, updating the underlying project. */
    public void update() {
      TestProjectUpdate projectUpdate = autoBuild();
      projectUpdate.projectUpdater().acceptAndThrowSilently(projectUpdate);
    }
  }

  abstract ImmutableList<TestPermission> addedPermissions();

  abstract ImmutableList<TestLabelPermission> addedLabelPermissions();

  abstract ThrowingConsumer<TestProjectUpdate> projectUpdater();
}
