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
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.events.ChangeEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Distributes Events to listeners if they are allowed to see them */
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
  public void removeEventListener(EventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void fireEventForUnrestrictedListeners(final Event event) {
    for (EventListener listener : unrestrictedListeners) {
      listener.onEvent(event);
    }
  }

  @Override
  public void fireEvent(final Change change, final ChangeEvent event,
      final ReviewDb db) throws OrmException {
    for (EventListenerHolder holder : listeners.values()) {
      if (isVisibleTo(change, holder.user, db)) {
        holder.listener.onEvent(event);
      }
    }

    fireEventForUnrestrictedListeners( event );
  }

  @Override
  public void fireEvent(Branch.NameKey branchName, final RefEvent event) {
    if (event instanceof ChangeEvent) {
      throw new IllegalArgumentException("ChangeEvents require a Change to fire");
    }

    for (EventListenerHolder holder : listeners.values()) {
      if (isVisibleTo(branchName, holder.user)) {
        holder.listener.onEvent(event);
      }
    }

    fireEventForUnrestrictedListeners( event );
  }

  @Override
  public void fireEvent(final Event event, final ReviewDb db)
      throws OrmException {
    if (event instanceof ChangeEvent) {
      ChangeEvent cev = (ChangeEvent) event;
      Change change = db.changes().get(cev.getChangeId());
      fireEvent(change, cev, db);
    } else if (event instanceof RefEvent) {
      RefEvent rev = (RefEvent) event;
      fireEvent(rev.getBranchNameKey(), rev);
    }
  }

  private boolean isVisibleTo(Change change, CurrentUser user, ReviewDb db)
      throws OrmException {
    ProjectState pe = projectCache.get(change.getProject());
    if (pe == null) {
      return false;
    }
    ProjectControl pc = pe.controlFor(user);
    return pc.controlFor(change).isVisible(db);
  }

  private boolean isVisibleTo(Branch.NameKey branchName, CurrentUser user) {
    ProjectState pe = projectCache.get(branchName.getParentKey());
    if (pe == null) {
      return false;
    }
    ProjectControl pc = pe.controlFor(user);
    return pc.controlForRef(branchName).isVisible();
  }
}
