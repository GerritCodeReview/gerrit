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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.lib.ObjectId;

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

  public static String joinGroups(List<String> groups) {
    requireNonNull(groups);
    for (String group : groups) {
      checkArgument(!group.contains(","), "group may not contain ',': %s", group);
    }
    return String.join(",", groups);
  }

  public static ImmutableList<String> splitGroups(String joinedGroups) {
    return Streams.stream(Splitter.on(',').split(joinedGroups)).collect(toImmutableList());
  }

  public static Id id(Change.Id changeId, int id) {
    return new AutoValue_PatchSet_Id(changeId, id);
  }

  @AutoValue
  public abstract static class Id {
    /** Parse a PatchSet.Id out of a string representation. */
    public static Id parse(String str) {
      List<String> parts = Splitter.on(',').splitToList(str);
      checkIdFormat(parts.size() == 2, str);
      Integer changeId = Ints.tryParse(parts.get(0));
      checkIdFormat(changeId != null, str);
      Integer id = Ints.tryParse(parts.get(1));
      checkIdFormat(id != null, str);
      return PatchSet.id(Change.id(changeId), id);
    }

    private static void checkIdFormat(boolean test, String input) {
      checkArgument(test, "invalid patch set ID: %s", input);
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
      return PatchSet.id(Change.id(changeId), patchSetId);
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

    public static String toId(int number) {
      return number == 0 ? "edit" : String.valueOf(number);
    }

    public String getId() {
      return toId(id());
    }

    public abstract Change.Id changeId();

    abstract int id();

    public int get() {
      return id();
    }

    public String toRefName() {
      return changeId().refPrefixBuilder().append(id()).toString();
    }

    @Override
    public String toString() {
      return changeId().toString() + ',' + id();
    }
  }

  protected Id id;

  protected ObjectId commitId;

  protected Account.Id uploader;

  /** When this patch set was first introduced onto the change. */
  protected Timestamp createdOn;

  /**
   * Opaque group identifier, usually assigned during creation.
   *
   * <p>This field is actually a comma-separated list of values, as in rare cases involving merge
   * commits a patch set may belong to multiple groups.
   *
   * <p>Changes on the same branch having patch sets with intersecting groups are considered
   * related, as in the "Related Changes" tab.
   */
  @Nullable protected String groups;

  // DELETED id = 7 (pushCertficate)

  /** Certificate sent with a push that created this patch set. */
  @Nullable protected String pushCertificate;

  /**
   * Optional user-supplied description for this patch set.
   *
   * <p>When this field is null, the description was never set on the patch set. When this field is
   * an empty string, the description was set and later cleared.
   */
  @Nullable protected String description;

  public PatchSet(PatchSet.Id id, ObjectId commitId) {
    this.id = requireNonNull(id);
    this.commitId = commitId.copy();
  }

  public PatchSet(PatchSet src) {
    this.id = src.id;
    this.commitId = src.commitId;
    this.uploader = src.uploader;
    this.createdOn = src.createdOn;
    this.groups = src.groups;
    this.pushCertificate = src.pushCertificate;
    this.description = src.description;
  }

  public PatchSet.Id getId() {
    return id;
  }

  public int getPatchSetId() {
    return id.get();
  }

  /**
   * Get the ID of the commit associated with this patch set.
   *
   * <p>The commit associated with a patch set is also known as the <strong>revision</strong>.
   *
   * @return the commit ID, never null.
   */
  public ObjectId getCommitId() {
    return commitId;
  }

  public Account.Id getUploader() {
    return uploader;
  }

  public void setUploader(Account.Id who) {
    uploader = who;
  }

  public Timestamp getCreatedOn() {
    return createdOn;
  }

  public void setCreatedOn(Timestamp ts) {
    createdOn = ts;
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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PatchSet)) {
      return false;
    }
    PatchSet p = (PatchSet) o;
    return Objects.equals(id, p.id)
        && Objects.equals(commitId, p.commitId)
        && Objects.equals(uploader, p.uploader)
        && Objects.equals(createdOn, p.createdOn)
        && Objects.equals(groups, p.groups)
        && Objects.equals(pushCertificate, p.pushCertificate)
        && Objects.equals(description, p.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, commitId, uploader, createdOn, groups, pushCertificate, description);
  }

  @Override
  public String toString() {
    return "[PatchSet " + getId().toString() + "]";
  }
}
