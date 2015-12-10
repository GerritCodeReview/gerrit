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
    registerClass(new ChangeAbandonedEvent());
    registerClass(new ChangeMergedEvent());
    registerClass(new ChangeRestoredEvent());
    registerClass(new CommentAddedEvent());
    registerClass(new CommitReceivedEvent());
    registerClass(new DraftPublishedEvent());
    registerClass(new HashtagsChangedEvent());
    registerClass(new MergeFailedEvent());
    registerClass(new RefUpdatedEvent());
    registerClass(new RefReceivedEvent());
    registerClass(new ReviewerAddedEvent());
    registerClass(new ReviewerDeletedEvent());
    registerClass(new PatchSetCreatedEvent());
    registerClass(new TopicChangedEvent());
    registerClass(new ProjectCreatedEvent());
  }

  /** Register an event.
   *
   *  @param event The event to register.
   *  registered.
   **/
  public static void registerClass(Event event) {
    String type = event.getType();
    typesByString.put(type, event.getClass());
  }

  /** Get the class for an event type.
   *
   * @param type The type.
   * @return The event class, or null if no class is registered with the
   * given type
   **/
  public static Class<?> getClass(String type) {
    return typesByString.get(type);
  }
}
