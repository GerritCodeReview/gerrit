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
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.LazyArg;
import com.google.common.flogger.LazyArgs;
import com.google.gerrit.common.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/** Metadata that is provided to {@link PerformanceLogger}s as context for performance records. */
@AutoValue
public abstract class Metadata {
  // The numeric ID of an account.
  public abstract Optional<Integer> accountId();

  // The type of an action (ACCOUNT_UPDATE, CHANGE_UPDATE, GROUP_UPDATE, INDEX_QUERY,
  // PLUGIN_UPDATE).
  public abstract Optional<String> actionType();

  // An authentication domain name.
  public abstract Optional<String> authDomainName();

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

  // The type of change ID which the user used to identify a change (e.g. numeric ID, triplet etc.).
  public abstract Optional<String> changeIdType();

  // The type of an event.
  public abstract Optional<String> eventType();

  // The value of the @Export annotation which was used to register a plugin extension.
  public abstract Optional<String> exportValue();

  // Path of a file in a repository.
  public abstract Optional<String> filePath();

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

  // The name of the implementation method.
  public abstract Optional<String> methodName();

  // One or more resources
  public abstract Optional<Boolean> multiple();

  // The name of an operation that is performed.
  public abstract Optional<String> operationName();

  // Partial or full computation
  public abstract Optional<Boolean> partial();

  // Path of a metadata file in NoteDb.
  public abstract Optional<String> noteDbFilePath();

  // Name of a metadata ref in NoteDb.
  public abstract Optional<String> noteDbRefName();

  // Type of a sequence in NoteDb (ACCOUNTS, CHANGES, GROUPS).
  public abstract Optional<String> noteDbSequenceType();

  // Name of a "table" in NoteDb (if set, always CHANGES).
  public abstract Optional<String> noteDbTable();

  // The ID of a patch set.
  public abstract Optional<Integer> patchSetId();

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

  /**
   * Returns a string representation of this instance that is suitable for logging. This is wrapped
   * in a {@link LazyArg} because it is expensive to evaluate.
   *
   * <p>{@link #toString()} formats the {@link Optional} fields as {@code key=Optional[value]} or
   * {@code key=Optional.empty}. Since this class has many optional fields from which usually only a
   * few are populated this leads to long string representations such as
   *
   * <pre>
   * Metadata{accountId=Optional.empty, actionType=Optional.empty, authDomainName=Optional.empty,
   * branchName=Optional.empty, cacheKey=Optional.empty, cacheName=Optional.empty,
   * className=Optional.empty, changeId=Optional[9212550], changeIdType=Optional.empty,
   * eventType=Optional.empty, exportValue=Optional.empty, filePath=Optional.empty,
   * garbageCollectorName=Optional.empty, gitOperation=Optional.empty, groupId=Optional.empty,
   * groupName=Optional.empty, groupUuid=Optional.empty, httpStatus=Optional.empty,
   * indexName=Optional.empty, indexVersion=Optional[0], methodName=Optional.empty,
   * multiple=Optional.empty, operationName=Optional.empty, partial=Optional.empty,
   * noteDbFilePath=Optional.empty, noteDbRefName=Optional.empty,
   * noteDbSequenceType=Optional.empty, noteDbTable=Optional.empty, patchSetId=Optional.empty,
   * pluginMetadata=[], pluginName=Optional.empty, projectName=Optional.empty,
   * pushType=Optional.empty, resourceCount=Optional.empty, restViewName=Optional.empty,
   * revision=Optional.empty, username=Optional.empty}
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
   * Metadata{changeId=9212550, indexVersion=0, pluginMetadata=[]}
   * </pre>
   *
   * @return string representation of this instance that is suitable for logging
   */
  LazyArg<String> toStringForLoggingLazy() {
    // Don't use a lambda because different compilers generate different method names for lambdas,
    // e.g. "lambda$myFunction$0" vs. just "lambda$0" in Eclipse. We need to identify the method
    // by name to skip it and avoid infinite recursion.
    return LazyArgs.lazy(this::toStringForLoggingImpl);
  }

  private String toStringForLoggingImpl() {
    // Append class name.
    String className = getClass().getSimpleName();
    if (className.startsWith("AutoValue_")) {
      className = className.substring(10);
    }
    ToStringHelper stringHelper = MoreObjects.toStringHelper(className);

    // Append key-value pairs for field which are set.
    Method[] methods = Metadata.class.getDeclaredMethods();
    Arrays.sort(methods, Comparator.comparing(Method::getName));
    for (Method method : methods) {
      if (Modifier.isStatic(method.getModifiers())) {
        // skip static method
        continue;
      }

      if (method.getName().equals("toStringForLoggingLazy")
          || method.getName().equals("toStringForLoggingImpl")) {
        // Don't call myself in infinite recursion.
        continue;
      }

      if (method.getReturnType().equals(Void.TYPE) || method.getParameterCount() > 0) {
        // skip method since it's not a getter
        continue;
      }

      method.setAccessible(true);

      Object returnValue;
      try {
        returnValue = method.invoke(this);
      } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
        // should never happen
        throw new IllegalStateException(e);
      }

      if (returnValue instanceof Optional) {
        Optional<?> fieldValueOptional = (Optional<?>) returnValue;
        if (!fieldValueOptional.isPresent()) {
          // drop this key-value pair
          continue;
        }

        // format as 'key=value' instead of 'key=Optional[value]'
        stringHelper.add(method.getName(), fieldValueOptional.get());
      } else {
        // not an Optional value, keep as is
        stringHelper.add(method.getName(), returnValue);
      }
    }

    return stringHelper.toString();
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

    public abstract Builder authDomainName(@Nullable String authDomainName);

    public abstract Builder branchName(@Nullable String branchName);

    public abstract Builder cacheKey(@Nullable String cacheKey);

    public abstract Builder cacheName(@Nullable String cacheName);

    public abstract Builder className(@Nullable String className);

    public abstract Builder changeId(int changeId);

    public abstract Builder changeIdType(@Nullable String changeIdType);

    public abstract Builder eventType(@Nullable String eventType);

    public abstract Builder exportValue(@Nullable String exportValue);

    public abstract Builder filePath(@Nullable String filePath);

    public abstract Builder garbageCollectorName(@Nullable String garbageCollectorName);

    public abstract Builder gitOperation(@Nullable String gitOperation);

    public abstract Builder groupId(int groupId);

    public abstract Builder groupName(@Nullable String groupName);

    public abstract Builder groupUuid(@Nullable String groupUuid);

    public abstract Builder httpStatus(int httpStatus);

    public abstract Builder indexName(@Nullable String indexName);

    public abstract Builder indexVersion(int indexVersion);

    public abstract Builder methodName(@Nullable String methodName);

    public abstract Builder multiple(boolean multiple);

    public abstract Builder operationName(String operationName);

    public abstract Builder partial(boolean partial);

    public abstract Builder noteDbFilePath(@Nullable String noteDbFilePath);

    public abstract Builder noteDbRefName(@Nullable String noteDbRefName);

    public abstract Builder noteDbSequenceType(@Nullable String noteDbSequenceType);

    public abstract Builder noteDbTable(@Nullable String noteDbTable);

    public abstract Builder patchSetId(int patchSetId);

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
