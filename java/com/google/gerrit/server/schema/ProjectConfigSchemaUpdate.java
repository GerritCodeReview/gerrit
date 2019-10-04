// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.gerrit.server.project.ProjectConfig.ACCESS;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;

public class ProjectConfigSchemaUpdate extends VersionedMetaData {
  public static class Factory {
    private final SitePaths sitePaths;
    private final AllProjectsName allProjectsName;

    @Inject
    Factory(SitePaths sitePaths, AllProjectsName allProjectsName) {
      this.sitePaths = sitePaths;
      this.allProjectsName = allProjectsName;
    }

    ProjectConfigSchemaUpdate read(MetaDataUpdate update)
        throws IOException, ConfigInvalidException {
      ProjectConfigSchemaUpdate r =
          new ProjectConfigSchemaUpdate(
              update,
              ProjectConfig.Factory.getBaseConfig(sitePaths, allProjectsName, allProjectsName));
      r.load(update);
      return r;
    }
  }

  private final MetaDataUpdate update;
  @Nullable private final StoredConfig baseConfig;
  private Config config;
  private boolean updated;

  private ProjectConfigSchemaUpdate(MetaDataUpdate update, @Nullable StoredConfig baseConfig) {
    this.update = update;
    this.baseConfig = baseConfig;
  }

  @Override
  protected String getRefName() {
    return RefNames.REFS_CONFIG;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (baseConfig != null) {
      baseConfig.load();
    }
    config = readConfig(ProjectConfig.PROJECT_CONFIG, baseConfig);
  }

  @VisibleForTesting
  Config getConfig() {
    return config;
  }

  public void removeForceFromPermission(String name) {
    for (String subsection : config.getSubsections(ACCESS)) {
      Set<String> names = config.getNames(ACCESS, subsection);
      if (names.contains(name)) {
        List<String> values =
            Arrays.stream(config.getStringList(ACCESS, subsection, name))
                .map(
                    r -> {
                      PermissionRule rule = PermissionRule.fromString(r, false);
                      if (rule.getForce()) {
                        rule.setForce(false);
                        updated = true;
                      }
                      return rule.asString(false);
                    })
                .collect(toList());
        config.setStringList(ACCESS, subsection, name, values);
      }
    }
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    saveConfig(ProjectConfig.PROJECT_CONFIG, config);
    return true;
  }

  public void save(PersonIdent personIdent, String commitMessage) {
    if (!updated) {
      return;
    }

    update.getCommitBuilder().setAuthor(personIdent);
    update.getCommitBuilder().setCommitter(personIdent);
    update.setMessage(commitMessage);
    try {
      commit(update);
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public boolean isUpdated() {
    return updated;
  }
}
