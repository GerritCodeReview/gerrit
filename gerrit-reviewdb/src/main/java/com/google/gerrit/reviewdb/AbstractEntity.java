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
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.RowVersion;
import com.google.gwtorm.client.StringKey;

import java.sql.Timestamp;

public abstract class AbstractEntity {
  public static abstract class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected int id;

    protected Id() {
    }

    protected Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }

    @Override
    protected void set(int newValue) {
      id = newValue;
    }
  }

  /** Globally unique identification of the change/topic. */
  public static class Key extends StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, length = 60)
    protected String id;

    protected Key() {
    }

    public Key(final String id) {
      this.id = id;
    }

    @Override
    public String get() {
      return id;
    }

    @Override
    protected void set(String newValue) {
      id = newValue;
    }

    /** Construct a key that is after all keys prefixed by this key. */
    public Key max() {
      final StringBuilder revEnd = new StringBuilder(get().length() + 1);
      revEnd.append(get());
      revEnd.append('\u9fa5');
      return new Key(revEnd.toString());
    }

    /** Obtain a shorter version of this key string, using a leading prefix. */
    public String abbreviate() {
      final String s = get();
      return s.substring(0, Math.min(s.length(), 9));
    }

    /** Parse a Key out of a string representation. */
    public static Key parse(final String str) {
      final Key r = new Key();
      r.fromString(str);
      return r;
    }
  }

  /** Minimum database status constant for an open change or topic. */
  private static final char MIN_OPEN = 'a';
  /** Database constant for {@link Status#NEW}. */
  protected static final char STATUS_NEW = 'n';
  /** Database constant for {@link Status#SUBMITTED}. */
  protected static final char STATUS_SUBMITTED = 's';
  /** Maximum database status constant for an open change. */
  private static final char MAX_OPEN = 'z';

  /** Database constant for {@link Status#MERGED}. */
  protected static final char STATUS_MERGED = 'M';

  /**
   * Current state within the basic workflow of a change or topic.
   *
   * <p>
   * Within the database, lower case codes ('a'..'z') indicate a change/topic
   * that is still open, and that can be modified/refined further, while upper
   * case codes ('A'..'Z') indicate a change/topic that is closed and cannot be
   * further modified.
   * */
  public static enum Status {
    /**
     * Change/topic is open and pending review, or review is in progress.
     *
     * <p>
     * This is the default state assigned to a change/topic when it is first created
     * in the database. A change/topic stays in the NEW state throughout its review
     * cycle, until the change/topic is submitted or abandoned.
     *
     * <p>
     * Changes/topic in the NEW state can be moved to:
     * <ul>
     * <li>{@link #SUBMITTED} - when the Submit Patch Set/Change Set action is used;
     * <li>{@link #ABANDONED} - when the Abandon action is used.
     * </ul>
     */
    NEW(STATUS_NEW),

    /**
     * Change/topic is open, but has been submitted to the merge queue.
     *
     * <p>
     * A change/topic enters the SUBMITTED state when an authorized user presses the
     * "submit" action through the web UI, requesting that Gerrit merge the
     * change's current patch set (or in the other hand, to merge the topic's
     * current change set) into the destination branch.
     *
     * <p>
     * Typically a change/topic resides in the SUBMITTED for only a brief sub-second
     * period while the merge queue fires and the destination branch is updated.
     * However, if a dependency commit (a {@link PatchSetAncestor}, directly or
     * transitively) is not yet merged into the branch, the change/topic will hang in
     * the SUBMITTED state indefinately.
     *
     * <p>
     * Changes/topics in the SUBMITTED state can be moved to:
     * <ul>
     * <li>{@link #NEW} - when a replacement patch/change set is supplied, OR when a
     * merge conflict is detected;
     * <li>{@link #MERGED} - when the change/topic has been successfully merged into
     * the destination branch;
     * <li>{@link #ABANDONED} - when the Abandon action is used.
     * </ul>
     */
    SUBMITTED(STATUS_SUBMITTED),

    /**
     * Change/topic is closed, and submitted to its destination branch.
     *
     * <p>
     * Once a change/topic has been merged, it cannot be further modified by adding a
     * replacement patch/change set. Draft comments however may be published,
     * supporting a post-submit review.
     */
    MERGED(STATUS_MERGED),

    /**
     * Change/topic is closed, but was not submitted to its destination branch.
     *
     * <p>
     * Once a change/topic has been abandoned, it cannot be further modified by adding
     * a replacement patch/change set, and it cannot be merged. Draft comments however
     * may be published, permitting reviewers to send constructive feedback.
     */
    ABANDONED('A');

    private final char code;
    private final boolean closed;

    private Status(final char c) {
      code = c;
      closed = !(MIN_OPEN <= c && c <= MAX_OPEN);
    }

    public char getCode() {
      return code;
    }

    public boolean isOpen() {
      return !closed;
    }

    public boolean isClosed() {
      return closed;
    }

    public static Status forCode(final char c) {
      for (final Status s : Status.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  /** optimistic locking */
  @Column(id = 3)
  @RowVersion
  protected int rowVersion;

  /** When this change was first introduced into the database. */
  @Column(id = 4)
  protected Timestamp createdOn;

  /**
   * When was a meaningful modification last made to this record's data
   * <p>
   * Note, this update timestamp includes its children.
   */
  @Column(id = 5)
  protected Timestamp lastUpdatedOn;

  /** A {@link #lastUpdatedOn} ASC,{@link #changeId} ASC for sorting. */
  @Column(id = 6, length = 16)
  protected String sortKey;

  @Column(id = 7, name = "owner_account_id")
  protected Account.Id owner;

  /** The branch (and project) this change merges into. */
  @Column(id = 8)
  protected Branch.NameKey dest;

  /** Is the change currently open? Set to {@link #status}.isOpen(). */
  @Column(id = 9)
  protected boolean open;

  /** Current state code; see {@link Status}. */
  @Column(id = 10)
  protected char status;

  public Timestamp getCreatedOn() {
    return createdOn;
  }

  public Timestamp getLastUpdatedOn() {
    return lastUpdatedOn;
  }

  public void resetLastUpdatedOn() {
    lastUpdatedOn = new Timestamp(System.currentTimeMillis());
  }

  public String getSortKey() {
    return sortKey;
  }

  public void setSortKey(final String newSortKey) {
    sortKey = newSortKey;
  }

  public Account.Id getOwner() {
    return owner;
  }

  public Branch.NameKey getDest() {
    return dest;
  }

  public Project.NameKey getProject() {
    return dest.getParentKey();
  }

  public Status getStatus() {
    return Status.forCode(status);
  }

  public void setStatus(final Status newStatus) {
    open = newStatus.isOpen();
    status = newStatus.getCode();
  }
}
