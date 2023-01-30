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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayDeque;
import java.util.Deque;

public class RefUpdateContext implements AutoCloseable {
  private static final ThreadLocal<Deque<RefUpdateContext>> current = new ThreadLocal<>();

  public enum RefUpdateType {
    UNKNOWN,
    MERGE_CHANGE,
    REPO_SEQ,
    INIT_REPO,
    GPG_KEYS_MODIFICATION,
    TEST_SETUP,
    INTERNAL_ACTION,
    API_CALL,
    DIRECT_PUSH,
    BRANCH_MODIFICATION,
    TAG_MODIFICATION,
    CONSISTENCY_CHECKER_FIX,
    OFFLINE_OPERATION,
    AFTER_CHANGE_SUBMITTED,
    UPDATE_SUPERPROJECT,
    HEAD_MODIFICATION,
    CHANGE_MODIFICATION,
    CHANGE_EDIT,
    VERSIONED_META_DATA_CHANGE,
    BAN_COMMIT
  }

  @CanIgnoreReturnValue
  public static <V, E extends Exception> V testSetup(CallableWithException<V, E> c) throws E {
    try (RefUpdateContext ctx = RefUpdateContext.open(RefUpdateType.TEST_SETUP)) {
      return c.call();
    }
  }

  public interface CallableWithException<V, E extends Exception> {
    V call() throws E;
  }

  @FunctionalInterface
  public interface RunnableWithException<E extends Exception> {
    void run() throws E;
  }

  public static <E extends Exception> void testSetup(RunnableWithException<E> c) throws E {
    try (RefUpdateContext ctx = RefUpdateContext.open(RefUpdateType.TEST_SETUP)) {
      c.run();
    }
  }

  private static RefUpdateContext open(RefUpdateContext ctx) {
    getCurrent().addLast(ctx);
    return ctx;
  }

  public static RefUpdateContext open(RefUpdateType updateType) {
    return open(new RefUpdateContext(updateType));
  }

  private final RefUpdateType updateType;

  private RefUpdateContext(RefUpdateType updateType) {
    this.updateType = updateType;
  }

  protected RefUpdateContext() {
    this(RefUpdateType.UNKNOWN);
  }

  protected static final Deque<RefUpdateContext> getCurrent() {
    Deque<RefUpdateContext> result = current.get();
    if (result == null) {
      result = new ArrayDeque<>();
      current.set(result);
    }
    return result;
  }

  public RefUpdateType getUpdateType() {
    return updateType;
  }

  @Override
  public void close() {
    Deque<RefUpdateContext> openedContexts = getCurrent();
    checkState(
        openedContexts.peekLast() == this, "The current context is different from this context.");
    openedContexts.removeLast();
  }

  public static boolean hasOpenContexts() {
    return !getCurrent().isEmpty();
  }

  public static ImmutableList<RefUpdateContext> getOpenedContexts() {
    return ImmutableList.copyOf(getCurrent());
  }

  public static boolean hasOpen(RefUpdateType type) {
    return getCurrent().stream().anyMatch(ctx -> ctx.getUpdateType() == type);
  }
}
