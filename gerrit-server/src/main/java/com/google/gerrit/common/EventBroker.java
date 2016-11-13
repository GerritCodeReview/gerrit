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

package com.google.gerrit.common;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectEvent;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/** Distributes Events to listeners if they are allowed to see them */
@Singleton
public class EventBroker implements EventDispatcher {

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicItem.itemOf(binder(), EventDispatcher.class);
      DynamicItem.bind(binder(), EventDispatcher.class).to(EventBroker.class);
    }
  }

  /** Listeners to receive changes as they happen (limited by visibility of user). */
  protected final DynamicSet<UserScopedEventListener> listeners;

  /** Listeners to receive all changes as they happen. */
  protected final DynamicSet<EventListener> unrestrictedListeners;

  protected final ProjectCache projectCache;

  protected final ChangeNotes.Factory notesFactory;

  protected final Provider<ReviewDb> dbProvider;

  @Inject
  public EventBroker(
      DynamicSet<UserScopedEventListener> listeners,
      DynamicSet<EventListener> unrestrictedListeners,
      ProjectCache projectCache,
      ChangeNotes.Factory notesFactory,
      Provider<ReviewDb> dbProvider) {
    this.listeners = listeners;
    this.unrestrictedListeners = unrestrictedListeners;
    this.projectCache = projectCache;
    this.notesFactory = notesFactory;
    this.dbProvider = dbProvider;
  }

  @Override
  public void postEvent(Change change, ChangeEvent event) throws OrmException {
    fireEvent(change, event);
  }

  @Override
  public void postEvent(Branch.NameKey branchName, RefEvent event) {
    fireEvent(branchName, event);
  }

  @Override
  public void postEvent(Project.NameKey projectName, ProjectEvent event) {
    fireEvent(projectName, event);
  }

  @Override
  public void postEvent(Event event) throws OrmException {
    fireEvent(event);
  }

  protected void fireEventForUnrestrictedListeners(Event event) {
    for (EventListener listener : unrestrictedListeners) {
      listener.onEvent(event);
    }
  }

  protected void fireEvent(Change change, ChangeEvent event) throws OrmException {
    for (UserScopedEventListener listener : listeners) {
      if (isVisibleTo(change, listener.getUser())) {
        listener.onEvent(event);
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Project.NameKey project, ProjectEvent event) {
    for (UserScopedEventListener listener : listeners) {
      if (isVisibleTo(project, listener.getUser())) {
        listener.onEvent(event);
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Branch.NameKey branchName, RefEvent event) {
    for (UserScopedEventListener listener : listeners) {
      if (isVisibleTo(branchName, listener.getUser())) {
        listener.onEvent(event);
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Event event) throws OrmException {
    for (UserScopedEventListener listener : listeners) {
      if (isVisibleTo(event, listener.getUser())) {
        listener.onEvent(event);
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected boolean isVisibleTo(Project.NameKey project, CurrentUser user) {
    ProjectState pe = projectCache.get(project);
    if (pe == null) {
      return false;
    }
    return pe.controlFor(user).isVisible();
  }

  protected boolean isVisibleTo(Change change, CurrentUser user) throws OrmException {
    if (change == null) {
      return false;
    }
    ProjectState pe = projectCache.get(change.getProject());
    if (pe == null) {
      return false;
    }
    ProjectControl pc = pe.controlFor(user);
    ReviewDb db = dbProvider.get();
    return pc.controlFor(db, change).isVisible(db);
  }

  protected boolean isVisibleTo(Branch.NameKey branchName, CurrentUser user) {
    ProjectState pe = projectCache.get(branchName.getParentKey());
    if (pe == null) {
      return false;
    }
    ProjectControl pc = pe.controlFor(user);
    return pc.controlForRef(branchName).isVisible();
  }

  protected boolean isVisibleTo(Event event, CurrentUser user) throws OrmException {
    if (event instanceof RefEvent) {
      RefEvent refEvent = (RefEvent) event;
      String ref = refEvent.getRefName();
      if (PatchSet.isChangeRef(ref)) {
        Change.Id cid = PatchSet.Id.fromRef(ref).getParentKey();
        Change change =
            notesFactory.create(dbProvider.get(), refEvent.getProjectNameKey(), cid).getChange();
        return isVisibleTo(change, user);
      }
      return isVisibleTo(refEvent.getBranchNameKey(), user);
    } else if (event instanceof ProjectEvent) {
      return isVisibleTo(((ProjectEvent) event).getProjectNameKey(), user);
    }
    return true;
  }
}
