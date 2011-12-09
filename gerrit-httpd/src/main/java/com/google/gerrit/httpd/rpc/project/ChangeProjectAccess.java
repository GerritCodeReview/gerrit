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
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSet.Id;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.client.OrmConcurrencyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final ReviewDb db;
  private final IdentifiedUser user;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final GitRepositoryManager repoManager;

  private final Project.NameKey projectName;
  private final ObjectId base;
  private List<AccessSection> sectionList;
  private String message;

  @Inject
  ChangeProjectAccess(final ProjectAccessFactory.Factory projectAccessFactory,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final GroupCache groupCache,
      final MetaDataUpdate.User metaDataUpdateFactory,
      final ReviewDb db, final IdentifiedUser user,
      final PatchSetInfoFactory patchSetInfoFactory,
      final GitRepositoryManager repoManager,

      @Assisted final Project.NameKey projectName,
      @Assisted final ObjectId base, @Assisted List<AccessSection> sectionList,
      @Nullable @Assisted String message) {
    this.projectAccessFactory = projectAccessFactory;
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.groupCache = groupCache;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.db = db;
    this.user = user;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.repoManager = repoManager;

    this.projectName = projectName;
    this.base = base;
    this.sectionList = sectionList;
    this.message = message;
  }

  @Override
  public ProjectAccess call() throws NoSuchProjectException, IOException,
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
          if (!projectControl.isOwner()) {
            continue;
          }
          replace(config, toDelete, section);

        } else if (AccessSection.isAccessSection(name)) {
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

          replace(config, toDelete, section);
        }
      }

      for (String name : toDelete) {
        if (AccessSection.GLOBAL_CAPABILITIES.equals(name)) {
          if (projectControl.isOwner()) {
            config.remove(config.getAccessSection(name));
          }

        } else if (projectControl.controlForRef(name).isOwner()) {
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

      int nextChangeId = db.nextChangeId();
      Id patchSetId = new PatchSet.Id(new Change.Id(nextChangeId), 1);
      final PatchSet ps = new PatchSet(patchSetId);
      String changeRef = ps.getRefName();
      // Create the changeRef before calling config.commit
      // as config.commit will set non-zero expected-old-object-id
      // because config.revision is the commit id of the commit
      // that contains the edited project.config file
      Repository repository = repoManager.openRepository(projectName);
      try {
        RefUpdate ru = repository.updateRef(changeRef);
        ru.setExpectedOldObjectId(ObjectId.zeroId());
        ru.setNewObjectId(base);
        Result result = ru.update();
        switch (result) {
          case NEW:
            break;
          default:
            throw new IOException("Cannot update " + ru.getName() + " in "
                + repository.getDirectory() + ": " + ru.getResult());
        }
        config.setRefName(changeRef);
        try {
          RevCommit commit = config.commit(md);
          Change.Key changeKey = new Change.Key("I" + commit.name());
          final Change change =
              new Change(changeKey, new Change.Id(nextChangeId),
                  user.getAccountId(), new Branch.NameKey(config.getProject()
                      .getNameKey(), GitRepositoryManager.REF_CONFIG));
          change.nextPatchSetId();

          ps.setCreatedOn(change.getCreatedOn());
          ps.setUploader(user.getAccountId());
          ps.setRevision(new RevId(commit.name()));

          db.patchSets().insert(Collections.singleton(ps));

          final PatchSetInfo info = patchSetInfoFactory.get(commit, ps.getId());
          change.setCurrentPatchSet(info);
          ChangeUtil.updated(change);

          db.changes().insert(Collections.singleton(change));

          projectCache.evict(config.getProject());
          return projectAccessFactory.create(projectName).call();
        } catch (IOException e) {
          throw new OrmConcurrencyException("Cannot update " + projectName, e);
        }
      } finally {
        repository.close();
      }
    } finally {
      md.close();
    }
  }

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
