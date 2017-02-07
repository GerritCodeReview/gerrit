// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.reviewdb.client.RefNames.REFS_DASHBOARDS;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UrlEncoded;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gson.annotations.SerializedName;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

@Singleton
class DashboardsCollection
    implements ChildCollection<ProjectResource, DashboardResource>, AcceptsCreate<ProjectResource> {
  private final GitRepositoryManager gitManager;
  private final DynamicMap<RestView<DashboardResource>> views;
  private final Provider<ListDashboards> list;
  private final Provider<SetDefaultDashboard.CreateDefault> createDefault;

  @Inject
  DashboardsCollection(
      GitRepositoryManager gitManager,
      DynamicMap<RestView<DashboardResource>> views,
      Provider<ListDashboards> list,
      Provider<SetDefaultDashboard.CreateDefault> createDefault) {
    this.gitManager = gitManager;
    this.views = views;
    this.list = list;
    this.createDefault = createDefault;
  }

  @Override
  public RestView<ProjectResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @SuppressWarnings("unchecked")
  @Override
  public RestModifyView<ProjectResource, ?> create(ProjectResource parent, IdString id)
      throws RestApiException {
    if (id.toString().equals("default")) {
      return createDefault.get();
    }
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DashboardResource parse(ProjectResource parent, IdString id)
      throws ResourceNotFoundException, IOException, ConfigInvalidException {
    ProjectControl myCtl = parent.getControl();
    if (id.toString().equals("default")) {
      return DashboardResource.projectDefault(myCtl);
    }

    List<String> parts = Lists.newArrayList(Splitter.on(':').limit(2).split(id.get()));
    if (parts.size() != 2) {
      throw new ResourceNotFoundException(id);
    }

    CurrentUser user = myCtl.getUser();
    String ref = parts.get(0);
    String path = parts.get(1);
    for (ProjectState ps : myCtl.getProjectState().tree()) {
      try {
        return parse(ps.controlFor(user), ref, path, myCtl);
      } catch (AmbiguousObjectException | ConfigInvalidException | IncorrectObjectTypeException e) {
        throw new ResourceNotFoundException(id);
      } catch (ResourceNotFoundException e) {
        continue;
      }
    }
    throw new ResourceNotFoundException(id);
  }

  private DashboardResource parse(ProjectControl ctl, String ref, String path, ProjectControl myCtl)
      throws ResourceNotFoundException, IOException, AmbiguousObjectException,
          IncorrectObjectTypeException, ConfigInvalidException {
    String id = ref + ":" + path;
    if (!ref.startsWith(REFS_DASHBOARDS)) {
      ref = REFS_DASHBOARDS + ref;
    }
    if (!Repository.isValidRefName(ref) || !ctl.controlForRef(ref).canRead()) {
      throw new ResourceNotFoundException(id);
    }

    try (Repository git = gitManager.openRepository(ctl.getProject().getNameKey())) {
      ObjectId objId = git.resolve(ref + ":" + path);
      if (objId == null) {
        throw new ResourceNotFoundException(id);
      }
      BlobBasedConfig cfg = new BlobBasedConfig(null, git, objId);
      return new DashboardResource(myCtl, ref, path, cfg, false);
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(id);
    }
  }

  @Override
  public DynamicMap<RestView<DashboardResource>> views() {
    return views;
  }

  static DashboardInfo parse(
      Project definingProject,
      String refName,
      String path,
      Config config,
      String project,
      boolean setDefault) {
    DashboardInfo info = new DashboardInfo(refName, path);
    info.project = project;
    info.definingProject = definingProject.getName();
    String query = config.getString("dashboard", null, "title");
    info.title = replace(project, query == null ? info.path : query);
    info.description = replace(project, config.getString("dashboard", null, "description"));
    info.foreach = config.getString("dashboard", null, "foreach");

    if (setDefault) {
      String id = refName + ":" + path;
      info.isDefault = id.equals(defaultOf(definingProject)) ? true : null;
    }

    UrlEncoded u = new UrlEncoded("/dashboard/");
    u.put("title", MoreObjects.firstNonNull(info.title, info.path));
    if (info.foreach != null) {
      u.put("foreach", replace(project, info.foreach));
    }
    for (String name : config.getSubsections("section")) {
      Section s = new Section();
      s.name = name;
      s.query = config.getString("section", name, "query");
      u.put(s.name, replace(project, s.query));
      info.sections.add(s);
    }
    info.url = u.toString().replace("%3A", ":");

    return info;
  }

  private static String replace(String project, String query) {
    return query.replace("${project}", project);
  }

  private static String defaultOf(Project proj) {
    final String defaultId =
        MoreObjects.firstNonNull(
            proj.getLocalDefaultDashboard(), Strings.nullToEmpty(proj.getDefaultDashboard()));
    if (defaultId.startsWith(REFS_DASHBOARDS)) {
      return defaultId.substring(REFS_DASHBOARDS.length());
    }
    return defaultId;
  }

  static class DashboardInfo {
    String id;
    String project;
    String definingProject;
    String ref;
    String path;
    String description;
    String foreach;
    String url;

    @SerializedName("default")
    Boolean isDefault;

    String title;
    List<Section> sections = new ArrayList<>();

    DashboardInfo(String ref, String name) {
      this.ref = ref;
      this.path = name;
      this.id = Joiner.on(':').join(Url.encode(ref), Url.encode(path));
    }
  }

  static class Section {
    String name;
    String query;
  }
}
