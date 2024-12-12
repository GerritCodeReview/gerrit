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

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;

public final class AccountGroup {
  public static NameKey nameKey(String n) {
    return new AutoValue_AccountGroup_NameKey(n);
  }

  /** Group name key */
  @AutoValue
  public abstract static class NameKey implements Comparable<NameKey> {
    abstract String name();

    public String get() {
      return name();
    }

    @Override
    public final int compareTo(NameKey o) {
      return name().compareTo(o.name());
    }

    @Override
    public final String toString() {
      return KeyUtil.encode(get());
    }
  }

  public static UUID uuid(String n) {
    return new AutoValue_AccountGroup_UUID(n);
  }

  /** Globally unique identifier. */
  @AutoValue
  public abstract static class UUID implements Comparable<UUID> {
    abstract String uuid();

    public String get() {
      return uuid();
    }

    public static final UUID EMPTY_UUID = parse("");

    /** Returns true if the UUID is for a group managed within Gerrit. */
    public boolean isInternalGroup() {
      return get().matches("^[0-9a-f]{40}$");
    }

    /** Parse an {@link AccountGroup.UUID} out of a string representation. */
    public static UUID parse(String str) {
      return AccountGroup.uuid(KeyUtil.decode(str));
    }

    /** Parse an {@link AccountGroup.UUID} out of a ref-name. */
    @Nullable
    public static UUID fromRef(String ref) {
      if (ref == null) {
        return null;
      }
      if (ref.startsWith(RefNames.REFS_GROUPS)) {
        return fromRefPart(ref.substring(RefNames.REFS_GROUPS.length()));
      }
      return null;
    }

    /**
     * Parse an {@link AccountGroup.UUID} out of a part of a ref-name.
     *
     * @param refPart a ref name with the following syntax: {@code "12/1234..."}. We assume that the
     *     caller has trimmed any prefix.
     */
    @Nullable
    public static UUID fromRefPart(String refPart) {
      String uuid = RefNames.parseShardedUuidFromRefPart(refPart);
      return uuid != null ? AccountGroup.uuid(uuid) : null;
    }

    @Override
    public final int compareTo(UUID o) {
      return uuid().compareTo(o.uuid());
    }

    @Override
    public final String toString() {
      return KeyUtil.encode(get());
    }
  }

  public static Id id(int id) {
    return new AutoValue_AccountGroup_Id(id);
  }

  /** Synthetic key to link to within the database */
  @AutoValue
  public abstract static class Id {
    abstract int id();

    public int get() {
      return id();
    }

    /** Parse an AccountGroup.Id out of a string representation. */
    public static Id parse(String str) {
      return AccountGroup.id(Integer.parseInt(str));
    }

    @Override
    public final String toString() {
      return Integer.toString(get());
    }
  }
}
