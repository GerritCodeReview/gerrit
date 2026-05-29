// Copyright (C) 2012 The Android Open Source Project
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
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.ConvertibleToProto;
import com.google.gerrit.common.Nullable;
import java.util.EnumSet;
import java.util.Set;

@AutoValue
public abstract class NotifyConfig implements Comparable<NotifyConfig> {
  public enum Header {
    TO,
    CC,
    BCC
  }

  @ConvertibleToProto
  public enum NotifyType {
    // sort by name, except 'ALL' which should stay last
    ABANDONED_CHANGES,
    ALL_COMMENTS,
    NEW_CHANGES,
    NEW_PATCHSETS,
    SUBMITTED_CHANGES,

    ALL
  }

  public abstract String getName();

  public abstract ImmutableSet<NotifyType> getNotify();

  @Nullable
  public abstract String getFilter();

  @Nullable
  public abstract Header getHeader();

  public abstract ImmutableSet<GroupReference> getGroups();

  public abstract ImmutableSet<Address> getAddresses();

  public boolean isNotify(NotifyType type) {
    return getNotify().contains(type) || getNotify().contains(NotifyType.ALL);
  }

  public static Builder builder() {
    return new AutoValue_NotifyConfig.Builder()
        .setNotify(ImmutableSet.copyOf(EnumSet.of(NotifyType.ALL)));
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setNotify(Set<NotifyType> newTypes);

    public abstract Builder setFilter(@Nullable String filter);

    public abstract Builder setHeader(Header hdr);

    @CanIgnoreReturnValue
    public Builder addGroup(GroupReference group) {
      groupsBuilder().add(group);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAddress(Address address) {
      addressesBuilder().add(address);
      return this;
    }

    protected abstract ImmutableSet.Builder<GroupReference> groupsBuilder();

    protected abstract ImmutableSet.Builder<Address> addressesBuilder();

    protected abstract NotifyConfig autoBuild();

    protected abstract String getFilter();

    public NotifyConfig build() {
      if ("*".equals(getFilter())) {
        setFilter(null);
      } else {
        setFilter(Strings.emptyToNull(getFilter()));
      }
      return autoBuild();
    }
  }

  @Override
  public final int compareTo(NotifyConfig o) {
    return getName().compareTo(o.getName());
  }

  @Memoized
  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public final boolean equals(Object obj) {
    if (obj instanceof NotifyConfig) {
      return compareTo((NotifyConfig) obj) == 0;
    }
    return false;
  }
}
