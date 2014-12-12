// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Distributes Events to listeners if they are allowed to see them */
@Singleton
public class EventBroker implements EventDispatcher, EventSource {

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(EventDispatcher.class).to(EventBroker.class);
      bind(EventSource.class).to(EventBroker.class);
    }
  }

  protected static class EventListenerHolder {
    final EventListener listener;
    final CurrentUser user;

    EventListenerHolder(EventListener l, CurrentUser u) {
      listener = l;
      user = u;
    }
  }

  /**
   *  Listeners to receive changes as they happen (limited by visibility
   *  of holder's user).
   */
  private final Map<EventListener, EventListenerHolder> listeners =
      new ConcurrentHashMap<>();

  /** Listeners to receive all changes as they happen. */
  private final DynamicSet<EventListener> unrestrictedListeners;

  private final ProjectCache projectCache;

  /**
    * Create a new EventBroker.
    * @param projectCache the project cache instance for the server.
    * @param unrestrictedListeners listeners to receive all events.
    */
  @Inject
  public EventBroker(ProjectCache projectCache,
      DynamicSet<EventListener> unrestrictedListeners) {
    this.projectCache = projectCache;
    this.unrestrictedListeners = unrestrictedListeners;
  }

  @Override
  public void addEventListener(EventListener listener, CurrentUser user) {
    listeners.put(listener, new EventListenerHolder(listener, user));
  }

  @Override
  public void removeEventListener(EventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void postEvent(Change change, Event event, ReviewDb db)
      throws OrmException {
    fireEvent(change, event, db);
  }

  @Override
  public void postEvent(Branch.NameKey branchName, Event event) {
    fireEvent(branchName, event);
  }

  @Override
  public void postEvent(Project.NameKey projectName, Event event) {
    fireEvent(projectName, event);
  }

  protected void fireEventForUnrestrictedListeners(Event event) {
    for (EventListener listener : unrestrictedListeners) {
      listener.onEvent(event);
    }
  }

  protected void fireEvent(Change change, Event event, ReviewDb db)
      throws OrmException {
    for (EventListenerHolder holder : listeners.values()) {
      if (isVisibleTo(change, holder.user, db)) {
        holder.listener.onEvent(event);
      }
    }

    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Branch.NameKey branchName, Event event) {
    for (EventListenerHolder holder : listeners.values()) {
      if (isVisibleTo(branchName, holder.user)) {
        holder.listener.onEvent(event);
      }
    }

    fireEventForUnrestrictedListeners(event);
  }

  protected boolean isVisibleTo(Change change, CurrentUser user, ReviewDb db)
      throws OrmException {
    ProjectState pe = projectCache.get(change.getProject());
    if (pe == null) {
      return false;
    }
    ProjectControl pc = pe.controlFor(user);
    return pc.controlFor(change).isVisible(db);
  }

  protected boolean isVisibleTo(Branch.NameKey branchName, CurrentUser user) {
    ProjectState pe = projectCache.get(branchName.getParentKey());
    if (pe == null) {
      return false;
    }
    ProjectControl pc = pe.controlFor(user);
    return pc.controlForRef(branchName).isVisible();
  }

  private void fireEvent(Project.NameKey project, Event event) {
    for (EventListenerHolder holder : listeners.values()) {
      if (isVisibleTo(project, event, holder.user)) {
        holder.listener.onEvent(event);
      }
    }

    fireEventForUnrestrictedListeners(event);
  }

  private boolean isVisibleTo(Project.NameKey project, Event event, CurrentUser user) {
    ProjectState pe = projectCache.get(project);
    if (pe == null) {
      return false;
    }
    ProjectControl pc = pe.controlFor(user);
    return pc.controlForRef(((ProjectCreatedEvent) event).getHeadName()).isVisible();
  }
}
