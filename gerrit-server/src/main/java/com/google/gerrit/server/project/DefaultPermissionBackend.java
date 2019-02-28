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

package com.google.gerrit.server.project;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Sets;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.permissions.FailedPermissionBackend;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Singleton
public class DefaultPermissionBackend extends PermissionBackend {
  private static final CurrentUser.PropertyKey<Boolean> IS_ADMIN = CurrentUser.PropertyKey.create();

  private final ProjectCache projectCache;

  @Inject
  DefaultPermissionBackend(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  private CapabilityCollection capabilities() {
    return projectCache.getAllProjects().getCapabilityCollection();
  }

  @Override
  public WithUser user(CurrentUser user) {
    return new WithUserImpl(checkNotNull(user, "user"));
  }

  class WithUserImpl extends WithUser {
    private final CurrentUser user;
    private Boolean admin;

    WithUserImpl(CurrentUser user) {
      this.user = checkNotNull(user, "user");
    }

    @Override
    public ForProject project(Project.NameKey project) {
      try {
        return projectCache.checkedGet(project, true).controlFor(user).asForProject().database(db);
      } catch (Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return FailedPermissionBackend.project(
            "project '" + project.get() + "' is unavailable", cause);
      }
    }

    @Override
    public void check(GlobalOrPluginPermission perm)
        throws AuthException, PermissionBackendException {
      if (!can(perm)) {
        throw new AuthException(perm.describeForException() + " not permitted");
      }
    }

    @Override
    public <T extends GlobalOrPluginPermission> Set<T> test(Collection<T> permSet)
        throws PermissionBackendException {
      Set<T> ok = newSet(permSet);
      for (T perm : permSet) {
        if (can(perm)) {
          ok.add(perm);
        }
      }
      return ok;
    }

    private boolean can(GlobalOrPluginPermission perm) throws PermissionBackendException {
      if (perm instanceof GlobalPermission) {
        return can((GlobalPermission) perm);
      } else if (perm instanceof PluginPermission) {
        PluginPermission pluginPermission = (PluginPermission) perm;
        return has(pluginPermission.permissionName())
            || (pluginPermission.fallBackToAdmin() && isAdmin());
      }
      throw new PermissionBackendException(perm + " unsupported");
    }

    private boolean can(GlobalPermission perm) throws PermissionBackendException {
      switch (perm) {
        case ADMINISTRATE_SERVER:
          return isAdmin();
        case EMAIL_REVIEWERS:
          return canEmailReviewers();

        case FLUSH_CACHES:
        case KILL_TASK:
        case RUN_GC:
        case VIEW_CACHES:
        case VIEW_QUEUE:
          return has(perm.permissionName()) || can(GlobalPermission.MAINTAIN_SERVER);

        case CREATE_ACCOUNT:
        case CREATE_GROUP:
        case CREATE_PROJECT:
        case MAINTAIN_SERVER:
        case MODIFY_ACCOUNT:
        case STREAM_EVENTS:
        case VIEW_ALL_ACCOUNTS:
        case VIEW_CONNECTIONS:
        case VIEW_PLUGINS:
          return has(perm.permissionName()) || isAdmin();

        case ACCESS_DATABASE:
        case RUN_AS:
          return has(perm.permissionName());
      }
      throw new PermissionBackendException(perm + " unsupported");
    }

    private boolean isAdmin() {
      if (admin == null) {
        admin = computeAdmin();
      }
      return admin;
    }

    private Boolean computeAdmin() {
      Boolean r = user.get(IS_ADMIN);
      if (r == null) {
        if (user.isImpersonating()) {
          r = false;
        } else if (user instanceof PeerDaemonUser) {
          r = true;
        } else {
          r = allow(capabilities().administrateServer);
        }
        user.put(IS_ADMIN, r);
      }
      return r;
    }

    private boolean canEmailReviewers() {
      List<PermissionRule> email = capabilities().emailReviewers;
      return allow(email) || notDenied(email);
    }

    private boolean has(String permissionName) {
      return allow(capabilities().getPermission(permissionName));
    }

    private boolean allow(Collection<PermissionRule> rules) {
      return user.getEffectiveGroups()
          .containsAnyOf(
              rules.stream()
                  .filter(r -> r.getAction() == Action.ALLOW)
                  .map(r -> r.getGroup().getUUID())
                  .collect(toSet()));
    }

    private boolean notDenied(Collection<PermissionRule> rules) {
      Set<AccountGroup.UUID> denied =
          rules.stream()
              .filter(r -> r.getAction() != Action.ALLOW)
              .map(r -> r.getGroup().getUUID())
              .collect(toSet());
      return denied.isEmpty() || !user.getEffectiveGroups().containsAnyOf(denied);
    }
  }

  private static <T extends GlobalOrPluginPermission> Set<T> newSet(Collection<T> permSet) {
    if (permSet instanceof EnumSet) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Set<T> s = ((EnumSet) permSet).clone();
      s.clear();
      return s;
    }
    return Sets.newHashSetWithExpectedSize(permSet.size());
  }
}
