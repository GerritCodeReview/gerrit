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

import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Deque;
import static com.google.common.base.Preconditions.checkState;

public class RefUpdateContext implements AutoCloseable {
  private final static ThreadLocal<Deque<RefUpdateContext>> current = new ThreadLocal<>();

  public enum RefUpdateType {
    UNKNOWN,
    MERGE_CHANGE,
    INIT_REPO,
    INSERT_CHANGES_AND_PATCH_SETS,
    TEST_SETUP,
    INTERNAL_ACTION,
    API_CALL,
    DIRECT_PUSH,
    CREATE_BRANCH,
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
    if(result == null) {
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
    checkState(openedContexts.peekLast() == this, "The current context is different from this context.");
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


