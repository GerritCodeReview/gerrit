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

import java.sql.Timestamp;

/**
 * A group of changes proposed to be merged into a {@link Branch}.
 * <p>
 * The data graph rooted below a Topic can be quite complex:
 *
 * <pre>
 *   {@link Topic}
 *     |
 *     +- {@link TopicMessage}: &quot;cover letter&quot; or general comment.
 *     |
 *     +- {@link ChangeSet}: a variant of this topic ( group of changes ).
 *          |
 *          +- {@link ChangeSetApproval}: a +/- vote on the topic's current state.
 *
 */
public final class Topic extends AbstractEntity {
  public static class Id extends AbstractEntity.Id {
    private static final long serialVersionUID = 1L;

    public Id() {
      super();
    }

    public Id(final int id) {
      super(id);
    }

    /** Parse a Topic.Id out of a string representation. */
    public static Id parse(final String str) {
      final Id r = new Id();
      r.fromString(str);
      return r;
    }

    public static Id fromRef(final String ref) {
      return ChangeSet.Id.fromRef(ref).getParentKey();
    }
  }

  /** Locally assigned unique identifier of the topic */
  @Column(id = 1)
  protected Id topicId;

  /** Globally assigned unique identifier of the topic */
  @Column(id = 2)
  protected Key topicKey;

  /** The total number of {@link ChangeSet} children in this Topic. */
  @Column(id = 11)
  protected int nbrChangeSets;

  /** The current change set. */
  @Column(id = 12)
  protected int currentChangeSetId;

  /** Subject from the current change set. */
  @Column(id = 13)
  protected String subject;

  /** Topic name assigned by the user, must not be null. */
  @Column(id = 14)
  protected String topicName;

  protected Topic() {
  }

  public Topic(final Topic.Key newKey, final Id newId,
      final Account.Id ownedBy, final Branch.NameKey forBranch) {
    topicKey = newKey;
    topicId = newId;
    createdOn = new Timestamp(System.currentTimeMillis());
    lastUpdatedOn = createdOn;
    owner = ownedBy;
    dest = forBranch;
    setStatus(Status.NEW);
  }

  public Id getId() {
    return topicId;
  }

  public int getTopicId() {
    return topicId.get();
  }

  /** The Topic-Id tag. */
  public Topic.Key getKey() {
    return topicKey;
  }

  public void setKey(final Topic.Key k) {
    topicKey = k;
  }

  public String getSubject() {
    return subject;
  }

  /** Get the id of the most current {@link ChangeSet} in this topic. */
  public ChangeSet.Id currentChangeSetId() {
    if (currentChangeSetId > 0) {
      return new ChangeSet.Id(topicId, currentChangeSetId);
    }
    return null;
  }

  public void setCurrentChangeSet(final ChangeSetInfo cs) {
    currentChangeSetId = cs.getKey().get();
    subject = cs.getSubject();
  }

  /**
   * Allocate a new ChangeSet id within this topic.
   * <p>
   * <b>Note: This makes the change dirty. Call update() after.</b>
   */
  public void nextChangeSetId() {
    ++nbrChangeSets;
  }

  public ChangeSet.Id currChangeSetId() {
    return new ChangeSet.Id(topicId, nbrChangeSets);
  }

  public String getTopic() {
    return topicName;
  }

  public void setTopic(String topic) {
    this.topicName = topic;
  }
}
