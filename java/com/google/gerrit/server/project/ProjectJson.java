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

package com.google.gerrit.server.project;

import static java.util.stream.Collectors.toMap;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.RoleSection;
import com.google.gerrit.common.data.RoleSection.RolePermission;
import com.google.gerrit.extensions.common.LabelTypeInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.common.RolePermissionInfo;
import com.google.gerrit.extensions.common.RoleTypeInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.WebLinks;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ProjectJson {

  private final AllProjectsName allProjects;
  private final WebLinks webLinks;

  @Inject
  ProjectJson(AllProjectsName allProjectsName, WebLinks webLinks) {
    this.allProjects = allProjectsName;
    this.webLinks = webLinks;
  }

  public ProjectInfo format(ProjectState projectState) {
    ProjectInfo info = format(projectState.getProject());
    info.labels = new HashMap<>();
    for (LabelType t : projectState.getLabelTypes().getLabelTypes()) {
      LabelTypeInfo labelInfo = new LabelTypeInfo();
      labelInfo.values =
          t.getValues().stream().collect(toMap(LabelValue::formatValue, LabelValue::getText));
      labelInfo.defaultValue = t.getDefaultValue();
      info.labels.put(t.getName(), labelInfo);
    }
    Map<String, RoleTypeInfo> roles = null;
    for (RoleSection rs : projectState.getAllRoleSections().values()) {
      if (roles == null) roles = new HashMap<>();
      RoleTypeInfo roleInfo = new RoleTypeInfo();
      roleInfo.permissions = new ArrayList<>();
      for (RolePermission rp : rs.getRolePermissions()) {
        RolePermissionInfo rpi = new RolePermissionInfo();
        rpi.permission = rp.getPermissionName();
        rpi.force = rp.getForce();
        rpi.range = rp.getRangeString() != null ? rp.getRangeString() : null;
        roleInfo.permissions.add(rpi);
      }
      roles.put(rs.getName(), roleInfo);
    }
    info.roles = roles;

    return info;
  }

  public ProjectInfo format(Project p) {
    ProjectInfo info = new ProjectInfo();
    info.name = p.getName();
    Project.NameKey parentName = p.getParent(allProjects);
    info.parent = parentName != null ? parentName.get() : null;
    info.description = Strings.emptyToNull(p.getDescription());
    info.state = p.getState();
    info.id = Url.encode(info.name);
    List<WebLinkInfo> links = webLinks.getProjectLinks(p.getName());
    info.webLinks = links.isEmpty() ? null : links;
    return info;
  }
}
