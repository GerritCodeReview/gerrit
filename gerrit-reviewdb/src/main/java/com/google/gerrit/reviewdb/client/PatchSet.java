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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;

import java.sql.Timestamp;

/** A single revision of a {@link Change}. */
public final class PatchSet {
  /** Is the reference name a change reference? */
  public static boolean isRef(String name) {
    return Id.fromRef(name) != null;
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
      if (name == null || !name.startsWith(REFS_CHANGES)) {
        return null;
      }

      // Last 2 digits.
      int ls = REFS_CHANGES.length();
      int le;
      for (le = ls; le < name.length() && name.charAt(le) != '/'; le++) {
        if (name.charAt(le) < '0' || name.charAt(le) > '9') {
          return null;
        }
      }
      if (le - ls != 2) {
        return null;
      }

      // Change ID.
      int cs = le + 1;
      if (cs >= name.length() || name.charAt(cs) == '0') {
        return null;
      }
      int ce;
      for (ce = cs; ce < name.length() && name.charAt(ce) != '/'; ce++) {
        if (name.charAt(ce) < '0' || name.charAt(ce) > '9') {
          return null;
        }
      }
      switch (ce - cs) {
        case 0:
          return null;
        case 1:
          if (name.charAt(ls) != '0'
              || name.charAt(ls + 1) != name.charAt(cs)) {
            return null;
          }
          break;
        default:
          if (name.charAt(ls) != name.charAt(ce - 2)
              || name.charAt(ls + 1) != name.charAt(ce - 1)) {
            return null;
          }
          break;
      }
      if (ce == cs) {
        return null;
      }

      // Patch set ID.
      int ps = ce + 1;
      if (ps >= name.length() || name.charAt(ps) == '0') {
        return null;
      }
      for (int i = ps; i < name.length(); i++) {
        if (name.charAt(i) < '0' || name.charAt(i) > '9') {
          return null;
        }
      }

      int ln = Integer.parseInt(name.substring(ls, le));
      int changeId = Integer.parseInt(name.substring(cs, ce));
      if (changeId % 100 != ln) {
        return null;
      }
      int patchSetId = Integer.parseInt(name.substring(ps));

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

  @Override
  public String toString() {
    return "[PatchSet " + getId().toString() + "]";
  }
}
