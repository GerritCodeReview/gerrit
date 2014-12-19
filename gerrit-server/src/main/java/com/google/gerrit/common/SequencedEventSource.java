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

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/** An EventSourceImpl which adds a sequenceId to events */
@Singleton
public class SequencedEventSource extends EventSourceImpl {
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      DynamicItem.itemOf(binder(), EventDispatcher.class);
      DynamicItem.bind(binder(), EventDispatcher.class)
        .to(SequencedEventSource.class);

      DynamicItem.itemOf(binder(), EventSource.class);
      DynamicItem.bind(binder(), EventSource.class)
        .to(SequencedEventSource.class);
    }
  }

  protected int sequenceId = 0;
  protected BlockingDeque<Event> dq;

  @Inject
  public SequencedEventSource(ProjectCache projectCache,
      DynamicSet<EventListener> unrestrictedListeners) {
    super(projectCache, unrestrictedListeners);
    dq = new LinkedBlockingDeque<>(1000);
  }

  @Override
  public synchronized void addEventListener(EventListener listener,
      CurrentUser user, long sequenceId, ReviewDb db) throws OrmException {
    for (Event event : dq) {
      if (sequenceId < new Long(event.sequenceId)) {
        if (isVisibleTo(event, user, db)) {
          listener.onEvent(event);
        }
      }
    }

    // Since this method and sequenceEvent are synchronized, any events
    // firing after this method is called, but before the new listener
    // is added, will block before iterating over the listeners.  This
    // means that the new listener should still get those events once
    // this method returns and unblocks the firing.  No events should
    // get dropped.
    addEventListener(listener, user);
  }

  @Override
  protected void fireEvent(Change change, Event event, ReviewDb db)
      throws OrmException {
    sequenceEvent(event);
    super.fireEvent(change, event, db);
  }

  @Override
  protected void fireEvent(Branch.NameKey branchName, Event event) {
    sequenceEvent(event);
    super.fireEvent(branchName, event);
  }

  @Override
  protected void fireEvent(Event event, ReviewDb db)
      throws OrmException {
    sequenceEvent(event);
    super.fireEvent(event, db);
  }

  protected synchronized void sequenceEvent(Event event) {
    event.sequenceId = new Integer(++sequenceId).toString();
    while (! dq.offer(event)) {
      dq.poll();
    }
  }
}