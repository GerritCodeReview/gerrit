// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group.db;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.Set;

/**
 * Definition of a group update that should be performed.
 *
 * <p>An {@code InternalGroupUpdate} only specifies the modifications which should be applied to a
 * group. Each of the modifications and hence each call on {@link InternalGroupUpdate.Builder} is
 * optional.
 *
 * <p>If an {@code InternalGroupUpdate} is passed next to an {@link InternalGroupCreation} during a
 * group creation, the modifications specified within this class are treated as optional properties.
 * Properties which show up both in {@code InternalGroupCreation} and {@code InternalGroupUpdate}
 * such as the name of a group are treated as the same property. Typically, such properties would
 * only be specified in {@code InternalGroupCreation}. If they are specified additionally in {@code
 * InternalGroupUpdate}, their value in {@code InternalGroupUpdate} will be used for the new group.
 */
@AutoValue
public abstract class InternalGroupUpdate {

  /** Representation of a member modification as defined by {@link #apply(ImmutableSet)}. */
  @FunctionalInterface
  public interface MemberModification {

    /**
     * Applies the modification to the given members.
     *
     * @param originalMembers current members of the group. If used for a group creation, this set
     *     is empty.
     * @return the desired resulting members (not the diff of the members!)
     */
    Set<Account.Id> apply(ImmutableSet<Account.Id> originalMembers);
  }

  @FunctionalInterface
  public interface SubgroupModification {
    /**
     * Applies the modification to the given subgroups.
     *
     * @param originalSubgroups current subgroups of the group. If used for a group creation, this
     *     set is empty.
     * @return the desired resulting subgroups (not the diff of the subgroups!)
     */
    Set<AccountGroup.UUID> apply(ImmutableSet<AccountGroup.UUID> originalSubgroups);
  }

  /** Defines the new name of the group. If not specified, the name remains unchanged. */
  public abstract Optional<AccountGroup.NameKey> getName();

  /**
   * Defines the new description of the group. If not specified, the description remains unchanged.
   *
   * <p><strong>Note: </strong>To unset the description of a group, the empty string (not null!)
   * must be.
   */
  public abstract Optional<String> getDescription();

  /** Defines the new owner of the group. If not specified, the owner remains unchanged. */
  public abstract Optional<AccountGroup.UUID> getOwnerGroupUUID();

  /**
   * Defines the new state of the 'visibleToAll' flag of the group. If not specified, the flag
   * remains unchanged.
   */
  public abstract Optional<Boolean> getVisibleToAll();

  /**
   * Defines how the members of the group should be modified. By default (that is if nothing is
   * specified), the members remain unchanged.
   *
   * @return a {@link MemberModification} which gets the current members of the group as input and
   *     outputs the desired resulting members
   */
  public abstract MemberModification getMemberModification();

  /**
   * Defines how the subgroups of the group should be modified. By default (that is if nothing is
   * specified), the subgroups remain unchanged.
   *
   * @return a {@link SubgroupModification} which gets the current subgroups of the group as input
   *     and outputs the desired resulting subgroups
   */
  public abstract SubgroupModification getSubgroupModification();

  /**
   * Defines the {@code Timestamp} to be used for the NoteDb commits of the update. If not
   * specified, the current {@code Timestamp} when creating the commit will be used.
   *
   * <p>If this {@code InternalGroupUpdate} is passed next to an {@link InternalGroupCreation}
   * during a group creation, this {@code Timestamp} is used for the NoteDb commits of the new
   * group. Hence, the {@link com.google.gerrit.server.group.InternalGroup#getCreatedOn()
   * InternalGroup#getCreatedOn()} field will match this {@code Timestamp}.
   *
   * <p><strong>Note: </strong>{@code Timestamp}s of NoteDb commits for groups are used for events
   * in the audit log. For this reason, specifying this field will have an effect on the resulting
   * audit log.
   */
  public abstract Optional<Timestamp> getUpdatedOn();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_InternalGroupUpdate.Builder()
        .setMemberModification(in -> in)
        .setSubgroupModification(in -> in);
  }

  /** A builder for an {@link InternalGroupUpdate}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** @see #getName() */
    public abstract Builder setName(AccountGroup.NameKey name);

    /** @see #getDescription() */
    public abstract Builder setDescription(String description);

    /** @see #getOwnerGroupUUID() */
    public abstract Builder setOwnerGroupUUID(AccountGroup.UUID ownerGroupUUID);

    /** @see #getVisibleToAll() */
    public abstract Builder setVisibleToAll(boolean visibleToAll);

    /** @see #getMemberModification() */
    public abstract Builder setMemberModification(MemberModification memberModification);

    /**
     * Returns the currently defined {@link MemberModification} for the prospective {@link
     * InternalGroupUpdate}.
     *
     * <p>This modification can be tweaked further and passed to {@link
     * #setMemberModification(MemberModification)} in order to combine multiple member additions,
     * deletions, or other modifications into one update.
     */
    abstract MemberModification getMemberModification();

    /** @see #getSubgroupModification() */
    public abstract Builder setSubgroupModification(SubgroupModification subgroupModification);

    /**
     * Returns the currently defined {@link SubgroupModification} for the prospective {@link
     * InternalGroupUpdate}.
     *
     * <p>This modification can be tweaked further and passed to {@link
     * #setSubgroupModification(SubgroupModification)} in order to combine multiple subgroup
     * additions, deletions, or other modifications into one update.
     */
    abstract SubgroupModification getSubgroupModification();

    /** @see #getUpdatedOn() */
    public abstract Builder setUpdatedOn(Timestamp timestamp);

    public abstract InternalGroupUpdate build();
  }
}
