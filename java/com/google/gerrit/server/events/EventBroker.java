// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.plugincontext.PluginSetEntryContext;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;

/** Distributes Events to listeners if they are allowed to see them */
@Singleton
public class EventBroker implements EventDispatcher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class EventBrokerModule extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicItem.itemOf(binder(), EventDispatcher.class);
      DynamicItem.bind(binder(), EventDispatcher.class).to(EventBroker.class);

      bind(Gson.class)
          .annotatedWith(EventGson.class)
          .toProvider(EventGsonProvider.class)
          .in(Singleton.class);
    }
  }

  /** Listeners to receive changes as they happen (limited by visibility of user). */
  protected final PluginSetContext<UserScopedEventListener> listeners;

  /** Listeners to receive all changes as they happen. */
  protected final PluginSetContext<EventListener> unrestrictedListeners;

  private final PermissionBackend permissionBackend;
  protected final ProjectCache projectCache;

  protected final ChangeNotes.Factory notesFactory;

  protected final String gerritInstanceId;

  @Inject
  public EventBroker(
      PluginSetContext<UserScopedEventListener> listeners,
      PluginSetContext<EventListener> unrestrictedListeners,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      ChangeNotes.Factory notesFactory,
      @Nullable @GerritInstanceId String gerritInstanceId) {
    this.listeners = listeners;
    this.unrestrictedListeners = unrestrictedListeners;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.notesFactory = notesFactory;
    this.gerritInstanceId = gerritInstanceId;
  }

  @Override
  public void postEvent(Change change, ChangeEvent event) throws PermissionBackendException {
    fireEvent(change, event);
  }

  @Override
  public void postEvent(BranchNameKey branchName, RefEvent event)
      throws PermissionBackendException {
    fireEvent(branchName, event);
  }

  @Override
  public void postEvent(Project.NameKey projectName, ProjectEvent event) {
    fireEvent(projectName, event);
  }

  @Override
  public void postEvent(Event event) throws PermissionBackendException {
    fireEvent(event);
  }

  protected void fireEventForUnrestrictedListeners(Event event) {
    unrestrictedListeners.runEach(l -> l.onEvent(event));
  }

  protected void fireEvent(Change change, ChangeEvent event) throws PermissionBackendException {
    setInstanceIdWhenEmpty(event);
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(change, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Project.NameKey project, ProjectEvent event) {
    setInstanceIdWhenEmpty(event);
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {

      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(project, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(BranchNameKey branchName, RefEvent event)
      throws PermissionBackendException {
    setInstanceIdWhenEmpty(event);
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(branchName, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Event event) throws PermissionBackendException {
    setInstanceIdWhenEmpty(event);
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(event, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void setInstanceIdWhenEmpty(Event event) {
    if (Strings.isNullOrEmpty(event.instanceId)) {
      event.instanceId = gerritInstanceId;
    }
  }

  protected boolean isVisibleTo(Project.NameKey project, CurrentUser user) {
    try {
      Optional<ProjectState> state = projectCache.get(project);
      if (!state.isPresent() || !state.get().statePermitsRead()) {
        return false;
      }

      return permissionBackend.user(user).project(project).test(ProjectPermission.ACCESS);
    } catch (PermissionBackendException e) {
      return false;
    }
  }

  protected boolean isVisibleTo(Change change, CurrentUser user) throws PermissionBackendException {
    if (change == null) {
      return false;
    }
    Optional<ProjectState> pe = projectCache.get(change.getProject());
    if (!pe.isPresent() || !pe.get().statePermitsRead()) {
      return false;
    }
    return permissionBackend
        .user(user)
        .change(notesFactory.createChecked(change))
        .test(ChangePermission.READ);
  }

  protected boolean isVisibleTo(BranchNameKey branchName, CurrentUser user)
      throws PermissionBackendException {
    Optional<ProjectState> pe = projectCache.get(branchName.project());
    if (!pe.isPresent() || !pe.get().statePermitsRead()) {
      return false;
    }

    return permissionBackend.user(user).ref(branchName).test(RefPermission.READ);
  }

  protected boolean isVisibleTo(Event event, CurrentUser user) throws PermissionBackendException {
    if (event instanceof RefEvent) {
      RefEvent refEvent = (RefEvent) event;
      String ref = refEvent.getRefName();
      if (PatchSet.isChangeRef(ref)) {
        Change.Id cid = PatchSet.Id.fromRef(ref).changeId();
        try {
          Change change = notesFactory.createChecked(refEvent.getProjectNameKey(), cid).getChange();
          return isVisibleTo(change, user);
        } catch (NoSuchChangeException e) {
          logger.atFine().log(
              "Change %s cannot be found, falling back on ref visibility check", cid.get());
        }
      }
      return isVisibleTo(refEvent.getBranchNameKey(), user);
    } else if (event instanceof ProjectEvent) {
      return isVisibleTo(((ProjectEvent) event).getProjectNameKey(), user);
    }
    return true;
  }
}
