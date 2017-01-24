// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.client;

import java.sql.Timestamp;
import java.util.Objects;

public abstract class EventInfo {
  public String id;
  public Timestamp date;
  public String tag;
  public EventInfo.Type type;

  // TODO: Add messages, comments, etc.
  public enum Type {
    COMMENT,
    MESSAGE,
    REVIEWER_UPDATE,
    DRAFT_COMMENT;
  }

  public abstract EventInfo.Type getType();

  public EventInfo() {
    type = getType();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o != null && getClass() == o.getClass()) {
      EventInfo e = (EventInfo) o;
      return Objects.equals(id, e.id) 
          && Objects.equals(tag, e.tag)
          && Objects.equals(type, e.type)
          && Objects.equals(date, e.date);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tag, type, date);
  }
}
