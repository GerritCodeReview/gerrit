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

package com.google.gerrit.entities;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/** A single revision of a {@link Change}. */
@AutoValue
public abstract class PatchSet {
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
    return Splitter.on(',').splitToStream(joinedGroups).collect(toImmutableList());
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

    /** Parse a PatchSet.Id from a {@link #refName()} result. */
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

    public String getCommaSeparatedChangeAndPatchSetId() {
      return changeId().toString() + ',' + id();
    }

    public String toRefName() {
      return changeId().refPrefixBuilder().append(id()).toString();
    }

    @Override
    public final String toString() {
      return getCommaSeparatedChangeAndPatchSetId();
    }
  }

  public static Builder builder() {
    return new AutoValue_PatchSet.Builder().groups(ImmutableList.of());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder id(Id id);

    public abstract Id id();

    public abstract Builder commitId(ObjectId commitId);

    public abstract Optional<ObjectId> commitId();

    public abstract Builder uploader(Account.Id uploader);

    public abstract Builder createdOn(Timestamp createdOn);

    public abstract Builder groups(Iterable<String> groups);

    public abstract ImmutableList<String> groups();

    public abstract Builder pushCertificate(Optional<String> pushCertificate);

    public abstract Builder pushCertificate(String pushCertificate);

    public abstract Builder description(Optional<String> description);

    public abstract Builder description(String description);

    public abstract Optional<String> description();

    public abstract PatchSet build();
  }

  /** ID of the patch set. */
  public abstract Id id();

  /**
   * Commit ID of the patch set, also known as the revision.
   *
   * <p>If this is a deserialized instance that was originally serialized by an older version of
   * Gerrit, and the old data erroneously did not include a {@code commitId}, then this method will
   * return {@link ObjectId#zeroId()}.
   */
  public abstract ObjectId commitId();

  /**
   * Account that uploaded the patch set.
   *
   * <p>If this is a deserialized instance that was originally serialized by an older version of
   * Gerrit, and the old data erroneously did not include an {@code uploader}, then this method will
   * return an account ID of 0.
   */
  public abstract Account.Id uploader();

  /**
   * When this patch set was first introduced onto the change.
   *
   * <p>If this is a deserialized instance that was originally serialized by an older version of
   * Gerrit, and the old data erroneously did not include a {@code createdOn}, then this method will
   * return a timestamp of 0.
   */
  public abstract Timestamp createdOn();

  /**
   * Opaque group identifier, usually assigned during creation.
   *
   * <p>This field is actually a comma-separated list of values, as in rare cases involving merge
   * commits a patch set may belong to multiple groups.
   *
   * <p>Changes on the same branch having patch sets with intersecting groups are considered
   * related, as in the "Related Changes" tab.
   */
  public abstract ImmutableList<String> groups();

  /** Certificate sent with a push that created this patch set. */
  public abstract Optional<String> pushCertificate();

  /**
   * Optional user-supplied description for this patch set.
   *
   * <p>When this field is an empty {@code Optional}, the description was never set on the patch
   * set. When this field is present but an empty string, the description was set and later cleared.
   */
  public abstract Optional<String> description();

  /** Patch set number. */
  public int number() {
    return id().get();
  }

  /** Name of the corresponding patch set ref. */
  public String refName() {
    return id().toRefName();
  }
}
