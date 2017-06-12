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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A single revision of a {@link Change}. */
public final class PatchSet {
  /** Is the reference name a change reference? */
  public static boolean isChangeRef(String name) {
    return Id.fromRef(name) != null;
  }

  /**
   * Is the reference name a change reference?
   *
   * @deprecated use isChangeRef instead.
   */
  @Deprecated
  public static boolean isRef(String name) {
    return isChangeRef(name);
  }

  static String joinGroups(List<String> groups) {
    if (groups == null) {
      throw new IllegalArgumentException("groups may not be null");
    }
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (String g : groups) {
      if (!first) {
        sb.append(',');
      } else {
        first = false;
      }
      sb.append(g);
    }
    return sb.toString();
  }

  public static List<String> splitGroups(String joinedGroups) {
    if (joinedGroups == null) {
      throw new IllegalArgumentException("groups may not be null");
    }
    List<String> groups = new ArrayList<>();
    int i = 0;
    while (true) {
      int idx = joinedGroups.indexOf(',', i);
      if (idx < 0) {
        groups.add(joinedGroups.substring(i, joinedGroups.length()));
        break;
      }
      groups.add(joinedGroups.substring(i, idx));
      i = idx + 1;
    }
    return groups;
  }

  public static class Id extends IntKey<Change.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    public Change.Id changeId;

    @Column(id = 2)
    public int patchSetId;

    public Id() {
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
      return changeId.refPrefixBuilder().append(patchSetId).toString();
    }

    /** Parse a PatchSet.Id out of a string representation. */
    public static Id parse(final String str) {
      final Id r = new Id();
      r.fromString(str);
      return r;
    }

    /** Parse a PatchSet.Id from a {@link PatchSet#getRefName()} result. */
    public static Id fromRef(String ref) {
      int cs = Change.Id.startIndex(ref);
      if (cs < 0) {
        return null;
      }
      int ce = Change.Id.nextNonDigit(ref, cs);
      int patchSetId = fromRef(ref, ce);
      if (patchSetId < 0) {
        return null;
      }
      int changeId = Integer.parseInt(ref.substring(cs, ce));
      return new PatchSet.Id(new Change.Id(changeId), patchSetId);
    }

    static int fromRef(String ref, int changeIdEnd) {
      // Patch set ID.
      int ps = changeIdEnd + 1;
      if (ps >= ref.length() || ref.charAt(ps) == '0') {
        return -1;
      }
      for (int i = ps; i < ref.length(); i++) {
        if (ref.charAt(i) < '0' || ref.charAt(i) > '9') {
          return -1;
        }
      }
      return Integer.parseInt(ref.substring(ps));
    }

    public String getId() {
      return toId(patchSetId);
    }

    public static String toId(int number) {
      return number == 0 ? "edit" : String.valueOf(number);
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

  /**
   * Opaque group identifier, usually assigned during creation.
   *
   * <p>This field is actually a comma-separated list of values, as in rare cases involving merge
   * commits a patch set may belong to multiple groups.
   *
   * <p>Changes on the same branch having patch sets with intersecting groups are considered
   * related, as in the "Related Changes" tab.
   */
  @Column(id = 6, notNull = false, length = Integer.MAX_VALUE)
  protected String groups;

  //DELETED id = 7 (pushCertficate)

  /** Certificate sent with a push that created this patch set. */
  @Column(id = 8, notNull = false, length = Integer.MAX_VALUE)
  protected String pushCertificate;

  protected PatchSet() {}

  public PatchSet(final PatchSet.Id k) {
    id = k;
  }

  public PatchSet(PatchSet src) {
    this.id = src.id;
    this.revision = src.revision;
    this.uploader = src.uploader;
    this.createdOn = src.createdOn;
    this.draft = src.draft;
    this.groups = src.groups;
    this.pushCertificate = src.pushCertificate;
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

  public List<String> getGroups() {
    if (groups == null) {
      return Collections.emptyList();
    }
    return splitGroups(groups);
  }

  public void setGroups(List<String> groups) {
    if (groups == null) {
      groups = Collections.emptyList();
    }
    this.groups = joinGroups(groups);
  }

  public String getRefName() {
    return id.toRefName();
  }

  public String getPushCertificate() {
    return pushCertificate;
  }

  public void setPushCertificate(String cert) {
    pushCertificate = cert;
  }

  @Override
  public String toString() {
    return "[PatchSet " + getId().toString() + "]";
  }
}
