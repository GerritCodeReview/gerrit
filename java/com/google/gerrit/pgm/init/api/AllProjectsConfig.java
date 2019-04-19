// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.pgm.init.api;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.project.GroupList;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.StoredConfig;

public class AllProjectsConfig extends VersionedMetaDataOnInit {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Nullable private final StoredConfig baseConfig;
  private Config cfg;
  private GroupList groupList;

  @Inject
  AllProjectsConfig(AllProjectsNameOnInitProvider allProjects, SitePaths site, InitFlags flags) {
    super(flags, site, allProjects.get(), RefNames.REFS_CONFIG);
    this.baseConfig =
        ProjectConfig.Factory.getBaseConfig(
            site, new AllProjectsName(allProjects.get()), Project.nameKey(allProjects.get()));
  }

  public Config getConfig() {
    return cfg;
  }

  public GroupList getGroups() {
    return groupList;
  }

  @Override
  public AllProjectsConfig load() throws IOException, ConfigInvalidException {
    super.load();
    return this;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    if (baseConfig != null) {
      baseConfig.load();
    }
    groupList = readGroupList();
    cfg = readConfig(ProjectConfig.PROJECT_CONFIG, baseConfig);
  }

  private GroupList readGroupList() throws IOException {
    return GroupList.parse(
        Project.nameKey(project),
        readUTF8(GroupList.FILE_NAME),
        error ->
            logger.atSevere().log(
                "Error parsing file %s: %s", GroupList.FILE_NAME, error.getMessage()));
  }

  public void save(String pluginName, String message) throws IOException, ConfigInvalidException {
    save(
        new PersonIdent(pluginName, pluginName + "@gerrit"),
        "Update from plugin " + pluginName + ": " + message);
  }

  @Override
  protected void save(PersonIdent ident, String msg) throws IOException, ConfigInvalidException {
    super.save(ident, msg);

    // we need to invalidate the JGit cache if the group list is invalidated in
    // an unattended init step
    RepositoryCache.clear();
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    saveConfig(ProjectConfig.PROJECT_CONFIG, cfg);
    saveGroupList();
    return true;
  }

  private void saveGroupList() throws IOException {
    saveUTF8(GroupList.FILE_NAME, groupList.asText());
  }
}
