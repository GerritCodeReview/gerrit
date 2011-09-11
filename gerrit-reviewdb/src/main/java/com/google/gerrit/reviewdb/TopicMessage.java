// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;
import com.google.gerrit.reviewdb.Topic.Id;

import java.sql.Timestamp;

/** A message attached to a {@link Topic}. */
public final class TopicMessage extends AbstractMessage {
  public static class Key extends StringKey<Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Id topicId;

    @Column(id = 2, length = 40)
    protected String uuid;

    protected Key() {
      topicId = new Id();
    }

    public Key(final Id topic, final String uuid) {
      this.topicId = topic;
      this.uuid = uuid;
    }

    @Override
    public Id getParentKey() {
      return topicId;
    }

    @Override
    public String get() {
      return uuid;
    }

    @Override
    protected void set(String newValue) {
      uuid = newValue;
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  protected TopicMessage() {
  }

  public TopicMessage(final TopicMessage.Key k, final Account.Id a) {
    this(k, a, new Timestamp(System.currentTimeMillis()));
  }

  public TopicMessage(final TopicMessage.Key k, final Account.Id a,
      final Timestamp wo) {
    key = k;
    author = a;
    writtenOn = wo;
  }

  public TopicMessage.Key getKey() {
    return key;
  }
}
