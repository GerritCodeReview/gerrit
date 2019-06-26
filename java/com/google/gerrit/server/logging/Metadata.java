// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

/** Metadata that is provided to {@link PerformanceLogger}s as context for performance records. */
@AutoValue
public abstract class Metadata {
  // The numeric ID of an account.
  public abstract Optional<Integer> accountId();

  // The type of an action (ACCOUNT_UPDATE, CHANGE_UPDATE, GROUP_UPDATE, INDEX_QUERY,
  // PLUGIN_UPDATE).
  public abstract Optional<String> actionType();

  // The name of a branch.
  public abstract Optional<String> branchName();

  // Key of an entity in a cache.
  public abstract Optional<String> cacheKey();

  // The name of a cache.
  public abstract Optional<String> cacheName();

  // The name of the implementation class.
  public abstract Optional<String> className();

  // The numeric ID of a change.
  public abstract Optional<Integer> changeId();

  // The type of change ID (e.g. numeric ID, triplet etc.).
  public abstract Optional<String> changeIdType();

  // The type of an event.
  public abstract Optional<String> eventType();

  // The name under which a plugin extension was registered.
  public abstract Optional<String> exportName();

  // Garbage collector name.
  public abstract Optional<String> garbageCollectorName();

  // Git operation (CLONE, FETCH).
  public abstract Optional<String> gitOperation();

  // The numeric ID of an internal group.
  public abstract Optional<Integer> groupId();

  // The name of a group.
  public abstract Optional<String> groupName();

  // The UUID of a group.
  public abstract Optional<String> groupUuid();

  // HTTP status response code.
  public abstract Optional<Integer> httpStatus();

  // The name of a secondary index.
  public abstract Optional<String> indexName();

  // The version of a secondary index.
  public abstract Optional<Integer> indexVersion();

  // An LDAP domain name.
  public abstract Optional<String> ldapDomainName();

  // The name of the implementation method.
  public abstract Optional<String> methodName();

  // Boolean: one or more
  public abstract Optional<Boolean> multiple();

  // Name of a metadata file in NoteDb.
  public abstract Optional<String> noteDbFileName();

  // Name of a metadata ref in NoteDb.
  public abstract Optional<String> noteDbRefName();

  // Type of a sequence in NoteDb (ACCOUNTS, CHANGES, GROUPS).
  public abstract Optional<String> noteDbSequenceType();

  // Name of a "table" in NoteDb (if set, always CHANGES).
  public abstract Optional<String> noteDbTable();

  // Plugin metadata that doesn't fit into any other category.
  public abstract ImmutableList<PluginMetadata> pluginMetadata();

  // The name of a plugin.
  public abstract Optional<String> pluginName();

  // The name of a Gerrit project (aka Git repository).
  public abstract Optional<String> projectName();

  // The type of a Git push to Gerrit (CREATE_REPLACE, NORMAL, AUTOCLOSE).
  public abstract Optional<String> pushType();

  // The number of resources that is processed.
  public abstract Optional<Integer> resourceCount();

  // The name of a REST view.
  public abstract Optional<String> restViewName();

  // The SHA1 of Git commit.
  public abstract Optional<String> revision();

  // The username of an account.
  public abstract Optional<String> username();

  public static Metadata.Builder builder() {
    return new AutoValue_Metadata.Builder();
  }

  public static Metadata empty() {
    return builder().build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder accountId(int accountId);

    public abstract Builder actionType(@Nullable String actionType);

    public abstract Builder branchName(@Nullable String branchName);

    public abstract Builder cacheKey(@Nullable String cacheKey);

    public abstract Builder cacheName(@Nullable String cacheName);

    public abstract Builder className(@Nullable String className);

    public abstract Builder changeId(int changeId);

    public abstract Builder changeIdType(@Nullable String changeIdType);

    public abstract Builder eventType(@Nullable String eventType);

    public abstract Builder exportName(@Nullable String exportName);

    public abstract Builder garbageCollectorName(@Nullable String garbageCollectorName);

    public abstract Builder gitOperation(@Nullable String gitOperation);

    public abstract Builder groupId(int groupId);

    public abstract Builder groupName(@Nullable String groupName);

    public abstract Builder groupUuid(@Nullable String groupUuid);

    public abstract Builder httpStatus(int httpStatus);

    public abstract Builder indexName(@Nullable String indexName);

    public abstract Builder indexVersion(int indexVersion);

    public abstract Builder ldapDomainName(@Nullable String ldapDomainName);

    public abstract Builder methodName(@Nullable String methodName);

    public abstract Builder multiple(boolean multiple);

    public abstract Builder noteDbFileName(@Nullable String noteDbFileName);

    public abstract Builder noteDbRefName(@Nullable String noteDbRefName);

    public abstract Builder noteDbSequenceType(@Nullable String noteDbSequenceType);

    public abstract Builder noteDbTable(@Nullable String noteDbTable);

    abstract ImmutableList.Builder<PluginMetadata> pluginMetadataBuilder();

    public Builder addPluginMetadata(PluginMetadata pluginMetadata) {
      pluginMetadataBuilder().add(pluginMetadata);
      return this;
    }

    public abstract Builder pluginName(@Nullable String pluginName);

    public abstract Builder projectName(@Nullable String projectName);

    public abstract Builder pushType(@Nullable String pushType);

    public abstract Builder resourceCount(int resourceCount);

    public abstract Builder restViewName(@Nullable String restViewName);

    public abstract Builder revision(@Nullable String revision);

    public abstract Builder username(@Nullable String username);

    public abstract Metadata build();
  }
}
