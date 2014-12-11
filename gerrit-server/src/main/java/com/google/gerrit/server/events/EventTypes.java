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

/** Class for registering all StreamEvent types */
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
    registerClass(new PatchSetCreatedEvent());
    registerClass(new TopicChangedEvent());
  }

  /** Register all known Events using this method. */
  public static void registerClass(Event event) {
    typesByString.put(event.getType(), event.getClass());
  }

  public static Class<?> getClass(String type) {
    return typesByString.get(type);
  }
}
