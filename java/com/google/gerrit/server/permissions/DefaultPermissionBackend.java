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

package com.google.gerrit.server.permissions;

import static com.google.gerrit.server.permissions.DefaultPermissionMappings.globalPermissionName;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.PermissionRule.Action;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.server.account.CapabilityCollection;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Singleton
public class DefaultPermissionBackend extends PermissionBackend {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final CurrentUser.PropertyKey<Boolean> IS_ADMIN = CurrentUser.PropertyKey.create();

  private final Provider<CurrentUser> currentUser;
  private final ProjectCache projectCache;
  private final ProjectControl.Factory projectControlFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  DefaultPermissionBackend(
      Provider<CurrentUser> currentUser,
      ProjectCache projectCache,
      ProjectControl.Factory projectControlFactory,
      IdentifiedUser.GenericFactory identifiedUserFactory) {
    this.currentUser = currentUser;
    this.projectCache = projectCache;
    this.projectControlFactory = projectControlFactory;
    this.identifiedUserFactory = identifiedUserFactory;
  }

  private CapabilityCollection capabilities() {
    return projectCache.getAllProjects().getCapabilityCollection();
  }

  @Override
  public WithUser currentUser() {
    return new WithUserImpl(currentUser.get());
  }

  @Override
  public WithUser user(CurrentUser user) {
    return new WithUserImpl(requireNonNull(user, "user"));
  }

  @Override
  public WithUser absentUser(Account.Id id) {
    IdentifiedUser identifiedUser = identifiedUserFactory.create(requireNonNull(id, "user"));
    return new WithUserImpl(identifiedUser);
  }

  @Override
  public boolean usesDefaultCapabilities() {
    return true;
  }

  class WithUserImpl extends WithUser {
    private final CurrentUser user;
    private Boolean admin;

    WithUserImpl(CurrentUser user) {
      this.user = requireNonNull(user, "user");
    }

    @Override
    public ForProject project(Project.NameKey project) {
      try {
        ProjectState state = projectCache.get(project).orElseThrow(illegalState(project));
        ProjectControl control =
            PerThreadCache.getOrCompute(
                PerThreadCache.Key.create(ProjectControl.class, project, user.getCacheKey()),
                () -> projectControlFactory.create(user, state));
        return control.asForProject();
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
      Set<T> ok = Sets.newHashSetWithExpectedSize(permSet.size());
      for (T perm : permSet) {
        if (can(perm)) {
          ok.add(perm);
        }
      }
      return ok;
    }

    @Override
    public BooleanCondition testCond(GlobalOrPluginPermission perm) {
      return new PermissionBackendCondition.WithUser(this, perm, user);
    }

    private boolean can(GlobalOrPluginPermission perm) throws PermissionBackendException {
      if (perm instanceof GlobalPermission) {
        return can((GlobalPermission) perm);
      } else if (perm instanceof PluginPermission) {
        PluginPermission pluginPermission = (PluginPermission) perm;
        return has(DefaultPermissionMappings.pluginCapabilityName(pluginPermission))
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
          return has(globalPermissionName(perm)) || can(GlobalPermission.MAINTAIN_SERVER);

        case CREATE_ACCOUNT:
        case CREATE_GROUP:
        case CREATE_PROJECT:
        case MAINTAIN_SERVER:
        case MODIFY_ACCOUNT:
        case READ_AS:
        case STREAM_EVENTS:
        case VIEW_ALL_ACCOUNTS:
        case VIEW_CONNECTIONS:
        case VIEW_PLUGINS:
        case VIEW_ACCESS:
          return has(globalPermissionName(perm)) || isAdmin();

        case ACCESS_DATABASE:
        case RUN_AS:
          return has(globalPermissionName(perm));
      }
      throw new PermissionBackendException(perm + " unsupported");
    }

    private boolean isAdmin() {
      if (admin == null) {
        admin = computeAdmin();
        if (admin) {
          logger.atFinest().log(
              "user %s is an administrator of the server", user.getLoggableName());
        } else {
          logger.atFinest().log(
              "user %s is not an administrator of the server", user.getLoggableName());
        }
      }
      return admin;
    }

    private Boolean computeAdmin() {
      Optional<Boolean> r = user.get(IS_ADMIN);
      if (r.isPresent()) {
        return r.get();
      }

      boolean isAdmin;
      if (user.isImpersonating()) {
        isAdmin = false;
      } else if (user instanceof PeerDaemonUser) {
        isAdmin = true;
      } else {
        isAdmin = allow(capabilities().administrateServer);
      }
      user.put(IS_ADMIN, isAdmin);
      return isAdmin;
    }

    private boolean canEmailReviewers() {
      List<PermissionRule> email = capabilities().emailReviewers;
      if (allow(email)) {
        logger.atFinest().log(
            "user %s can email reviewers (allowed by %s)", user.getLoggableName(), email);
        return true;
      }

      if (notDenied(email)) {
        logger.atFinest().log(
            "user %s can email reviewers (not denied by %s)", user.getLoggableName(), email);
        return true;
      }

      logger.atFinest().log("user %s cannot email reviewers", user.getLoggableName());
      return false;
    }

    private boolean has(String permissionName) {
      boolean has = allow(capabilities().getPermission(requireNonNull(permissionName)));
      if (has) {
        logger.atFinest().log(
            "user %s has global capability %s", user.getLoggableName(), permissionName);
      } else {
        logger.atFinest().log(
            "user %s doesn't have global capability %s", user.getLoggableName(), permissionName);
      }
      return has;
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
}
