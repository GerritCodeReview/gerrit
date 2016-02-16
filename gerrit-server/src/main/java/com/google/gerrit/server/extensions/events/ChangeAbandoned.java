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

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import org.eclipse.jgit.revwalk.RevWalk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class ChangeAbandoned {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeAbandoned.class);

  public static final ChangeAbandoned DISABLED = new ChangeAbandoned(
      Collections.<ChangeAbandonedListener> emptyList());

  private final Iterable<ChangeAbandonedListener> listeners;

  @Inject
  ChangeAbandoned(DynamicSet<ChangeAbandonedListener> listeners) {
    this.listeners = listeners;
  }

  ChangeAbandoned(Iterable<ChangeAbandonedListener> listeners) {
    this.listeners = listeners;
  }

  public void fire(
      Project.NameKey project, String reason,
      Change change, PatchSet ps, RevWalk rw) {
    Event event = new Event(project, reason, change, ps, rw);
    for (ChangeAbandonedListener l : listeners) {
      try {
        l.onChangeAbandoned(event);
      } catch (RuntimeException e) {
        log.warn("Failure in ChangeAbandonedListener", e);
      }
    }
  }

  private static class Event implements ChangeAbandonedListener.Event {
    private final String projectName;
    private final String reason;
    private final Change change;
    private final PatchSet ps;
    private final RevWalk rw;

    Event(
        Project.NameKey project, String reason,
        Change change, PatchSet ps, RevWalk rw) {
      this.projectName = project.get();
      this.reason = reason;
      this.change = change;
      this.ps = ps;
      this.rw = rw;
    }

    @Override
    public String getProjectName() {
      return projectName;
    }

    @Override
    public String getReason() {
      return reason;
    }

    @Override
    public String getSubject() {
      return EventsCommon.getSubject(change);
    }

    @Override
    public String getChangeId() {
      return EventsCommon.getId(change);
    }

    @Override
    public String getChangeNumber() {
      return EventsCommon.getNumber(change);
    }

    @Override
    public String getTopic() {
      return EventsCommon.getTopic(change);
    }

    @Override
    public String getBranch() {
      return EventsCommon.getBranch(change);
    }

    @Override
    public String getPatchSetNumber() {
      return EventsCommon.getNumber(ps);
    }

    @Override
    public String getRevision() {
      return EventsCommon.getRevision(ps);
    }

    @Override
    public String getRef() {
      return EventsCommon.getRef(ps);
    }

    @Override
    public List<String> getParents() {
      return EventsCommon.getParents(ps, rw);
    }

    @Override
    public String toString() {
      return String.format(
          "%s[%s,%s,%s]",
          getClass().getSimpleName(),
          projectName, getChangeNumber(), getPatchSetNumber());
    }
  }
}
