// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.client.OrmConcurrencyException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

class ChangeProjectAccess extends Handler<ProjectAccess> {
  interface Factory {
    ChangeProjectAccess create(@Assisted Project.NameKey projectName,
        @Assisted ObjectId base, @Assisted List<AccessSection> sectionList,
        @Nullable @Assisted String message);
  }

  private final ProjectAccessFactory.Factory projectAccessFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final GroupCache groupCache;
  private final MetaDataUpdate.User metaDataUpdateFactory;

  private final Project.NameKey projectName;
  private final ObjectId base;
  private final List<AccessSection> sectionList;
  private String message;

  @Inject
  ChangeProjectAccess(final ProjectAccessFactory.Factory projectAccessFactory,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final GroupCache groupCache,
      final MetaDataUpdate.User metaDataUpdateFactory,

      @Assisted final Project.NameKey projectName,
      @Assisted final ObjectId base, @Assisted List<AccessSection> sectionList,
      @Nullable @Assisted String message) {
    this.projectAccessFactory = projectAccessFactory;
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.groupCache = groupCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;

    this.projectName = projectName;
    this.base = base;
    this.sectionList = sectionList;
    this.message = message;
  }

  @Override
  public ProjectAccess call() throws NoSuchProjectException, IOException,
      ConfigInvalidException, InvalidNameException, NoSuchGroupException,
      OrmConcurrencyException {
    final ProjectControl projectControl =
        projectControlFactory.controlFor(projectName);

    final MetaDataUpdate md;
    try {
      md = metaDataUpdateFactory.create(projectName);
    } catch (RepositoryNotFoundException notFound) {
      throw new NoSuchProjectException(projectName);
    }
    try {
      ProjectConfig config = new ProjectConfig();
      config.load(md, base);

      Set<String> oldSections = new HashSet<String>();
      for (AccessSection section : config.getAccessSections()) {
        oldSections.add(section.getRefPattern());
      }

      for (Iterator<AccessSection> itr = sectionList.iterator(); itr.hasNext();) {
        final AccessSection section = itr.next();
        if (section.getPermissions().isEmpty()) {
          // Don't keep or replace an empty section.  Discard it.
          itr.remove();
          continue;
        }

        final String name = section.getRefPattern();
        if (!projectControl.controlForRef(name).isOwner()) {
          continue;
        }

        if (name.startsWith(AccessSection.REGEX_PREFIX)) {
          if (!Repository.isValidRefName(RefControl.shortestExample(name))) {
            throw new InvalidNameException();
          }

        } else if (name.equals(AccessSection.ALL)) {
          // This is a special case we have to allow, it fails below.

        } else if (name.endsWith("/*")) {
          String prefix = name.substring(0, name.length() - 2);
          if (!Repository.isValidRefName(prefix)) {
            throw new InvalidNameException();
          }

        } else if (!Repository.isValidRefName(name)) {
          throw new InvalidNameException();
        }

        for (Permission permission : section.getPermissions()) {
          for (PermissionRule rule : permission.getRules()) {
            lookupGroup(rule);
            rule.setGroup(config.resolve(rule.getGroup()));
          }
        }

        config.replace(section);
        oldSections.remove(section.getRefPattern());
      }

      for (String name : oldSections) {
        if (projectControl.controlForRef(name).isOwner()) {
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

      if (config.commit(md)) {
        projectCache.evict(config.getProject());
        return projectAccessFactory.create(projectName).call();

      } else {
        throw new OrmConcurrencyException("Cannot update " + projectName);
      }
    } finally {
      md.close();
    }
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
