// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.common.EventListener;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EventsMetrics implements EventListener {

  private final Counter1<String> events;
  private final Counter3<String, String, String> scopedEvents;

  private static final Logger log = LoggerFactory.getLogger(EventsMetrics.class);

  @Inject
  public EventsMetrics(MetricMaker metricMaker) {
    Field<String> type = Field.ofString("type");
    Field<String> project = Field.ofString("project");
    Field<String> actor = Field.ofString("actor");

    this.scopedEvents =
        metricMaker.newCounter(
            "scoped-events",
            new Description("Events per Project and Actor").setRate().setUnit("triggered events"),
            type,
            project,
            actor);

    events =
        metricMaker.newCounter(
            "events",
            new Description("Triggered events").setRate().setUnit("triggered events"),
            type);
  }

  @Override
  public void onEvent(Event event) {
    events.increment(event.getType());

    try {
      // scoped events (project & user). not all events carry this information, so there will be
      // less scoped than scope-less events (thus tracking both).
      if (event instanceof ProjectEvent) {
        processProjectEvent((ProjectEvent) event);
      }
    } catch (Exception e) {
      log.error("exception during metrics conversion", e);
    }
  }

  private void processProjectEvent(ProjectEvent event) {
    String prj = event.getProjectNameKey().get();
    String user = null;

    if (event instanceof RefEvent) {
      user = personFromRefEvent(prj, (RefEvent) event);
    }

    if (user != null) {
      scopedEvents.increment(event.getType(), prj, user);
    }
  }

  private String personFromRefEvent(String prj, RefEvent event) {
    if (event instanceof CommitReceivedEvent) {
      return ((CommitReceivedEvent) event).user.getName();
    } else if (event instanceof RefReceivedEvent) {
      return ((RefReceivedEvent) event).user.getName();
    } else if (event instanceof RefUpdatedEvent) {
      return ((RefUpdatedEvent) event).submitter.get().name;
    }

    if (event instanceof ChangeEvent) {
      return personFromChangeEvent(prj, (ChangeEvent) event);
    }

    return null;
  }

  private String personFromChangeEvent(String prj, ChangeEvent event) {
    if (event instanceof AssigneeChangedEvent) {
      return ((AssigneeChangedEvent) event).changer.get().name;
    } else if (event instanceof HashtagsChangedEvent) {
      return ((HashtagsChangedEvent) event).editor.get().name;
    } else if (event instanceof PrivateStateChangedEvent) {
      return ((PrivateStateChangedEvent) event).changer.get().name;
    } else if (event instanceof TopicChangedEvent) {
      return ((TopicChangedEvent) event).changer.get().name;
    } else if (event instanceof WorkInProgressStateChangedEvent) {
      return ((WorkInProgressStateChangedEvent) event).changer.get().name;
    }

    if (event instanceof PatchSetEvent) {
      return personFromPatchSetEvent(prj, (PatchSetEvent) event);
    }

    return null;
  }

  private String personFromPatchSetEvent(String prj, PatchSetEvent event) {
    if (event instanceof ChangeAbandonedEvent) {
      return ((ChangeAbandonedEvent) event).abandoner.get().name;
    } else if (event instanceof ChangeMergedEvent) {
      return ((ChangeMergedEvent) event).submitter.get().name;
    } else if (event instanceof ChangeRestoredEvent) {
      return ((ChangeRestoredEvent) event).restorer.get().name;
    } else if (event instanceof CommentAddedEvent) {
      String actor = ((CommentAddedEvent) event).author.get().name;

      // also inject artificial events for all approvals
      ApprovalAttribute[] approvals = ((CommentAddedEvent) event).approvals.get();
      if (approvals != null && approvals.length > 0) {
        for (ApprovalAttribute approval : approvals) {
          if (approval.value.equals(approval.oldValue)) {
            continue; // no change
          }
          String apprId = approval.type + "-" + approval.value;
          scopedEvents.increment(apprId.toLowerCase(), prj, actor);
        }
      }
      return actor;
    } else if (event instanceof PatchSetCreatedEvent) {
      // also inject an artificial event if patchset number is 1 (change created)
      String user = ((PatchSetCreatedEvent) event).uploader.get().name;
      if (event.patchSet.get().number == 1) {
        scopedEvents.increment("change-created", prj, user);
      }
      return user;
    } else if (event instanceof ReviewerAddedEvent) {
      // intentionally left blank
    } else if (event instanceof ReviewerDeletedEvent) {
      // intentionally left blank
    } else if (event instanceof VoteDeletedEvent) {
      return ((VoteDeletedEvent) event).remover.get().name;
    }

    return null;
  }
}
