// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.InProcessProtocol.Context;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.config.RequestScopedReviewDbProvider;
import com.google.gerrit.server.git.AsyncReceiveCommits;
import com.google.gerrit.server.git.ReceiveCommits;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.git.validators.UploadValidators;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestScopePropagator;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scope;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PostReceiveHookChain;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.PreUploadHookChain;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.TestProtocol;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

class InProcessProtocol extends TestProtocol<Context> {
  static Module module() {
    return new AbstractModule() {
      @Override
      public void configure() {
        install(new GerritRequestModule());
        bind(RequestScopePropagator.class).to(Propagator.class);
        bindScope(RequestScoped.class, InProcessProtocol.REQUEST);
      }

      @Provides
      @RemotePeer
      SocketAddress getSocketAddress() {
        // TODO(dborowitz): Could potentially fake this with thread ID or
        // something.
        throw new OutOfScopeException("No remote peer in acceptance tests");
      }
    };
  }

  private static final Scope REQUEST =
      new Scope() {
        @Override
        public <T> Provider<T> scope(Key<T> key, Provider<T> creator) {
          return new Provider<T>() {
            @Override
            public T get() {
              Context ctx = current.get();
              if (ctx == null) {
                throw new OutOfScopeException("Not in TestProtocol scope");
              }
              return ctx.get(key, creator);
            }

            @Override
            public String toString() {
              return String.format("%s[%s]", creator, REQUEST);
            }
          };
        }

        @Override
        public String toString() {
          return "InProcessProtocol.REQUEST";
        }
      };

  private static class Propagator extends ThreadLocalRequestScopePropagator<Context> {
    @Inject
    Propagator(
        ThreadLocalRequestContext local,
        Provider<RequestScopedReviewDbProvider> dbProviderProvider) {
      super(REQUEST, current, local, dbProviderProvider);
    }

    @Override
    protected Context continuingContext(Context ctx) {
      return ctx.newContinuingContext();
    }
  }

  private static final ThreadLocal<Context> current = new ThreadLocal<>();

  // TODO(dborowitz): Merge this with AcceptanceTestRequestScope.
  /**
   * Multi-purpose session/context object.
   *
   * <p>Confusingly, Gerrit has two ideas of what a "context" object is: one for Guice {@link
   * RequestScoped}, and one for its own simplified version of request scoping using {@link
   * ThreadLocalRequestContext}. This class provides both, in essence just delegating the {@code
   * ThreadLocalRequestContext} scoping to the Guice scoping mechanism.
   *
   * <p>It is also used as the session type for {@code UploadPackFactory} and {@code
   * ReceivePackFactory}, since, after all, it encapsulates all the information about a single
   * request.
   */
  static class Context implements RequestContext {
    private static final Key<RequestScopedReviewDbProvider> DB_KEY =
        Key.get(RequestScopedReviewDbProvider.class);
    private static final Key<RequestCleanup> RC_KEY = Key.get(RequestCleanup.class);
    private static final Key<CurrentUser> USER_KEY = Key.get(CurrentUser.class);

    private final SchemaFactory<ReviewDb> schemaFactory;
    private final IdentifiedUser.GenericFactory userFactory;
    private final Account.Id accountId;
    private final Project.NameKey project;
    private final RequestCleanup cleanup;
    private final Map<Key<?>, Object> map;

    Context(
        SchemaFactory<ReviewDb> schemaFactory,
        IdentifiedUser.GenericFactory userFactory,
        Account.Id accountId,
        Project.NameKey project) {
      this.schemaFactory = schemaFactory;
      this.userFactory = userFactory;
      this.accountId = accountId;
      this.project = project;
      map = new HashMap<>();
      cleanup = new RequestCleanup();
      map.put(DB_KEY, new RequestScopedReviewDbProvider(schemaFactory, Providers.of(cleanup)));
      map.put(RC_KEY, cleanup);

      IdentifiedUser user = userFactory.create(accountId);
      user.setAccessPath(AccessPath.GIT);
      map.put(USER_KEY, user);
    }

    private Context newContinuingContext() {
      return new Context(schemaFactory, userFactory, accountId, project);
    }

    @Override
    public CurrentUser getUser() {
      return get(USER_KEY, null);
    }

    @Override
    public Provider<ReviewDb> getReviewDbProvider() {
      return get(DB_KEY, null);
    }

    private synchronized <T> T get(Key<T> key, Provider<T> creator) {
      @SuppressWarnings("unchecked")
      T t = (T) map.get(key);
      if (t == null) {
        t = creator.get();
        map.put(key, t);
      }
      return t;
    }
  }

  private static class Upload implements UploadPackFactory<Context> {
    private final Provider<ReviewDb> dbProvider;
    private final Provider<CurrentUser> userProvider;
    private final TagCache tagCache;
    @Nullable private final SearchingChangeCacheImpl changeCache;
    private final ProjectControl.GenericFactory projectControlFactory;
    private final ChangeNotes.Factory changeNotesFactory;
    private final TransferConfig transferConfig;
    private final DynamicSet<PreUploadHook> preUploadHooks;
    private final UploadValidators.Factory uploadValidatorsFactory;
    private final ThreadLocalRequestContext threadContext;

    @Inject
    Upload(
        Provider<ReviewDb> dbProvider,
        Provider<CurrentUser> userProvider,
        TagCache tagCache,
        @Nullable SearchingChangeCacheImpl changeCache,
        ProjectControl.GenericFactory projectControlFactory,
        ChangeNotes.Factory changeNotesFactory,
        TransferConfig transferConfig,
        DynamicSet<PreUploadHook> preUploadHooks,
        UploadValidators.Factory uploadValidatorsFactory,
        ThreadLocalRequestContext threadContext) {
      this.dbProvider = dbProvider;
      this.userProvider = userProvider;
      this.tagCache = tagCache;
      this.changeCache = changeCache;
      this.projectControlFactory = projectControlFactory;
      this.changeNotesFactory = changeNotesFactory;
      this.transferConfig = transferConfig;
      this.preUploadHooks = preUploadHooks;
      this.uploadValidatorsFactory = uploadValidatorsFactory;
      this.threadContext = threadContext;
    }

    @Override
    public UploadPack create(Context req, Repository repo) throws ServiceNotAuthorizedException {
      // Set the request context, but don't bother unsetting, since we don't
      // have an easy way to run code when this instance is done being used.
      // Each operation is run in its own thread, so we don't need to recover
      // its original context anyway.
      threadContext.setContext(req);
      current.set(req);
      try {
        ProjectControl ctl = projectControlFactory.controlFor(req.project, userProvider.get());
        if (!ctl.canRunUploadPack()) {
          throw new ServiceNotAuthorizedException();
        }

        UploadPack up = new UploadPack(repo);
        up.setPackConfig(transferConfig.getPackConfig());
        up.setTimeout(transferConfig.getTimeout());
        up.setAdvertiseRefsHook(
            new VisibleRefFilter(
                tagCache, changeNotesFactory, changeCache, repo, ctl, dbProvider.get(), true));
        List<PreUploadHook> hooks = Lists.newArrayList(preUploadHooks);
        hooks.add(uploadValidatorsFactory.create(ctl.getProject(), repo, "localhost-test"));
        up.setPreUploadHook(PreUploadHookChain.newChain(hooks));
        return up;
      } catch (NoSuchProjectException | IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class Receive implements ReceivePackFactory<Context> {
    private final Provider<CurrentUser> userProvider;
    private final ProjectControl.GenericFactory projectControlFactory;
    private final AsyncReceiveCommits.Factory factory;
    private final TransferConfig config;
    private final DynamicSet<ReceivePackInitializer> receivePackInitializers;
    private final DynamicSet<PostReceiveHook> postReceiveHooks;
    private final ThreadLocalRequestContext threadContext;

    @Inject
    Receive(
        Provider<CurrentUser> userProvider,
        ProjectControl.GenericFactory projectControlFactory,
        AsyncReceiveCommits.Factory factory,
        TransferConfig config,
        DynamicSet<ReceivePackInitializer> receivePackInitializers,
        DynamicSet<PostReceiveHook> postReceiveHooks,
        ThreadLocalRequestContext threadContext) {
      this.userProvider = userProvider;
      this.projectControlFactory = projectControlFactory;
      this.factory = factory;
      this.config = config;
      this.receivePackInitializers = receivePackInitializers;
      this.postReceiveHooks = postReceiveHooks;
      this.threadContext = threadContext;
    }

    @Override
    public ReceivePack create(Context req, Repository db) throws ServiceNotAuthorizedException {
      // Set the request context, but don't bother unsetting, since we don't
      // have an easy way to run code when this instance is done being used.
      // Each operation is run in its own thread, so we don't need to recover
      // its original context anyway.
      threadContext.setContext(req);
      current.set(req);
      try {
        ProjectControl ctl = projectControlFactory.controlFor(req.project, userProvider.get());
        if (!ctl.canRunReceivePack()) {
          throw new ServiceNotAuthorizedException();
        }

        ReceiveCommits rc = factory.create(ctl, db).getReceiveCommits();
        ReceivePack rp = rc.getReceivePack();

        Capable r = rc.canUpload();
        if (r != Capable.OK) {
          throw new ServiceNotAuthorizedException();
        }

        rp.setRefLogIdent(ctl.getUser().asIdentifiedUser().newRefLogIdent());
        rp.setTimeout(config.getTimeout());
        rp.setMaxObjectSizeLimit(config.getMaxObjectSizeLimit());

        for (ReceivePackInitializer initializer : receivePackInitializers) {
          initializer.init(ctl.getProject().getNameKey(), rp);
        }

        rp.setPostReceiveHook(PostReceiveHookChain.newChain(Lists.newArrayList(postReceiveHooks)));
        return rp;
      } catch (NoSuchProjectException | IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Inject
  InProcessProtocol(Upload uploadPackFactory, Receive receivePackFactory) {
    super(uploadPackFactory, receivePackFactory);
  }
}
