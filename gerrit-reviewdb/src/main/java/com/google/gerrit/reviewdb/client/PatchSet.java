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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;

import java.sql.Timestamp;

/** A single revision of a {@link Change}. */
public final class PatchSet {
  private static final String REFS_CHANGES = "refs/changes/";

  /** Is the reference name a change reference? */
  public static boolean isRef(final String name) {
    if (name == null || !name.startsWith(REFS_CHANGES)) {
      return false;
    }
    boolean accepted = false;
    int numsFound = 0;
    for (int i = name.length() - 1; i >= REFS_CHANGES.length() - 1; i--) {
      char c = name.charAt(i);
      if (c >= '0' && c <= '9') {
        accepted = (c != '0');
      } else if (c == '/') {
        if (accepted) {
          if (++numsFound == 2) {
            return true;
          }
          accepted = false;
        }
      } else {
        return false;
      }
    }
    return false;
  }

  public static class Id extends IntKey<Change.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected Change.Id changeId;

    @Column(id = 2)
    protected int patchSetId;

    protected Id() {
      changeId = new Change.Id();
    }

    public Id(final Change.Id change, final int id) {
      this.changeId = change;
      this.patchSetId = id;
    }

    @Override
    public Change.Id getParentKey() {
      return changeId;
    }

    @Override
    public int get() {
      return patchSetId;
    }

    @Override
    protected void set(int newValue) {
      patchSetId = newValue;
    }

    public String toRefName() {
      StringBuilder r = new StringBuilder();
      r.append(REFS_CHANGES);
      int change = changeId.get();
      int m = change % 100;
      if (m < 10) {
        r.append('0');
      }
      r.append(m);
      r.append('/');
      r.append(change);
      r.append('/');
      r.append(patchSetId);
      return r.toString();
    }

    /** Parse a PatchSet.Id out of a string representation. */
    public static Id parse(final String str) {
      final Id r = new Id();
      r.fromString(str);
      return r;
    }

    /** Parse a PatchSet.Id from a {@link PatchSet#getRefName()} result. */
    public static Id fromRef(String name) {
      if (!name.startsWith(REFS_CHANGES)) {
        throw new IllegalArgumentException("Not a PatchSet.Id: " + name);
      }
      final String[] parts = name.substring(REFS_CHANGES.length()).split("/");
      final int n = parts.length;
      if (n < 2) {
        throw new IllegalArgumentException("Not a PatchSet.Id: " + name);
      }
      final int changeId = Integer.parseInt(parts[n - 2]);
      final int patchSetId = Integer.parseInt(parts[n - 1]);
      return new PatchSet.Id(new Change.Id(changeId), patchSetId);
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Id id;

  @Column(id = 2, notNull = false)
  protected RevId revision;

  @Column(id = 3, name = "uploader_account_id")
  protected Account.Id uploader;

  /** When this patch set was first introduced onto the change. */
  @Column(id = 4)
  protected Timestamp createdOn;

  @Column(id = 5)
  protected boolean draft;

  /** Not persisted in the database */
  protected boolean hasDraftComments;

  protected PatchSet() {
  }

  public PatchSet(final PatchSet.Id k) {
    id = k;
  }

  public PatchSet.Id getId() {
    return id;
  }

  public int getPatchSetId() {
    return id.get();
  }

  public RevId getRevision() {
    return revision;
  }

  public void setRevision(final RevId i) {
    revision = i;
  }

  public Account.Id getUploader() {
    return uploader;
  }

  public void setUploader(final Account.Id who) {
    uploader = who;
  }

  public Timestamp getCreatedOn() {
    return createdOn;
  }

  public void setCreatedOn(final Timestamp ts) {
    createdOn = ts;
  }

  public boolean isDraft() {
    return draft;
  }

  public void setDraft(boolean draftStatus) {
    draft = draftStatus;
  }

  public String getRefName() {
    return id.toRefName();
  }

  public boolean getHasDraftComments() {
    return hasDraftComments;
  }

  public void setHasDraftComments(boolean hasDraftComments) {
    this.hasDraftComments = hasDraftComments;
  }

  @Override
  public String toString() {
    return "[PatchSet " + getId().toString() + "]";
  }
}
