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
import java.util.Optional;

/**
 * Passes additional information about an operation to the {@code BatchRefUpdate#execute} method.
 *
 * <p>To pass the additional information {@link RefUpdateContext}, wraps a code into an open
 * RefUpdateContext, e.g.:
 *
 * <pre>{@code
 * try(RefUpdateContext ctx = RefUpdateContext.open(RefUpdateType.CHANGE_MODIFICATION)) {
 *   ...
 *   // some code which modifies a ref using BatchRefUpdate.execute method
 * }
 * }</pre>
 *
 * When the {@code BatchRefUpdate#execute} method is executed, it can get all opened contexts and
 * use it for an additional actions, e.g. it can put it in the reflog.
 *
 * <p>The information provided by this class is used internally in google.
 *
 * <p>The InMemoryRepositoryManager file makes some validation to ensure that RefUpdateContext is
 * used correctly within the code (see thee validateRefUpdateContext method).
 *
 * <p>The class includes only operations from open-source gerrit and can be extended (see {@code
 * TestActionRefUpdateContext} for example how to extend it).
 */
public class RefUpdateContext implements AutoCloseable {
  private static final ThreadLocal<Deque<RefUpdateContext>> current = new ThreadLocal<>();

  /**
   * List of possible ref-update types.
   *
   * <p>Items in this enum are not fine-grained; different actions are shared the same type (e.g.
   * {@link #CHANGE_MODIFICATION} includes posting comments, change edits and attention set update).
   *
   * <p>It is expected, that each type of operation should include only specific ref(s); check the
   * validateRefUpdateContext in InMemoryRepositoryManager for relation between RefUpdateType and
   * ref name.
   */
  public enum RefUpdateType {
    /**
     * Indicates that the context is implemented as a descendant of the {@link RefUpdateContext} .
     *
     * <p>The {@link #getUpdateType()} returns this type for all descendant of {@link
     * RefUpdateContext}. This type is never returned if the context is exactly {@link
     * RefUpdateContext}.
     */
    OTHER,
    /**
     * A ref is updated as a part of change-related operation.
     *
     * <p>This covers multiple different cases - creating and uploading changes and patchsets,
     * comments operations, change edits, etc...
     */
    CHANGE_MODIFICATION,
    /** A ref is updated during merge-change operation. */
    MERGE_CHANGE,
    /** A ref is updated as a part of a repo sequence operation. */
    REPO_SEQ,
    /** A ref is updated as a part of a repo initialization. */
    INIT_REPO,
    /** A ref is udpated as a part of gpg keys modification. */
    GPG_KEYS_MODIFICATION,
    /** A ref is updated as a part of group(s) update */
    GROUPS_UPDATE,
    /** A ref is updated as a part of account(s) update. */
    ACCOUNTS_UPDATE,
    /** A ref is updated as a part of direct push. */
    DIRECT_PUSH,
    /** A ref is updated as a part of explicit branch or ref update operation. */
    BRANCH_MODIFICATION,
    /** A ref is updated as a part of explicit tag update operation. */
    TAG_MODIFICATION,
    /**
     * A tag is updated as a part of an offline operation.
     *
     * <p>Offline operation - an operation which is executed separately from the gerrit server and
     * can't be triggered by any gerrit API. E.g. schema update.
     */
    OFFLINE_OPERATION,
    /** A tag is updated as a part of an update-superproject flow. */
    UPDATE_SUPERPROJECT,
    /** A ref is updated as a part of explicit HEAD update operation. */
    HEAD_MODIFICATION,
    /** A ref is updated as a part of versioned meta data change. */
    VERSIONED_META_DATA_CHANGE,
    /** A ref is updated as a part of commit-ban operation. */
    BAN_COMMIT,
    /**
     * A ref is updated inside a plugin.
     *
     * <p>If a plugin updates one of a special refs - it must also open a nested context.
     */
    PLUGIN,
    /** A ref is updated as a part of auto-close-changes. */
    AUTO_CLOSE_CHANGES
  }

  /** Opens a provided context. */
  protected static <T extends RefUpdateContext> T open(T ctx) {
    getCurrent().addLast(ctx);
    return ctx;
  }

  /** Opens a context of a give type. */
  public static RefUpdateContext open(RefUpdateType updateType) {
    checkArgument(updateType != RefUpdateType.OTHER, "The OTHER type is for internal use only.");
    checkArgument(
        updateType != RefUpdateType.DIRECT_PUSH,
        "openDirectPush method with justification must be used to open DIRECT_PUSH context.");
    return open(new RefUpdateContext(updateType, Optional.empty()));
  }

  /** Opens a direct push context with an optional justification. */
  public static RefUpdateContext openDirectPush(Optional<String> justification) {
    return open(new RefUpdateContext(RefUpdateType.DIRECT_PUSH, justification));
  }

  /** Returns the list of opened contexts; the first element is the outermost context. */
  public static ImmutableList<RefUpdateContext> getOpenedContexts() {
    return ImmutableList.copyOf(getCurrent());
  }

  /** Checks if there is an open context of the given type. */
  public static boolean hasOpen(RefUpdateType type) {
    return getCurrent().stream().anyMatch(ctx -> ctx.getUpdateType() == type);
  }

  private final RefUpdateType updateType;
  private final Optional<String> justification;

  private RefUpdateContext(RefUpdateType updateType, Optional<String> justification) {
    this.updateType = updateType;
    this.justification = justification;
  }

  protected RefUpdateContext(Optional<String> justification) {
    this(RefUpdateType.OTHER, justification);
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
   * <p>For descendants, always return {@link RefUpdateType#OTHER} (except known descendants defined
   * as nested classes).
   */
  public final RefUpdateType getUpdateType() {
    return updateType;
  }

  /** Returns the justification for the operation. */
  public final Optional<String> getJustification() {
    return justification;
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
