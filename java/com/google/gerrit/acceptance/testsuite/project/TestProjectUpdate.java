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

import com.google.auto.value.AutoValue;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.testing.Util;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@AutoValue
public abstract class TestProjectUpdate {
  public static TestPermission.Builder allow(String name) {
    return TestPermission.builder().name(name).action(PermissionRule.Action.ALLOW);
  }

  public static TestPermission.Builder block(String name) {
    return TestPermission.builder().name(name).action(PermissionRule.Action.BLOCK);
  }

  @AutoValue
  public abstract static class TestPermission {
    private static Builder builder() {
      return new AutoValue_TestProjectUpdate_TestPermission.Builder().force(false).min(0).max(0);
    }

    public abstract String name();

    public abstract String ref();

    public abstract AccountGroup.UUID group();

    public abstract PermissionRule.Action action();

    public abstract boolean force();

    public abstract int min();

    public abstract int max();

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder name(String name);

      public abstract Builder ref(String ref);

      public abstract Builder group(AccountGroup.UUID groupUuid);

      public abstract Builder action(PermissionRule.Action action);

      public abstract Builder force(boolean force);

      public abstract Builder min(int min);

      public abstract Builder max(int max);

      public abstract TestPermission build();
    }
  }

  abstract Consumer<ProjectConfig> configModification();

  abstract ThrowingConsumer<TestProjectUpdate> projectUpdater();

  static Builder builder(ThrowingConsumer<TestProjectUpdate> projectUpdater) {
    return new AutoValue_TestProjectUpdate.Builder()
        .projectUpdater(projectUpdater)
        .configModification(in -> {});
  }

  @AutoValue.Builder
  public abstract static class Builder {
    abstract Builder configModification(Consumer<ProjectConfig> configModification);

    abstract Consumer<ProjectConfig> configModification();

    abstract Builder projectUpdater(ThrowingConsumer<TestProjectUpdate> projectUpdater);

    Builder addConfigModification(Consumer<ProjectConfig> configModification) {
      return configModification(configModification().andThen(configModification));
    }

    public Builder add(TestPermission.Builder permissionBuilder) {
      TestPermission p = permissionBuilder.build();
      return addPermission(
          p.name(),
          p.ref(),
          p.group(),
          (projectConfig, rule) -> {
            rule.setAction(p.action());
            rule.setForce(p.force());
            rule.setMin(p.min());
            rule.setMax(p.max());
            rule.setGroup(projectConfig.resolve(new GroupReference(p.group(), p.group().get())));
          });
    }

    // TODO(dborowitz): If we prefer the add(...) construction, these variants will go away.
    public Builder addPermission(
        String permissionName,
        String ref,
        AccountGroup.UUID groupUuid,
        BiConsumer<ProjectConfig, PermissionRule> initRule) {
      return addConfigModification(
          projectConfig -> addRule(projectConfig, permissionName, ref, groupUuid, initRule));
    }

    public Builder addPermission(String permissionName, String ref, AccountGroup.UUID groupUuid) {
      return addPermission(permissionName, ref, groupUuid, (pc, r) -> {});
    }

    public Builder removePermission(
        String permissionName, String ref, AccountGroup.UUID groupUuid) {
      return addConfigModification(
          projectConfig -> removeRule(projectConfig, permissionName, ref, groupUuid));
    }

    abstract TestProjectUpdate autoBuild();

    public void update() {
      TestProjectUpdate projectUpdate = autoBuild();
      projectUpdate.projectUpdater().acceptAndThrowSilently(projectUpdate);
    }
  }

  private static void addRule(
      ProjectConfig projectConfig,
      String permissionName,
      String ref,
      AccountGroup.UUID groupUuid,
      BiConsumer<ProjectConfig, PermissionRule> initRule) {
    PermissionRule rule = Util.newRule(projectConfig, groupUuid);
    initRule.accept(projectConfig, rule);
    projectConfig.getAccessSection(ref, true).getPermission(permissionName, true).add(rule);
  }

  private static void removeRule(
      ProjectConfig projectConfig, String permissionName, String ref, AccountGroup.UUID groupUuid) {
    GroupReference group = projectConfig.resolve(new GroupReference(groupUuid, groupUuid.get()));
    projectConfig
        .getAccessSection(ref, true)
        .getPermission(permissionName, true)
        .remove(new PermissionRule(group));
  }
}
