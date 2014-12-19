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
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Distributes Events to listeners if they are allowed to see them */
@Singleton
public class EventSourceImpl implements EventDispatcher, EventSource {
  protected static class EventListenerHolder {
    final EventListener listener;
    final CurrentUser user;

    EventListenerHolder(EventListener l, CurrentUser u) {
      listener = l;
      user = u;
    }
  }

  /** Listeners to receive changes as they happen (limited by visibility
   *  of holder's user). */
  protected final Map<EventListener, EventListenerHolder> listeners =
      new ConcurrentHashMap<>();

  /** Listeners to receive all changes as they happen. */
  protected final DynamicSet<EventListener> unrestrictedListeners;

  protected final ProjectCache projectCache;

  /**
    * Create a new ChangeEventSourceImpl.
    * @param projectCache the project cache instance for the server.
    */
  @Inject
  public EventSourceImpl(final ProjectCache projectCache,
      final DynamicSet<EventListener> unrestrictedListeners) {
    this.projectCache = projectCache;
    this.unrestrictedListeners = unrestrictedListeners;
  }

  @Override
  public void addEventListener(EventListener listener, CurrentUser user) {
    listeners.put(listener, new EventListenerHolder(listener, user));
  }

  @Override
  public void addEventListener(EventListener listener, CurrentUser user,
      long sequenceId, ReviewDb db) throws OrmException {
    addEventListener(listener, user);
  }

  @Override
  public void removeEventListener(EventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void postEvent(final Change change, final Event event,
      final ReviewDb db) throws OrmException {
    fireEvent(change, event, db);
  }

  @Override
  public void postEvent(final Branch.NameKey branchName, final Event event) {
    fireEvent(branchName, event);
  }

  @Override
  public void postEvent(final Event event, final ReviewDb db)
      throws OrmException {
    fireEvent(event, db);
  }

  protected void fireEventForUnrestrictedListeners(final Event event) {
    for (EventListener listener : unrestrictedListeners) {
      listener.onEvent(event);
    }
  }

  protected void fireEvent(final Change change, final Event event,
      final ReviewDb db) throws OrmException {
    for (EventListenerHolder holder : listeners.values()) {
      if (isVisibleTo(change, holder.user, db)) {
        holder.listener.onEvent(event);
      }
    }

    fireEventForUnrestrictedListeners( event );
  }

  protected void fireEvent(Branch.NameKey branchName, final Event event) {
    for (EventListenerHolder holder : listeners.values()) {
      if (isVisibleTo(branchName, holder.user)) {
        holder.listener.onEvent(event);
      }
    }

    fireEventForUnrestrictedListeners( event );
  }

  protected void fireEvent(final Event event, final ReviewDb db)
      throws OrmException {
    for (EventListenerHolder holder : listeners.values()) {
      if (isVisibleTo(event, holder.user, db)) {
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

  protected boolean isVisibleTo(Event event, CurrentUser user, ReviewDb db)
      throws OrmException {
    if (event instanceof RefEvent) {
      RefEvent rev = (RefEvent) event;
      String ref = rev.getRefName();
      if (PatchSet.isRef(ref)) {
        Change.Id cid;
        if (event instanceof ChangeEvent) {
          cid = ((ChangeEvent) event).getChangeId();
        } else {
          cid = PatchSet.Id.fromRef(ref).getParentKey();
        }
        return isVisibleTo(db.changes().get(cid), user, db);
      }
      return isVisibleTo(rev.getBranchNameKey(), user);
    }
    return true;
  }

}
