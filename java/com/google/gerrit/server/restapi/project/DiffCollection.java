// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.project.DiffFileResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * Collection for diffing commits at the project level.
 *
 * <p>Provides endpoints:
 *
 * <ul>
 *   <li>GET /projects/{project}/diff?old={sha1}&new={sha1} - list changed files
 *   <li>GET /projects/{project}/diff/{file}?old={sha1}&new={sha1} - get file diff
 * </ul>
 */
@Singleton
public class DiffCollection implements ChildCollection<ProjectResource, DiffFileResource> {
  private final DynamicMap<RestView<DiffFileResource>> views;
  private final Provider<ListDiffFiles> list;

  @Inject
  DiffCollection(DynamicMap<RestView<DiffFileResource>> views, Provider<ListDiffFiles> list) {
    this.views = views;
    this.list = list;
  }

  @Override
  public DynamicMap<RestView<DiffFileResource>> views() {
    return views;
  }

  @Override
  public RestView<ProjectResource> list() {
    return list.get();
  }

  @Override
  public DiffFileResource parse(ProjectResource parent, IdString id) {
    return new DiffFileResource(parent, id.get());
  }
}
