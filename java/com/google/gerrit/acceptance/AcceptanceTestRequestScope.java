// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.base.Preconditions.checkState;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;

/** Guice scopes for state during an Acceptance Test connection. */
public class AcceptanceTestRequestScope {

  public static class Context implements RequestContext {
    private final SshSession session;
    private final CurrentUser user;

    private Context(SshSession s, CurrentUser u) {
      session = s;
      user = u;
    }

    public SshSession getSession() {
      return session;
    }

    @Override
    public CurrentUser getUser() {
      if (user == null) {
        throw new IllegalStateException("user == null, forgot to set it?");
      }
      return user;
    }
  }

  private final ThreadLocalRequestContext local;

  @Inject
  AcceptanceTestRequestScope(ThreadLocalRequestContext local) {
    this.local = local;
  }

  public Context newContext(SshSession s, CurrentUser user) {
    return new Context(s, user);
  }

  @CanIgnoreReturnValue
  public Context set(Context ctx) {
    RequestContext old = local.setContext(ctx);
    checkState(
        old == null || old instanceof Context,
        "Previous context must be either not set or has AcceptanceTestRequestScope.Context type");
    return (Context) old;
  }

  public Context get() {
    RequestContext result = local.getContext();
    checkState(
        result == null || result instanceof Context,
        "The current context must be either not set or has AcceptanceTestRequestScope.Context type");
    return (Context) result;
  }
}
