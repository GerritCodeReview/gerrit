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

package com.google.gerrit.server.events;

import java.util.HashMap;
import java.util.Map;

/** Class for registering event types */
public class EventTypes {
  private static final Map<String, Class<?>> typesByString = new HashMap<>();

  static {
    register(AssigneeChangedEvent.TYPE, AssigneeChangedEvent.class);
    register(ChangeAbandonedEvent.TYPE, ChangeAbandonedEvent.class);
    register(ChangeMergedEvent.TYPE, ChangeMergedEvent.class);
    register(ChangeRestoredEvent.TYPE, ChangeRestoredEvent.class);
    register(CommentAddedEvent.TYPE, CommentAddedEvent.class);
    register(CommitReceivedEvent.TYPE, CommitReceivedEvent.class);
    register(DraftPublishedEvent.TYPE, DraftPublishedEvent.class);
    register(HashtagsChangedEvent.TYPE, HashtagsChangedEvent.class);
    register(RefUpdatedEvent.TYPE, RefUpdatedEvent.class);
    register(RefReceivedEvent.TYPE, RefReceivedEvent.class);
    register(ReviewerAddedEvent.TYPE, ReviewerAddedEvent.class);
    register(ReviewerDeletedEvent.TYPE, ReviewerDeletedEvent.class);
    register(PatchSetCreatedEvent.TYPE, PatchSetCreatedEvent.class);
    register(TopicChangedEvent.TYPE, TopicChangedEvent.class);
    register(ProjectCreatedEvent.TYPE, ProjectCreatedEvent.class);
  }

  /**
   * Register an event type and associated class.
   *
   * @param eventType The event type to register.
   * @param eventClass The event class to register.
   */
  public static void register(String eventType, Class<? extends Event> eventClass) {
    typesByString.put(eventType, eventClass);
  }

  /**
   * Get the class for an event type.
   *
   * @param type The type.
   * @return The event class, or null if no class is registered with the given type
   */
  public static Class<?> getClass(String type) {
    return typesByString.get(type);
  }
}
