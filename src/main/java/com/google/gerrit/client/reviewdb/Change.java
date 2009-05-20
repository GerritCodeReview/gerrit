// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.RowVersion;

import java.sql.Timestamp;

/** A change recommended to be inserted into {@link Branch}. */
public final class Change {
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
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

    /** Parse a Change.Id out of a string representation. */
    public static Id parse(final String str) {
      final Id r = new Id();
      r.fromString(str);
      return r;
    }
  }

  /** Minimum database status constant for an open change. */
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
   * Current state within the basic workflow of the change.
   * 
   * <p>
   * Within the database, lower case codes ('a'..'z') indicate a change that is
   * still open, and that can be modified/refined further, while upper case
   * codes ('A'..'Z') indicate a change that is closed and cannot be further
   * modified.
   * */
  public static enum Status {
    /**
     * Change is open and pending review, or review is in progress.
     * 
     * <p>
     * This is the default state assigned to a change when it is first created
     * in the database. A change stays in the NEW state throughout its review
     * cycle, until the change is submitted or abandoned.
     * 
     * <p>
     * Changes in the NEW state can be moved to:
     * <ul>
     * <li>{@link #SUBMITTED} - when the Submit Patch Set action is used;
     * <li>{@link #ABANDONED} - when the Abandon action is used.
     * </ul>
     */
    NEW(STATUS_NEW),

    /**
     * Change is open, but has been submitted to the merge queue.
     * 
     * <p>
     * A change enters the SUBMITTED state when an authorized user presses the
     * "submit" action through the web UI, requesting that Gerrit merge the
     * change's current patch set into the destination branch.
     * 
     * <p>
     * Typically a change resides in the SUBMITTED for only a brief sub-second
     * period while the merge queue fires and the destination branch is updated.
     * However, if a dependency commit (a {@link PatchSetAncestor}, directly or
     * transitively) is not yet merged into the branch, the change will hang in
     * the SUBMITTED state indefinately.
     * 
     * <p>
     * Changes in the SUBMITTED state can be moved to:
     * <ul>
     * <li>{@link #NEW} - when a replacement patch set is supplied, OR when a
     * merge conflict is detected;
     * <li>{@link #MERGED} - when the change has been successfully merged into
     * the destination branch;
     * <li>{@link #ABANDONED} - when the Abandon action is used.
     * </ul>
     */
    SUBMITTED(STATUS_SUBMITTED),

    /**
     * Change is closed, and submitted to its destination branch.
     * 
     * <p>
     * Once a change has been merged, it cannot be further modified by adding a
     * replacement patch set. Draft comments however may be published,
     * supporting a post-submit review.
     */
    MERGED(STATUS_MERGED),

    /**
     * Change is closed, but was not submitted to its destination branch.
     * 
     * <p>
     * Once a change has been abandoned, it cannot be further modified by adding
     * a replacement patch set, and it cannot be merged. Draft comments however
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

  /** Locally assigned unique identifier of the change */
  @Column
  protected Id changeId;

  /** optimistic locking */
  @Column
  @RowVersion
  protected int rowVersion;

  /** When this change was first introduced into the database. */
  @Column
  protected Timestamp createdOn;

  /**
   * When was a meaningful modification last made to this record's data
   * <p>
   * Note, this update timestamp includes its children.
   */
  @Column
  protected Timestamp lastUpdatedOn;

  /** A {@link #lastUpdatedOn} ASC,{@link #changeId} ASC for sorting. */
  @Column(length = 16)
  protected String sortKey;

  @Column(name = "owner_account_id")
  protected Account.Id owner;

  /** The branch (and project) this change merges into. */
  @Column
  protected Branch.NameKey dest;

  /** Is the change currently open? Set to {@link #status}.isOpen(). */
  @Column
  protected boolean open;

  /** Current state code; see {@link Status}. */
  @Column
  protected char status;

  /** The total number of {@link PatchSet} children in this Change. */
  @Column
  protected int nbrPatchSets;

  /** The current patch set. */
  @Column
  protected int currentPatchSetId;

  /** Subject from the current patch set. */
  @Column
  protected String subject;

  protected Change() {
  }

  public Change(final Change.Id newId, final Account.Id ownedBy,
      final Branch.NameKey forBranch) {
    changeId = newId;
    createdOn = new Timestamp(System.currentTimeMillis());
    lastUpdatedOn = createdOn;
    owner = ownedBy;
    dest = forBranch;
    setStatus(Status.NEW);
  }

  public Change.Id getId() {
    return changeId;
  }

  public int getChangeId() {
    return changeId.get();
  }

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

  public String getSubject() {
    return subject;
  }

  /** Get the id of the most current {@link PatchSet} in this change. */
  public PatchSet.Id currentPatchSetId() {
    if (currentPatchSetId > 0) {
      return new PatchSet.Id(changeId, currentPatchSetId);
    }
    return null;
  }

  public void setCurrentPatchSet(final PatchSetInfo ps) {
    currentPatchSetId = ps.getKey().get();
    subject = ps.getSubject();
  }

  /**
   * Allocate a new PatchSet id within this change.
   * <p>
   * <b>Note: This makes the change dirty. Call update() after.</b>
   */
  public PatchSet.Id newPatchSetId() {
    return new PatchSet.Id(changeId, ++nbrPatchSets);
  }

  public Status getStatus() {
    return Status.forCode(status);
  }

  public void setStatus(final Status newStatus) {
    open = newStatus.isOpen();
    status = newStatus.getCode();
  }
}
