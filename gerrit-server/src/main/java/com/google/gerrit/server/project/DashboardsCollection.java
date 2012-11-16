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

import static com.google.gerrit.server.git.GitRepositoryManager.REFS_DASHBOARDS;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.UrlEncoded;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gson.annotations.SerializedName;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

class DashboardsCollection implements
    ChildCollection<ProjectResource, DashboardResource> {
  private final GitRepositoryManager gitManager;
  private final DynamicMap<RestView<DashboardResource>> views;
  private final Provider<ListDashboards> list;

  @Inject
  DashboardsCollection(GitRepositoryManager gitManager,
      DynamicMap<RestView<DashboardResource>> views,
      Provider<ListDashboards> list) {
    this.gitManager = gitManager;
    this.views = views;
    this.list = list;
  }

  @Override
  public RestView<ProjectResource> list() throws ResourceNotFoundException {
    return list.get();
  }

  @Override
  public DashboardResource parse(ProjectResource parent, String id)
      throws ResourceNotFoundException, Exception {
    ProjectControl ctl = parent.getControl();
    if ("default".equals(id)) {
      id = defaultOf(ctl.getProject());
    }

    List<String> parts = Lists.newArrayList(
        Splitter.on(':').limit(2).split(id));
    if (parts.size() != 2) {
      throw new ResourceNotFoundException(id);
    }

    String ref = URLDecoder.decode(parts.get(0), "UTF-8");
    String path = URLDecoder.decode(parts.get(1), "UTF-8");
    if (!ref.startsWith(REFS_DASHBOARDS)) {
      ref = REFS_DASHBOARDS + ref;
    }
    if (!Repository.isValidRefName(ref)
        || !ctl.controlForRef(ref).canRead()) {
      throw new ResourceNotFoundException(id);
    }

    Repository git;
    try {
      git = gitManager.openRepository(parent.getNameKey());
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(id);
    }
    try {
      ObjectId objId;
      try {
        objId = git.resolve(Joiner.on(':').join(ref, path));
      } catch (AmbiguousObjectException e) {
        throw new ResourceNotFoundException(id);
      } catch (IncorrectObjectTypeException e) {
        throw new ResourceNotFoundException(id);
      }
      if (objId == null) {
        throw new ResourceNotFoundException();
      }
      BlobBasedConfig cfg = new BlobBasedConfig(null, git, objId);
      return new DashboardResource(ctl, ref, path, objId, cfg);
    } finally {
      git.close();
    }
  }

  @Override
  public DynamicMap<RestView<DashboardResource>> views() {
    return views;
  }

  static DashboardInfo parse(Project project, String refName, String path,
      Config config) throws UnsupportedEncodingException {
    DashboardInfo info = new DashboardInfo(refName, path);
    info.title = config.getString("dashboard", null, "title");
    info.description = config.getString("dashboard", null, "description");
    info.isDefault = info.id.equals(defaultOf(project)) ? true : null;

    UrlEncoded u = new UrlEncoded("/dashboard/");
    u.put("title", Objects.firstNonNull(info.title, info.path));
    for (String name : config.getSubsections("section")) {
      Section s = new Section();
      s.name = name;
      s.query = config.getString("section", name, "query");
      u.put(s.name, replace(project.getName(), s.query));
      info.sections.add(s);
    }
    info.url = u.toString().replace("%3A", ":");

    return info;
  }

  private static String replace(String project, String query) {
    return query.replace("${project}", project);
  }

  private static String defaultOf(Project proj) {
    return Objects.firstNonNull(
        proj.getLocalDefaultDashboard(),
        Strings.nullToEmpty(proj.getDefaultDashboard()));
  }

  static class DashboardInfo {
    final String kind = "gerritcodereview#dashboard";
    String id;
    String project;
    String ref;
    String path;
    String description;
    String url;

    @SerializedName("default")
    Boolean isDefault;

    String title;
    List<Section> sections = Lists.newArrayList();

    DashboardInfo(String ref, String name)
        throws UnsupportedEncodingException {
      this.ref = ref;
      this.path = name;
      this.id = Joiner.on(':').join(
          URLEncoder.encode(ref,"UTF-8"),
          URLEncoder.encode(path, "UTF-8"));
    }
  }

  static class Section {
    String name;
    String query;
  }
}
