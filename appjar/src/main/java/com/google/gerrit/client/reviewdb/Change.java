// Copyright 2008 Google Inc.
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

  protected static final char STATUS_NEW = 'n';
  protected static final char STATUS_MERGED = 'M';

  public static enum Status {
    NEW(STATUS_NEW, false),

    MERGED(STATUS_MERGED, true),

    ABANDONED('A', true);

    private final char code;
    private final boolean closed;

    private Status(final char c, final boolean o) {
      code = c;
      closed = o;
    }

    public char getCode() {
      return code;
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

  @Column(name = "owner_account_id")
  protected Account.Id owner;

  /** The branch (and project) this change merges into. */
  @Column
  protected Branch.NameKey dest;

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

  public Change.Id getKey() {
    return changeId;
  }

  public int getId() {
    return changeId.get();
  }

  public Timestamp getCreatedOn() {
    return createdOn;
  }

  public Timestamp getLastUpdatedOn() {
    return lastUpdatedOn;
  }

  public void updated() {
    lastUpdatedOn = new Timestamp(System.currentTimeMillis());
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
    status = newStatus.getCode();
  }
}
