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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;

public class UpdateContextManager {
  private final static ThreadLocal<UpdateContextManager> threadContextManager = new ThreadLocal<>();

  private final Deque<UpdateContext> ctxStack = new ArrayDeque<>();

  private UpdateContextManager() {}

  public static UpdateContextManager getThreadLocalInstance() {
    UpdateContextManager mgr = threadContextManager.get();
    if(mgr == null) {
      mgr = new UpdateContextManager();
      threadContextManager.set(mgr);
    }
    return mgr;
  }

  public UpdateContext open(UpdateContext ctx) {
    ctxStack.addLast(ctx);
    return ctx;

  }

  public void close(UpdateContext ctx) {
    checkArgument(ctxStack.peekLast()  == ctx, "The passed ctx is not the current one");
    ctxStack.removeLast();
  }

  public boolean hasOpenCtx() {
    return !ctxStack.isEmpty();
  }
}
