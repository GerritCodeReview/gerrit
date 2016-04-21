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

package com.google.gerrit.server.access;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.GetAccess;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ListAccess implements RestReadView<TopLevelResource> {

  @Option(name = "--project", aliases = {"-p"}, metaVar = "PROJECT",
      usage = "projects for which the access rights should be returned")
  private List<String> projects = Lists.newArrayList();

  private final Provider<CurrentUser> self;
  private final ProjectControl.GenericFactory projectControlFactory;
  private final ProjectCache projectCache;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final GroupControl.Factory groupControlFactory;
  private final ProjectJson projectJson;
  private final GroupBackend groupBackend;
  private final AllProjectsName allProjectsName;

  @Inject
  public ListAccess(Provider<CurrentUser> self,
      ProjectControl.GenericFactory projectControlFactory,
      ProjectCache projectCache, MetaDataUpdate.Server metaDataUpdateFactory,
      GroupControl.Factory groupControlFactory, GroupBackend groupBackend,
      AllProjectsName allProjectsName, ProjectJson projectJson) {
    this.self = self;
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.groupControlFactory = groupControlFactory;
    this.groupBackend = groupBackend;
    this.allProjectsName = allProjectsName;
    this.projectJson =  projectJson;
  }

  @Override
  public Map<String, ProjectAccessInfo> apply(TopLevelResource resource)
      throws ResourceNotFoundException, ResourceConflictException, IOException {
    Map<String, ProjectAccessInfo> access = Maps.newTreeMap();
    for (String p: projects) {
      // Load the current configuration from the repository, ensuring it's the most
      // recent version available. If it differs from what was in the project
      // state, force a cache flush now.
      //
      Project.NameKey projectName = new Project.NameKey(p);
      try (MetaDataUpdate md = metaDataUpdateFactory.create(projectName)) {
        ProjectControl pc = open(projectName);
        ProjectConfig config = ProjectConfig.read(md);

        if (config.updateGroupNames(groupBackend)) {
          md.setMessage("Update group names\n");
          config.commit(md);
          projectCache.evict(config.getProject());
          pc = open(projectName);
        } else if (config.getRevision() != null
            && !config.getRevision().equals(
                pc.getProjectState().getConfig().getRevision())) {
          projectCache.evict(config.getProject());
          pc = open(projectName);
        }
        GetAccess ga = new GetAccess(groupControlFactory, allProjectsName, projectJson);
        access.put(p, ga.apply(new ProjectResource(pc)));
      } catch (ConfigInvalidException e) {
        throw new ResourceConflictException(e.getMessage());
      } catch (RepositoryNotFoundException e) {
        throw new ResourceNotFoundException(p);
      }
    }
    return access;
  }

  private ProjectControl open(Project.NameKey projectName)
      throws ResourceNotFoundException, IOException {
    try {
      return projectControlFactory.validateFor(projectName,
          ProjectControl.OWNER | ProjectControl.VISIBLE, self.get());
    } catch (NoSuchProjectException e) {
      throw new ResourceNotFoundException(projectName.get());
    }
  }

}
