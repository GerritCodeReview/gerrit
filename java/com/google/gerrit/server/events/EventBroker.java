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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
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
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Distributes Events to listeners if they are allowed to see them */
@Singleton
public class EventBroker implements EventDispatcher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicItem.itemOf(binder(), EventDispatcher.class);
      DynamicItem.bind(binder(), EventDispatcher.class).to(EventBroker.class);
    }
  }

  /** Listeners to receive changes as they happen (limited by visibility of user). */
  protected final PluginSetContext<UserScopedEventListener> listeners;

  /** Listeners to receive all changes as they happen. */
  protected final PluginSetContext<EventListener> unrestrictedListeners;

  private final PermissionBackend permissionBackend;
  protected final ProjectCache projectCache;

  protected final ChangeNotes.Factory notesFactory;

  @Inject
  public EventBroker(
      PluginSetContext<UserScopedEventListener> listeners,
      PluginSetContext<EventListener> unrestrictedListeners,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      ChangeNotes.Factory notesFactory) {
    this.listeners = listeners;
    this.unrestrictedListeners = unrestrictedListeners;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.notesFactory = notesFactory;
  }

  @Override
  public void postEvent(Change change, ChangeEvent event)
      throws StorageException, PermissionBackendException {
    fireEvent(change, event);
  }

  @Override
  public void postEvent(Branch.NameKey branchName, RefEvent event)
      throws PermissionBackendException {
    fireEvent(branchName, event);
  }

  @Override
  public void postEvent(Project.NameKey projectName, ProjectEvent event) {
    fireEvent(projectName, event);
  }

  @Override
  public void postEvent(Event event) throws StorageException, PermissionBackendException {
    fireEvent(event);
  }

  protected void fireEventForUnrestrictedListeners(Event event) {
    unrestrictedListeners.runEach(l -> l.onEvent(event));
  }

  protected void fireEvent(Change change, ChangeEvent event)
      throws StorageException, PermissionBackendException {
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(change, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Project.NameKey project, ProjectEvent event) {
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(project, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Branch.NameKey branchName, RefEvent event)
      throws PermissionBackendException {
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(branchName, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Event event) throws StorageException, PermissionBackendException {
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(UserScopedEventListener::getUser);
      if (isVisibleTo(event, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected boolean isVisibleTo(Project.NameKey project, CurrentUser user) {
    try {
      ProjectState state = projectCache.get(project);
      if (state == null || !state.statePermitsRead()) {
        return false;
      }

      permissionBackend.user(user).project(project).check(ProjectPermission.ACCESS);
      return true;
    } catch (AuthException | PermissionBackendException e) {
      return false;
    }
  }

  protected boolean isVisibleTo(Change change, CurrentUser user)
      throws StorageException, PermissionBackendException {
    if (change == null) {
      return false;
    }
    ProjectState pe = projectCache.get(change.getProject());
    if (pe == null || !pe.statePermitsRead()) {
      return false;
    }
    try {
      permissionBackend
          .user(user)
          .change(notesFactory.createChecked(change))
          .check(ChangePermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  protected boolean isVisibleTo(Branch.NameKey branchName, CurrentUser user)
      throws PermissionBackendException {
    ProjectState pe = projectCache.get(branchName.getParentKey());
    if (pe == null || !pe.statePermitsRead()) {
      return false;
    }

    try {
      permissionBackend.user(user).ref(branchName).check(RefPermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  protected boolean isVisibleTo(Event event, CurrentUser user)
      throws StorageException, PermissionBackendException {
    if (event instanceof RefEvent) {
      RefEvent refEvent = (RefEvent) event;
      String ref = refEvent.getRefName();
      if (PatchSet.isChangeRef(ref)) {
        Change.Id cid = PatchSet.Id.fromRef(ref).getParentKey();
        try {
          Change change = notesFactory.createChecked(refEvent.getProjectNameKey(), cid).getChange();
          return isVisibleTo(change, user);
        } catch (NoSuchChangeException e) {
          logger.atFine().log(
              "Change %s cannot be found, falling back on ref visibility check", cid.id);
        }
      }
      return isVisibleTo(refEvent.getBranchNameKey(), user);
    } else if (event instanceof ProjectEvent) {
      return isVisibleTo(((ProjectEvent) event).getProjectNameKey(), user);
    }
    return true;
  }
}
