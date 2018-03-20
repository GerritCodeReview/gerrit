// Copyright (C) 2018 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.simple.submit.config;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class ConfigServlet
    implements RestReadView<ProjectResource>, RestModifyView<ProjectResource, SubmitConfig> {
  private final ProjectCache projectCache;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final MetaDataUpdate.User metaDataUpdateFactory;

  @Inject
  ConfigServlet(
      ProjectCache projectCache,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      MetaDataUpdate.User metaDataUpdateFactory) {
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
  }

  @Override
  public Object apply(ProjectResource resource) throws AuthException, PermissionBackendException {
    permissionBackend.user(user).project(resource.getNameKey()).check(ProjectPermission.ACCESS);
    resource.getProjectState().getConfig();
    return new SubmitConfig();
  }

  @Override
  public Object apply(ProjectResource resource, SubmitConfig input)
      throws PermissionBackendException, ResourceConflictException, AuthException, IOException {
    PermissionBackend.ForProject projectPermissions =
        permissionBackend.user(user).project(resource.getNameKey());
    projectPermissions.check(ProjectPermission.ACCESS);
    projectPermissions.check(ProjectPermission.WRITE_CONFIG);

    try (MetaDataUpdate md =
        metaDataUpdateFactory.create(resource.getNameKey(), user.get().asIdentifiedUser())) {

      resource.getProjectState().getConfig().getLabelSections().remove("Test");
      resource.getProjectState().getConfig().commit(md);
      projectCache.evict(resource.getNameKey());
    }
    System.out.println(resource);
    System.out.println(user.get());

    System.out.println(input);
    return input;
  }
}
