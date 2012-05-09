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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ProjectAccessHandler<T> extends Handler<T> {

  private final ProjectControl.Factory projectControlFactory;
  protected final GroupCache groupCache;
  private final MetaDataUpdate.User metaDataUpdateFactory;

  protected final Project.NameKey projectName;
  protected final ObjectId base;
  private List<AccessSection> sectionList;
  protected String message;
  private boolean checkIfOwner;

  protected ProjectAccessHandler(
      final ProjectControl.Factory projectControlFactory,
      final GroupCache groupCache,
      final MetaDataUpdate.User metaDataUpdateFactory,
      final Project.NameKey projectName, final ObjectId base,
      final List<AccessSection> sectionList, final String message,
      final boolean checkIfOwner) {
    this.projectControlFactory = projectControlFactory;
    this.groupCache = groupCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;

    this.projectName = projectName;
    this.base = base;
    this.sectionList = sectionList;
    this.message = message;
    this.checkIfOwner = checkIfOwner;
  }

  @Override
  public final T call() throws NoSuchProjectException, IOException,
      ConfigInvalidException, InvalidNameException, NoSuchGroupException,
      OrmException {
    final ProjectControl projectControl =
        projectControlFactory.controlFor(projectName);

    final MetaDataUpdate md;
    try {
      md = metaDataUpdateFactory.create(projectName);
    } catch (RepositoryNotFoundException notFound) {
      throw new NoSuchProjectException(projectName);
    }
    try {
      ProjectConfig config = ProjectConfig.read(md, base);
      Set<String> toDelete = scanSectionNames(config);

      for (AccessSection section : mergeSections(sectionList)) {
        String name = section.getName();

        if (AccessSection.GLOBAL_CAPABILITIES.equals(name)) {
          if (checkIfOwner && !projectControl.isOwner()) {
            continue;
          }
          replace(config, toDelete, section);

        } else if (AccessSection.isValid(name)) {
          if (checkIfOwner && !projectControl.controlForRef(name).isOwner()) {
            continue;
          }

          RefControl.validateRefPattern(name);

          replace(config, toDelete, section);
        }
      }

      for (String name : toDelete) {
        if (AccessSection.GLOBAL_CAPABILITIES.equals(name)) {
          if (!checkIfOwner || projectControl.isOwner()) {
            config.remove(config.getAccessSection(name));
          }

        } else if (!checkIfOwner ||  projectControl.controlForRef(name).isOwner()) {
          config.remove(config.getAccessSection(name));
        }
      }

      if (message != null && !message.isEmpty()) {
        if (!message.endsWith("\n")) {
          message += "\n";
        }
        md.setMessage(message);
      } else {
        md.setMessage("Modify access rules\n");
      }

      return updateProjectConfig(config, md);
    } finally {
      md.close();
    }
  }

  protected abstract T updateProjectConfig(ProjectConfig config,
      MetaDataUpdate md) throws IOException, NoSuchProjectException,
      ConfigInvalidException, OrmException;

  private void replace(ProjectConfig config, Set<String> toDelete,
      AccessSection section) throws NoSuchGroupException {
    for (Permission permission : section.getPermissions()) {
      for (PermissionRule rule : permission.getRules()) {
        lookupGroup(rule);
      }
    }
    config.replace(section);
    toDelete.remove(section.getName());
  }

  private static List<AccessSection> mergeSections(List<AccessSection> src) {
    Map<String, AccessSection> map = new LinkedHashMap<String, AccessSection>();
    for (AccessSection section : src) {
      if (section.getPermissions().isEmpty()) {
        continue;
      }

      AccessSection prior = map.get(section.getName());
      if (prior != null) {
        prior.mergeFrom(section);
      } else {
        map.put(section.getName(), section);
      }
    }
    return new ArrayList<AccessSection>(map.values());
  }

  private static Set<String> scanSectionNames(ProjectConfig config) {
    Set<String> names = new HashSet<String>();
    for (AccessSection section : config.getAccessSections()) {
      names.add(section.getName());
    }
    return names;
  }

  private void lookupGroup(PermissionRule rule) throws NoSuchGroupException {
    GroupReference ref = rule.getGroup();
    if (ref.getUUID() == null) {
      AccountGroup.NameKey name = new AccountGroup.NameKey(ref.getName());
      AccountGroup group = groupCache.get(name);
      if (group == null) {
        throw new NoSuchGroupException(name);
      }
      ref.setUUID(group.getGroupUUID());
    }
  }
}
