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

import com.google.common.collect.Maps;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.config.RequestScopedReviewDbProvider;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestScopePropagator;
import com.google.gerrit.testutil.DisabledReviewDb;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.util.Providers;

import java.util.Map;

/** Guice scopes for state during an Acceptance Test connection. */
public class AcceptanceTestRequestScope {
  private static final Key<RequestCleanup> RC_KEY =
      Key.get(RequestCleanup.class);

  private static final Key<RequestScopedReviewDbProvider> DB_KEY =
      Key.get(RequestScopedReviewDbProvider.class);

  public abstract static class Context implements RequestContext {
    protected final RequestCleanup cleanup = new RequestCleanup();
    protected final Map<Key<?>, Object> map = Maps.newHashMap();
    protected final SchemaFactory<ReviewDb> schemaFactory;
    protected final SshSession session;

    final long created;
    volatile long started;
    volatile long finished;

    private Context(SchemaFactory<ReviewDb> sf, SshSession s, long at) {
      schemaFactory = sf;
      session = s;
      created = started = finished = at;
      map.put(RC_KEY, cleanup);
      map.put(DB_KEY, new RequestScopedReviewDbProvider(
          schemaFactory,
          Providers.of(cleanup)));
    }

    private Context(Context p, SshSession s) {
      this(p.schemaFactory, s, p.created);
      started = p.started;
      finished = p.finished;
    }

    SshSession getSession() {
      return session;
    }

    @Override
    public Provider<ReviewDb> getReviewDbProvider() {
      return (RequestScopedReviewDbProvider) map.get(DB_KEY);
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
  }

  private static class IdentifiedUserContext extends Context {
    private final IdentifiedUser.GenericFactory userFactory;
    private final Account.Id accountId;

    private IdentifiedUserContext(SchemaFactory<ReviewDb> sf,
        IdentifiedUser.GenericFactory uf, SshSession s, Account.Id a, long at) {
      super(sf, s, at);
      userFactory = uf;
      accountId = a;
    }

    @Override
    public CurrentUser getUser() {
      return userFactory.create(getReviewDbProvider(), accountId);
    }
  }

  private static class AnonymousUserContext extends Context {
    private final Provider<AnonymousUser> userProvider;

    private AnonymousUserContext(SchemaFactory<ReviewDb> sf,
        Provider<AnonymousUser> up, long at) {
      super(sf, null, at);
      userProvider = up;
    }

    @Override
    public CurrentUser getUser() {
      return userProvider.get();
    }
  }

  static class ContextProvider implements Provider<Context> {
    @Override
    public Context get() {
      return requireContext();
    }
  }

  static class SshSessionProvider implements Provider<SshSession> {
    @Override
    public SshSession get() {
      return requireContext().getSession();
    }
  }

  static class Propagator extends ThreadLocalRequestScopePropagator<Context> {
    @Inject
    Propagator(ThreadLocalRequestContext local,
        Provider<RequestScopedReviewDbProvider> dbProviderProvider) {
      super(REQUEST, current, local, dbProviderProvider);
    }

    @Override
    protected Context continuingContext(Context ctx) {
      // The cleanup is not chained, since the RequestScopePropagator executors
      // the Context's cleanup when finished executing.
      return newContinuingContext(ctx);
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

  @Inject
  AcceptanceTestRequestScope(ThreadLocalRequestContext local) {
    this.local = local;
  }

  public static Context newContext(SchemaFactory<ReviewDb> sf,
      IdentifiedUser.GenericFactory uf, SshSession s, Account.Id a) {
    return new IdentifiedUserContext(sf, uf, s, a, TimeUtil.nowMs());
  }

  public static Context newAnonymousContext(SchemaFactory<ReviewDb> sf,
      Provider<AnonymousUser> up) {
    return new AnonymousUserContext(sf, up, TimeUtil.nowMs());
  }

  private static Context newContinuingContext(Context ctx) {
    return newContinuingContext(ctx, ctx.schemaFactory);
  }

  private static Context newContinuingContext(Context ctx,
      SchemaFactory<ReviewDb> sf) {
    if (ctx instanceof IdentifiedUserContext) {
      IdentifiedUserContext ic = (IdentifiedUserContext) ctx;
      return new IdentifiedUserContext(sf, ic.userFactory,
          ic.session, ic.accountId, ic.created);
    } else if (ctx instanceof AnonymousUserContext) {
      AnonymousUserContext ac = (AnonymousUserContext) ctx;
      return new AnonymousUserContext(sf, ac.userProvider, ac.created);
    } else {
      throw new IllegalArgumentException("unexpected Context type: " + ctx);
    }
  }

  public Context set(Context ctx) {
    Context old = current.get();
    current.set(ctx);
    local.setContext(ctx);
    return old;
  }

  public Context get() {
    return current.get();
  }

  public Context disableDb() {
    Context old = current.get();
    SchemaFactory<ReviewDb> sf = new SchemaFactory<ReviewDb>() {
      @Override
      public ReviewDb open() {
        return new DisabledReviewDb();
      }
    };
    Context ctx = newContinuingContext(old, sf);

    current.set(ctx);
    local.setContext(ctx);
    return old;
  }

  /** Returns exactly one instance per command executed. */
  static final Scope REQUEST = new Scope() {
    @Override
    public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
      return new Provider<T>() {
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
      return "Acceptance Test Scope.REQUEST";
    }
  };
}
