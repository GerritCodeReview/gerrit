// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.gerrit.metrics.proc.ThreadMXBeanFactory;
import com.google.gerrit.metrics.proc.ThreadMXBeanInterface;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestScopePropagator;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import java.util.HashMap;
import java.util.Map;

/** Guice scopes for state during an SSH connection. */
public class SshScope {
  private static final Key<RequestCleanup> RC_KEY = Key.get(RequestCleanup.class);
  private static final ThreadMXBeanInterface threadMxBean = ThreadMXBeanFactory.create();

  class Context implements RequestContext {

    private final RequestCleanup cleanup = new RequestCleanup();
    private final Map<Key<?>, Object> map = new HashMap<>();
    private final SshSession session;
    private final String commandLine;

    private final long created;
    private volatile long started;
    private volatile long finished;
    private volatile long startedTotalCpu;
    private volatile long finishedTotalCpu;
    private volatile long startedUserCpu;
    private volatile long finishedUserCpu;
    private volatile long startedMemory;
    private volatile long finishedMemory;
    private volatile boolean forceTracing;
    private volatile String traceId;

    private IdentifiedUser identifiedUser;

    private Context(SshSession s, String c, long at) {
      session = s;
      commandLine = c;
      created = started = finished = at;
      startedTotalCpu = threadMxBean.getCurrentThreadCpuTime();
      startedUserCpu = threadMxBean.getCurrentThreadUserTime();
      startedMemory = threadMxBean.getCurrentThreadAllocatedBytes();
      map.put(RC_KEY, cleanup);
    }

    private Context(Context p, SshSession s, String c) {
      this(s, c, p.created);
      started = p.started;
      finished = p.finished;
      startedTotalCpu = p.startedTotalCpu;
      finishedTotalCpu = p.finishedTotalCpu;
      startedUserCpu = p.startedUserCpu;
      finishedUserCpu = p.finishedUserCpu;
      startedMemory = p.startedMemory;
      finishedMemory = p.finishedMemory;
    }

    void start() {
      started = TimeUtil.nowMs();
      startedTotalCpu = threadMxBean.getCurrentThreadCpuTime();
      startedUserCpu = threadMxBean.getCurrentThreadUserTime();
      startedMemory = threadMxBean.getCurrentThreadAllocatedBytes();
    }

    void finish() {
      finished = TimeUtil.nowMs();
      finishedTotalCpu = threadMxBean.getCurrentThreadCpuTime();
      finishedUserCpu = threadMxBean.getCurrentThreadUserTime();
      finishedMemory = threadMxBean.getCurrentThreadAllocatedBytes();
    }

    public long getCreated() {
      return created;
    }

    public long getWait() {
      return started - created;
    }

    public long getExec() {
      return finished - started;
    }

    public long getTotalCpu() {
      return (finishedTotalCpu - startedTotalCpu) / 1_000_000;
    }

    public long getUserCpu() {
      return (finishedUserCpu - startedUserCpu) / 1_000_000;
    }

    public long getAllocatedMemory() {
      return finishedMemory - startedMemory;
    }

    String getCommandLine() {
      return commandLine;
    }

    SshSession getSession() {
      return session;
    }

    void setForceTracing(boolean v) {
      forceTracing = v;
    }

    boolean getForceTracing() {
      return forceTracing;
    }

    void setTraceId(String id) {
      traceId = id;
    }

    String getTraceId() {
      return traceId;
    }

    @Override
    public CurrentUser getUser() {
      CurrentUser user = session.getUser();
      if (user != null && user.isIdentifiedUser()) {
        if (identifiedUser == null) {
          identifiedUser = userFactory.create(user.getAccountId());
          identifiedUser.setAccessPath(user.getAccessPath());
        }
        return identifiedUser;
      }
      return user;
    }

    synchronized <T> T get(Key<T> key, Provider<T> creator) {
      @SuppressWarnings("unchecked")
      T t = (T) map.get(key);
      if (t == null) {
        t = creator.get();
        map.put(key, t);
      }
      return t;
    }

    synchronized Context subContext(SshSession newSession, String newCommandLine) {
      Context ctx = new Context(this, newSession, newCommandLine);
      ctx.cleanup.add(cleanup);
      return ctx;
    }
  }

  static class ContextProvider implements Provider<Context> {
    @Override
    public Context get() {
      return requireContext();
    }
  }

  public static class SshSessionProvider implements Provider<SshSession> {
    @Override
    public SshSession get() {
      return requireContext().getSession();
    }
  }

  static class Propagator extends ThreadLocalRequestScopePropagator<Context> {
    private final SshScope sshScope;

    @Inject
    Propagator(SshScope sshScope, ThreadLocalRequestContext local) {
      super(REQUEST, current, local);
      this.sshScope = sshScope;
    }

    @Override
    protected Context continuingContext(Context ctx) {
      // The cleanup is not chained, since the RequestScopePropagator executors
      // the Context's cleanup when finished executing.
      return sshScope.newContinuingContext(ctx);
    }
  }

  private static final ThreadLocal<Context> current = new ThreadLocal<>();

  private static Context requireContext() {
    final Context ctx = current.get();
    if (ctx == null) {
      throw new OutOfScopeException("Not in command/request");
    }
    return ctx;
  }

  private final ThreadLocalRequestContext local;
  private final IdentifiedUser.RequestFactory userFactory;

  @Inject
  SshScope(ThreadLocalRequestContext local, IdentifiedUser.RequestFactory userFactory) {
    this.local = local;
    this.userFactory = userFactory;
  }

  Context newContext(SshSession s, String cmd) {
    return new Context(s, cmd, TimeUtil.nowMs());
  }

  private Context newContinuingContext(Context ctx) {
    return new Context(ctx, ctx.getSession(), ctx.getCommandLine());
  }

  Context set(Context ctx) {
    Context old = current.get();
    current.set(ctx);

    @SuppressWarnings("unused")
    var unused = local.setContext(ctx);

    return old;
  }

  /** Returns exactly one instance per command executed. */
  public static final Scope REQUEST =
      new Scope() {
        @Override
        public <T> Provider<T> scope(Key<T> key, Provider<T> creator) {
          return new Provider<>() {
            @Override
            public T get() {
              return requireContext().get(key, creator);
            }

            @Override
            public String toString() {
              return String.format("%s[%s]", creator, REQUEST);
            }
          };
        }

        @Override
        public String toString() {
          return "SshScopes.REQUEST";
        }
      };
}
