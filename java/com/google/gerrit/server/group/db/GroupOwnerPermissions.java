// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group.db;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In NoteDb the owner of a group is determined by permissions on the group ref. Everyone who can
 * update the group ref is owner of the group.
 *
 * <p>This class updates the permissions for the group owner when a new group owner is set. It also
 * allows to read the existing group permissions to determine the current group owner.
 *
 * <p>This class relies on the group permissions being exclusively managed by Gerrit. Manual
 * modifications of the group permissions must not be allowed. This guarantees that each group has
 * exactly one set of owner permissions and there is always a single owner group.
 */
public class GroupOwnerPermissions {
  private static final Logger log = LoggerFactory.getLogger(GroupOwnerPermissions.class);

  private final AllUsersName allUsersName;
  private final Repository allUsersRepo;
  @Nullable private final MetaDataUpdateFactory metaDataUpdateFactory;

  /**
   * @param allUsersName Name of the All-Users repository in which the groups are stored.
   * @param allUsersRepo All-Users repository.
   * @param metaDataUpdateFactory Factory to create a {@link MetaDataUpdate}, required for updating
   *     the group owner. Can be {@code null} if the group owner is read-only.
   */
  public GroupOwnerPermissions(
      AllUsersName allUsersName,
      Repository allUsersRepo,
      @Nullable MetaDataUpdateFactory metaDataUpdateFactory) {
    this.allUsersName = checkNotNull(allUsersName);
    this.allUsersRepo = checkNotNull(allUsersRepo);
    this.metaDataUpdateFactory = metaDataUpdateFactory;
  }

  /**
   * Determines the owner group from the permissions on the group ref.
   *
   * @param groupUuid UUID of the group for which the group owner should be read.
   * @return UUID of the owner group, {@code null} if the owner permissions are missing or if owner
   *     permissions for multiple groups exist.
   */
  public AccountGroup.UUID readOwnerGroup(AccountGroup.UUID groupUuid)
      throws IOException, ConfigInvalidException {
    ProjectConfig config = new ProjectConfig(allUsersName);
    config.load(allUsersRepo);

    String ref = RefNames.refsGroups(groupUuid);
    AccessSection accessSection = config.getAccessSection(ref);
    if (accessSection == null) {
      return null;
    }

    Permission pushPermission = accessSection.getPermission(Permission.PUSH);
    if (pushPermission == null) {
      return null;
    }

    List<PermissionRule> rules = pushPermission.getRules();
    if (rules.isEmpty()) {
      return null;
    } else if (rules.size() > 1) {
      log.error(
          String.format(
              "Group %s has multiple group owner permissions, expected exactly one",
              groupUuid.get()));
      return null;
    }

    return Iterables.getOnlyElement(rules).getGroup().getUUID();
  }

  /**
   * Updating the group owner permissions.
   *
   * <p>For the new group owner READ/PUSH permissions on the group ref are added, for the old group
   * owner the permissions are removed.
   *
   * @param groupUuid UUID of the group for which the owner permissions should be updated.
   * @param oldOwnerGroupReference Group reference for the old owner group, {@code null} if the
   *     group is newly created.
   * @param newOwnerGroupReference Group reference for the new owner group.
   */
  public void updateOwnerPermissions(
      AccountGroup.UUID groupUuid,
      @Nullable GroupReference oldOwnerGroupReference,
      GroupReference newOwnerGroupReference)
      throws IOException, ConfigInvalidException {
    checkNotNull(metaDataUpdateFactory);
    checkNotNull(newOwnerGroupReference);
    if (newOwnerGroupReference.equals(oldOwnerGroupReference)) {
      return;
    }

    String ref = RefNames.refsGroups(groupUuid);

    try (MetaDataUpdate md = metaDataUpdateFactory.create(allUsersName, allUsersRepo)) {
      ProjectConfig config = ProjectConfig.read(md);

      if (oldOwnerGroupReference != null) {
        for (String permission : ImmutableList.of(Permission.READ, Permission.PUSH)) {
          config.remove(
              config.getAccessSection(ref),
              new Permission(permission),
              new PermissionRule(oldOwnerGroupReference));
        }
      }

      AccessSection accessSection = config.getAccessSection(ref, true);
      for (String permission : ImmutableList.of(Permission.READ, Permission.PUSH)) {
        accessSection
            .getPermission(permission, true)
            .add(new PermissionRule(newOwnerGroupReference));
      }

      config.commit(md);
    }
  }
}
