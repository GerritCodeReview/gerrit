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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

/** Metadata that is provided to {@link PerformanceLogger}s as context for performance records. */
@AutoValue
public abstract class Metadata {
  /** The numeric ID of an account. */
  public abstract Optional<Integer> accountId();

  /**
   * The type of an action (ACCOUNT_UPDATE, CHANGE_UPDATE, GROUP_UPDATE, INDEX_QUERY,
   * PLUGIN_UPDATE).
   */
  public abstract Optional<String> actionType();

  /**
   * Number of attempt. The first execution has {@code attempt=1}, the first retry has {@code
   * attempt=2}.
   */
  public abstract Optional<Integer> attempt();

  /** An authentication domain name. */
  public abstract Optional<String> authDomainName();

  /** The name of a branch. */
  public abstract Optional<String> branchName();

  /** Key of an entity in a cache. */
  public abstract Optional<String> cacheKey();

  /** The name of a cache. */
  public abstract Optional<String> cacheName();

  /** The caller that triggered the operation. */
  public abstract Optional<String> caller();

  /** The name of the implementation class. */
  public abstract Optional<String> className();

  /**
   * The reason of a request cancellation (CLIENT_CLOSED_REQUEST, CLIENT_PROVIDED_DEADLINE_EXCEEDED,
   * SERVER_DEADLINE_EXCEEDED).
   */
  public abstract Optional<String> cancellationReason();

  /** The numeric ID of a change. */
  public abstract Optional<Integer> changeId();

  /**
   * The type of change ID which the user used to identify a change (e.g. numeric ID, triplet etc.).
   */
  public abstract Optional<String> changeIdType();

  /** The cause of an error. */
  public abstract Optional<String> cause();

  /** Side where the comment is written: <= 0 for parent, 1 for revision. */
  public abstract Optional<Integer> commentSide();

  /** The SHA1 of a commit. */
  public abstract Optional<String> commit();

  /** Diff algorithm used in diff computation. */
  public abstract Optional<String> diffAlgorithm();

  /** The type of an event. */
  public abstract Optional<String> eventType();

  /** The value of the @Export annotation which was used to register a plugin extension. */
  public abstract Optional<String> exportValue();

  /** Path of a file in a repository. */
  public abstract Optional<String> filePath();

  /** Garbage collector name. */
  public abstract Optional<String> garbageCollectorName();

  /** Git operation (CLONE, FETCH). */
  public abstract Optional<String> gitOperation();

  /** The numeric ID of an internal group. */
  public abstract Optional<Integer> groupId();

  /** The name of a group. */
  public abstract Optional<String> groupName();

  /** The group system being queried. */
  public abstract Optional<String> groupSystem();

  /** The UUID of a group. */
  public abstract Optional<String> groupUuid();

  /** HTTP status response code. */
  public abstract Optional<Integer> httpStatus();

  /** The name of a secondary index. */
  public abstract Optional<String> indexName();

  /** The version of a secondary index. */
  public abstract Optional<Integer> indexVersion();

  /** The name of the implementation method. */
  public abstract Optional<String> memoryPoolName();

  /** The name of the implementation method. */
  public abstract Optional<String> methodName();

  /** One or more resources */
  public abstract Optional<Boolean> multiple();

  /** The name of an operation that is performed. */
  public abstract Optional<String> operationName();

  /** Partial or full computation */
  public abstract Optional<Boolean> partial();

  /** If a value is still current or not */
  public abstract Optional<Boolean> outdated();

  /** Path of a metadata file in NoteDb. */
  public abstract Optional<String> noteDbFilePath();

  /** Name of a metadata ref in NoteDb. */
  public abstract Optional<String> noteDbRefName();

  /** Type of a sequence in NoteDb (ACCOUNTS, CHANGES, GROUPS). */
  public abstract Optional<String> noteDbSequenceType();

  /** The ID of a patch set. */
  public abstract Optional<Integer> patchSetId();

  /** Plugin metadata that doesn't fit into any other category. */
  public abstract ImmutableList<PluginMetadata> pluginMetadata();

  /** The name of a plugin. */
  public abstract Optional<String> pluginName();

  /** The name of a Gerrit project (aka Git repository). */
  public abstract Optional<String> projectName();

  /** The type of a Git push to Gerrit (CREATE_REPLACE, NORMAL, AUTOCLOSE). */
  public abstract Optional<String> pushType();

  /** The type of a Git push to Gerrit (GIT_RECEIVE, GIT_UPLOAD, REST, SSH). */
  public abstract Optional<String> requestType();

  /** The number of resources that is processed. */
  public abstract Optional<Integer> resourceCount();

  /** The name of a REST view. */
  public abstract Optional<String> restViewName();

  public abstract Optional<String> submitRequirementName();

  /** The SHA1 of Git commit. */
  public abstract Optional<String> revision();

  /** The username of an account. */
  public abstract Optional<String> username();

  /**
   * Returns a string representation of this instance that is suitable for logging.
   *
   * <p>{@link #toString()} formats the {@link Optional} fields as {@code key=Optional[value]} or
   * {@code key=Optional.empty}. Since this class has many optional fields from which usually only a
   * few are populated this leads to long string representations such as
   *
   * <pre>
   * Metadata{accountId=Optional.empty, actionType=Optional.empty, attempt=Optional.empty,
   * authDomainName=Optional.empty, branchName=Optional.empty, cacheKey=Optional.empty,
   * cacheName=Optional.empty, caller=Optional.empty, className=Optional.empty,
   * cancellationReason=Optional.empty, changeId=Optional[9212550], changeIdType=Optional.empty,
   * cause=Optional.empty, diffAlgorithm=Optional.empty, eventType=Optional.empty,
   * exportValue=Optional.empty, filePath=Optional.empty, garbageCollectorName=Optional.empty,
   * gitOperation=Optional.empty, groupId=Optional.empty, groupName=Optional.empty,
   * groupUuid=Optional.empty, httpStatus=Optional.empty, indexName=Optional.empty,
   * indexVersion=Optional[0], methodName=Optional.empty, multiple=Optional.empty,
   * operationName=Optional.empty, partial=Optional.empty, noteDbFilePath=Optional.empty,
   * noteDbRefName=Optional.empty, noteDbSequenceType=Optional.empty, patchSetId=Optional.empty,
   * pluginMetadata=[], pluginName=Optional.empty, projectName=Optional.empty,
   * pushType=Optional.empty, requestType=Optional.empty, resourceCount=Optional.empty,
   * restViewName=Optional.empty, revision=Optional.empty, username=Optional.empty}
   * </pre>
   *
   * <p>That's hard to read in logs. This is why this method
   *
   * <ul>
   *   <li>drops fields which have {@code Optional.empty} as value and
   *   <li>reformats values that are {@code Optional[value]} to {@code value}.
   * </ul>
   *
   * <p>For the example given above the formatted string would look like this:
   *
   * <pre>
   * Metadata{changeId=9212550, indexVersion=0}
   * </pre>
   *
   * @return string representation of this instance that is suitable for logging
   */
  public String toStringForLogging() {
    return MoreObjects.toStringHelper("Metadata")
        .omitNullValues()
        .add("accountId", accountId().orElse(null))
        .add("actionType", actionType().orElse(null))
        .add("attempt", attempt().orElse(null))
        .add("authDomainName", authDomainName().orElse(null))
        .add("branchName", branchName().orElse(null))
        .add("cacheKey", cacheKey().orElse(null))
        .add("cacheName", cacheName().orElse(null))
        .add("caller", caller().orElse(null))
        .add("className", className().orElse(null))
        .add("cancellationReason", cancellationReason().orElse(null))
        .add("changeId", changeId().orElse(null))
        .add("changeIdType", changeIdType().orElse(null))
        .add("cause", cause().orElse(null))
        .add("commentSide", commentSide().orElse(null))
        .add("commit", commit().orElse(null))
        .add("diffAlgorithm", diffAlgorithm().orElse(null))
        .add("eventType", eventType().orElse(null))
        .add("exportValue", exportValue().orElse(null))
        .add("filePath", filePath().orElse(null))
        .add("garbageCollectorName", garbageCollectorName().orElse(null))
        .add("gitOperation", gitOperation().orElse(null))
        .add("groupId", groupId().orElse(null))
        .add("groupName", groupName().orElse(null))
        .add("groupSystem", groupSystem().orElse(null))
        .add("groupUuid", groupUuid().orElse(null))
        .add("httpStatus", httpStatus().orElse(null))
        .add("indexName", indexName().orElse(null))
        .add("memoryPoolName", memoryPoolName().orElse(null))
        .add("methodName", methodName().orElse(null))
        .add("multiple", multiple().orElse(null))
        .add("operationName", operationName().orElse(null))
        .add("partial", partial().orElse(null))
        .add("outdated", outdated().orElse(null))
        .add("noteDbFilePath", noteDbFilePath().orElse(null))
        .add("noteDbRefName", noteDbRefName().orElse(null))
        .add("noteDbSequenceType", noteDbSequenceType().orElse(null))
        .add("patchSetId", patchSetId().orElse(null))
        .add(
            "pluginMetadata",
            !pluginMetadata().isEmpty()
                ? pluginMetadata().stream()
                    .map(PluginMetadata::toStringForLogging)
                    .collect(toImmutableList())
                : null)
        .add("pluginName", pluginName().orElse(null))
        .add("projectName", projectName().orElse(null))
        .add("pushType", pushType().orElse(null))
        .add("requestType", requestType().orElse(null))
        .add("resourceCount", resourceCount().orElse(null))
        .add("restViewName", restViewName().orElse(null))
        .add("submitRequirementName", submitRequirementName().orElse(null))
        .add("revision", revision().orElse(null))
        .add("username", username().orElse(null))
        .toString();
  }

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

    public abstract Builder attempt(int attempt);

    public abstract Builder authDomainName(@Nullable String authDomainName);

    public abstract Builder branchName(@Nullable String branchName);

    public abstract Builder cacheKey(@Nullable String cacheKey);

    public abstract Builder cacheName(@Nullable String cacheName);

    public abstract Builder caller(@Nullable String caller);

    public abstract Builder className(@Nullable String className);

    public abstract Builder cancellationReason(@Nullable String cancellationReason);

    public abstract Builder changeId(int changeId);

    public abstract Builder changeIdType(@Nullable String changeIdType);

    public abstract Builder cause(@Nullable String cause);

    public abstract Builder commentSide(int side);

    public abstract Builder commit(@Nullable String commit);

    public abstract Builder diffAlgorithm(@Nullable String diffAlgorithm);

    public abstract Builder eventType(@Nullable String eventType);

    public abstract Builder exportValue(@Nullable String exportValue);

    public abstract Builder filePath(@Nullable String filePath);

    public abstract Builder garbageCollectorName(@Nullable String garbageCollectorName);

    public abstract Builder gitOperation(@Nullable String gitOperation);

    public abstract Builder groupId(int groupId);

    public abstract Builder groupName(@Nullable String groupName);

    public abstract Builder groupSystem(@Nullable String groupSystem);

    public abstract Builder groupUuid(@Nullable String groupUuid);

    public abstract Builder httpStatus(int httpStatus);

    public abstract Builder indexName(@Nullable String indexName);

    public abstract Builder indexVersion(int indexVersion);

    public abstract Builder memoryPoolName(@Nullable String memoryPoolName);

    public abstract Builder methodName(@Nullable String methodName);

    public abstract Builder multiple(boolean multiple);

    public abstract Builder operationName(String operationName);

    public abstract Builder partial(boolean partial);

    public abstract Builder outdated(boolean outdated);

    public abstract Builder noteDbFilePath(@Nullable String noteDbFilePath);

    public abstract Builder noteDbRefName(@Nullable String noteDbRefName);

    public abstract Builder noteDbSequenceType(@Nullable String noteDbSequenceType);

    public abstract Builder patchSetId(int patchSetId);

    abstract ImmutableList.Builder<PluginMetadata> pluginMetadataBuilder();

    @CanIgnoreReturnValue
    public Builder addPluginMetadata(PluginMetadata pluginMetadata) {
      pluginMetadataBuilder().add(pluginMetadata);
      return this;
    }

    public abstract Builder pluginName(@Nullable String pluginName);

    public abstract Builder projectName(@Nullable String projectName);

    public abstract Builder pushType(@Nullable String pushType);

    public abstract Builder requestType(@Nullable String requestType);

    public abstract Builder resourceCount(int resourceCount);

    public abstract Builder restViewName(@Nullable String restViewName);

    public abstract Builder revision(@Nullable String revision);

    public abstract Builder submitRequirementName(@Nullable String srName);

    public abstract Builder username(@Nullable String username);

    public abstract Metadata build();
  }
}
