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

package com.google.gerrit.server.permissions;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.api.access.PluginPermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Mappings from {@link com.google.gerrit.extensions.api.access.GerritPermission} enum instances to
 * the permission names used by {@link DefaultPermissionBackend}.
 *
 * <p>These should be considered implementation details of {@code DefaultPermissionBackend}; a
 * backend that doesn't respect the default permission model will not need to consult these.
 * However, implementations may also choose to respect certain aspects of the default permission
 * model, so this class is provided as public to aid those implementations.
 */
public class DefaultPermissionMappings {
  private static final ImmutableBiMap<GlobalPermission, String> CAPABILITIES =
      ImmutableBiMap.<GlobalPermission, String>builder()
          .put(GlobalPermission.ACCESS_DATABASE, GlobalCapability.ACCESS_DATABASE)
          .put(GlobalPermission.ADMINISTRATE_SERVER, GlobalCapability.ADMINISTRATE_SERVER)
          .put(GlobalPermission.CREATE_ACCOUNT, GlobalCapability.CREATE_ACCOUNT)
          .put(GlobalPermission.CREATE_GROUP, GlobalCapability.CREATE_GROUP)
          .put(GlobalPermission.CREATE_PROJECT, GlobalCapability.CREATE_PROJECT)
          .put(GlobalPermission.EMAIL_REVIEWERS, GlobalCapability.EMAIL_REVIEWERS)
          .put(GlobalPermission.FLUSH_CACHES, GlobalCapability.FLUSH_CACHES)
          .put(GlobalPermission.KILL_TASK, GlobalCapability.KILL_TASK)
          .put(GlobalPermission.MAINTAIN_SERVER, GlobalCapability.MAINTAIN_SERVER)
          .put(GlobalPermission.MODIFY_ACCOUNT, GlobalCapability.MODIFY_ACCOUNT)
          .put(GlobalPermission.RUN_AS, GlobalCapability.RUN_AS)
          .put(GlobalPermission.RUN_GC, GlobalCapability.RUN_GC)
          .put(GlobalPermission.STREAM_EVENTS, GlobalCapability.STREAM_EVENTS)
          .put(GlobalPermission.VIEW_ALL_ACCOUNTS, GlobalCapability.VIEW_ALL_ACCOUNTS)
          .put(GlobalPermission.VIEW_CACHES, GlobalCapability.VIEW_CACHES)
          .put(GlobalPermission.VIEW_CONNECTIONS, GlobalCapability.VIEW_CONNECTIONS)
          .put(GlobalPermission.VIEW_PLUGINS, GlobalCapability.VIEW_PLUGINS)
          .put(GlobalPermission.VIEW_QUEUE, GlobalCapability.VIEW_QUEUE)
          .build();

  static {
    checkMapContainsAllEnumValues(CAPABILITIES, GlobalPermission.class);
  }

  private static final ImmutableBiMap<ProjectPermission, String> PROJECT_PERMISSIONS =
      ImmutableBiMap.<ProjectPermission, String>builder()
          .put(ProjectPermission.READ, Permission.READ)
          .build();

  private static final ImmutableBiMap<RefPermission, String> REF_PERMISSIONS =
      ImmutableBiMap.<RefPermission, String>builder()
          .put(RefPermission.READ, Permission.READ)
          .put(RefPermission.CREATE, Permission.CREATE)
          .put(RefPermission.DELETE, Permission.DELETE)
          .put(RefPermission.UPDATE, Permission.PUSH)
          .put(RefPermission.FORGE_AUTHOR, Permission.FORGE_AUTHOR)
          .put(RefPermission.FORGE_COMMITTER, Permission.FORGE_COMMITTER)
          .put(RefPermission.FORGE_SERVER, Permission.FORGE_SERVER)
          .put(RefPermission.CREATE_TAG, Permission.CREATE_TAG)
          .put(RefPermission.CREATE_SIGNED_TAG, Permission.CREATE_SIGNED_TAG)
          .put(RefPermission.READ_PRIVATE_CHANGES, Permission.VIEW_PRIVATE_CHANGES)
          .build();

  private static <T extends Enum<T>> void checkMapContainsAllEnumValues(
      ImmutableMap<T, String> actual, Class<T> clazz) {
    Set<T> expected = EnumSet.allOf(clazz);
    checkState(
        actual.keySet().equals(expected),
        "all %s values must be defined, found: %s",
        clazz.getSimpleName(),
        actual.keySet());
  }

  public static String globalPermissionName(GlobalPermission globalPermission) {
    return checkNotNull(CAPABILITIES.get(globalPermission));
  }

  public static Optional<GlobalPermission> globalPermission(String capabilityName) {
    return Optional.ofNullable(CAPABILITIES.inverse().get(capabilityName));
  }

  public static String pluginPermissionName(PluginPermission pluginPermission) {
    return pluginPermission.pluginName() + '-' + pluginPermission.capability();
  }

  public static String globalOrPluginPermissionName(GlobalOrPluginPermission permission) {
    return permission instanceof GlobalPermission
        ? globalPermissionName((GlobalPermission) permission)
        : pluginPermissionName((PluginPermission) permission);
  }

  public static Optional<String> projectPermissionName(ProjectPermission projectPermission) {
    return Optional.ofNullable(PROJECT_PERMISSIONS.get(projectPermission));
  }

  public static Optional<ProjectPermission> projectPermission(String permissionName) {
    return Optional.ofNullable(PROJECT_PERMISSIONS.inverse().get(permissionName));
  }

  public static Optional<String> refPermissionName(RefPermission refPermission) {
    return Optional.ofNullable(REF_PERMISSIONS.get(refPermission));
  }

  public static Optional<RefPermission> refPermission(String permissionName) {
    return Optional.ofNullable(REF_PERMISSIONS.inverse().get(permissionName));
  }

  private DefaultPermissionMappings() {}
}
