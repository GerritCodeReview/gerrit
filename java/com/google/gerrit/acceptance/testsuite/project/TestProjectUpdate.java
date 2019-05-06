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
import java.util.function.Consumer;

@AutoValue
public abstract class TestProjectUpdate {
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

    public Builder addPermission(
        String permissionName,
        String ref,
        AccountGroup.UUID groupUuid,
        Consumer<PermissionRule> initRule) {
      return addConfigModification(
          projectConfig -> addRule(projectConfig, permissionName, ref, groupUuid, initRule));
    }

    public Builder addPermission(String permissionName, String ref, AccountGroup.UUID groupUuid) {
      return addPermission(permissionName, ref, groupUuid, rule -> {});
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
      Consumer<PermissionRule> initRule) {
    PermissionRule rule = Util.newRule(projectConfig, groupUuid);
    initRule.accept(rule);
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
