// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.update.context;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Deque;


/**
 * Allows to pass additional information about an operation which modifies a ref.
 *
 * Usage:
 * <pre>
 *   try(RefUpdateContext ctx = RefUpdateContext.open(RefUpdateType.CHANGE_MODIFICATION)) {
 *     // some code which modifies a ref
 *     ...
 *   }
 * </pre>
 * The RefUpdateContext can be nested.
 *
 * The class can be extended (see {@link TestActionRefUpdateContext} for example how to extend it).
 */
public class RefUpdateContext implements AutoCloseable {
  private static final ThreadLocal<Deque<RefUpdateContext>> current = new ThreadLocal<>();

  public enum RefUpdateType {
    /**
     * Indicates that the context is implemented as a descendant of the {@link RefUpdateContext} .
     *
     * The {@link #getUpdateType()} returns this type for all descendant of {@link RefUpdateContext}.
     * This type is never returned if the context is exactly {@link RefUpdateContext}.
     */
    OTHER,
    /**
     * A ref is updated as a part of change-related operation.
     *
     * This covers multiple different cases - creating and uploading changes and patchsets,
     * comments operations, change edits, etc...
     */
    CHANGE_MODIFICATION,
    /**
     * A ref is updated during merge-change operation.
     */
    MERGE_CHANGE,
    /**
     * A ref is updated as a part of a repo sequence operation.
     */
    REPO_SEQ,
    /** A ref is updated as a part of a repo initialization. */
    INIT_REPO,
    /** A ref is udpated as a part of gpg keys modification. */
    GPG_KEYS_MODIFICATION,
    /** A ref is updated as a part  */
    GROUPS_UPDATE,
    ACCOUNTS_UPDATE,
    DIRECT_PUSH,
    BRANCH_MODIFICATION,
    TAG_MODIFICATION,
    OFFLINE_OPERATION,
    UPDATE_SUPERPROJECT,
    HEAD_MODIFICATION,

    VERSIONED_META_DATA_CHANGE,
    BAN_COMMIT
  }

  /** Opens a provided context. */
  protected static <T extends RefUpdateContext> T open(T ctx) {
    getCurrent().addLast(ctx);
    return ctx;
  }

  /** Opens a context of a give type. */
  public static RefUpdateContext open(RefUpdateType updateType) {
    checkArgument(updateType != RefUpdateType.OTHER, "The OTHER type is for internal use only.");
    return open(new RefUpdateContext(updateType));
  }

  /** Returns the list of opened contexts; the first element is the outermost context.  */
  public static ImmutableList<RefUpdateContext> getOpenedContexts() {
    return ImmutableList.copyOf(getCurrent());
  }

  /** Checks if there is an open context of the given type. */
  public static boolean hasOpen(RefUpdateType type) {
    return getCurrent().stream().anyMatch(ctx -> ctx.getUpdateType() == type);
  }


  private final RefUpdateType updateType;

  private RefUpdateContext(RefUpdateType updateType) {
    this.updateType = updateType;
  }

  protected RefUpdateContext() {
    this(RefUpdateType.OTHER);
  }

  protected static final Deque<RefUpdateContext> getCurrent() {
    Deque<RefUpdateContext> result = current.get();
    if (result == null) {
      result = new ArrayDeque<>();
      current.set(result);
    }
    return result;
  }


  /**
   * Returns the type of {@link RefUpdateContext}.
   *
   * For descendants, always return {@link RefUpdateType#OTHER}
   */
  public final RefUpdateType getUpdateType() {
    return updateType;
  }

  /** Closes the current context. */
  @Override
  public void close() {
    Deque<RefUpdateContext> openedContexts = getCurrent();
    checkState(
        openedContexts.peekLast() == this, "The current context is different from this context.");
    openedContexts.removeLast();
  }
}
