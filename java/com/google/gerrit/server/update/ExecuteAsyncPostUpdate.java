// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.update;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;

/** Executes {@link AsyncPostUpdateOp#asyncPostUpdate(Context)} on a specific op, asynchronously. */
public class ExecuteAsyncPostUpdate implements Runnable, RequestContext {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AsyncPostUpdateOp op;
  private final Context ctx;
  private final CurrentUser user;
  private final ThreadLocalRequestContext threadLocalRequestContext;

  ExecuteAsyncPostUpdate(
      AsyncPostUpdateOp op,
      Context ctx,
      CurrentUser user,
      ThreadLocalRequestContext threadLocalRequestContext) {
    this.op = op;
    this.ctx = ctx;
    this.user = user;
    this.threadLocalRequestContext = threadLocalRequestContext;
  }

  @Override
  public void run() {
    RequestContext old = threadLocalRequestContext.setContext(this);
    try {
      op.asyncPostUpdate(ctx);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Cannot perform async post update for repo %s and user %s",
          ctx.getProject(), ctx.getAccount().account().getName());
    } finally {
      threadLocalRequestContext.setContext(old);
    }
  }

  @Override
  public String toString() {
    return "async-post-update";
  }

  @Override
  public CurrentUser getUser() {
    return user;
  }
}
