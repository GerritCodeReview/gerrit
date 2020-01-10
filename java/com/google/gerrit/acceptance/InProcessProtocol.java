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

import static com.google.gerrit.server.git.receive.LazyPostReceiveHookChain.affectsSize;
import static com.google.gerrit.server.quota.QuotaGroupDefinitions.REPOSITORY_SIZE_GROUP;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.InProcessProtocol.Context;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.git.PermissionAwareRepositoryManager;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.UploadPackInitializer;
import com.google.gerrit.server.git.UsersSelfAdvertiseRefsHook;
import com.google.gerrit.server.git.receive.AsyncReceiveCommits;
import com.google.gerrit.server.git.validators.UploadValidators;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.gerrit.server.quota.QuotaException;
import com.google.gerrit.server.quota.QuotaResponse;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestScopePropagator;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scope;
import com.google.inject.servlet.RequestScoped;
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
    Propagator(ThreadLocalRequestContext local) {
      super(REQUEST, current, local);
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
    private static final Key<RequestCleanup> RC_KEY = Key.get(RequestCleanup.class);
    private static final Key<CurrentUser> USER_KEY = Key.get(CurrentUser.class);

    private final IdentifiedUser.GenericFactory userFactory;
    private final Account.Id accountId;
    private final Project.NameKey project;
    private final RequestCleanup cleanup;
    private final Map<Key<?>, Object> map;

    Context(
        IdentifiedUser.GenericFactory userFactory, Account.Id accountId, Project.NameKey project) {
      this.userFactory = userFactory;
      this.accountId = accountId;
      this.project = project;
      map = new HashMap<>();
      cleanup = new RequestCleanup();
      map.put(RC_KEY, cleanup);

      IdentifiedUser user = userFactory.create(accountId);
      user.setAccessPath(AccessPath.GIT);
      map.put(USER_KEY, user);
    }

    private Context newContinuingContext() {
      return new Context(userFactory, accountId, project);
    }

    @Override
    public CurrentUser getUser() {
      return get(USER_KEY, null);
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
    private final TransferConfig transferConfig;
    private final PluginSetContext<UploadPackInitializer> uploadPackInitializers;
    private final DynamicSet<PreUploadHook> preUploadHooks;
    private final UploadValidators.Factory uploadValidatorsFactory;
    private final ThreadLocalRequestContext threadContext;
    private final ProjectCache projectCache;
    private final PermissionBackend permissionBackend;
    private final UsersSelfAdvertiseRefsHook usersSelfAdvertiseRefsHook;

    @Inject
    Upload(
        TransferConfig transferConfig,
        PluginSetContext<UploadPackInitializer> uploadPackInitializers,
        DynamicSet<PreUploadHook> preUploadHooks,
        UploadValidators.Factory uploadValidatorsFactory,
        ThreadLocalRequestContext threadContext,
        ProjectCache projectCache,
        PermissionBackend permissionBackend,
        UsersSelfAdvertiseRefsHook usersSelfAdvertiseRefsHook) {
      this.transferConfig = transferConfig;
      this.uploadPackInitializers = uploadPackInitializers;
      this.preUploadHooks = preUploadHooks;
      this.uploadValidatorsFactory = uploadValidatorsFactory;
      this.threadContext = threadContext;
      this.projectCache = projectCache;
      this.permissionBackend = permissionBackend;
      this.usersSelfAdvertiseRefsHook = usersSelfAdvertiseRefsHook;
    }

    @Override
    public UploadPack create(Context req, Repository repo) throws ServiceNotAuthorizedException {
      // Set the request context, but don't bother unsetting, since we don't
      // have an easy way to run code when this instance is done being used.
      // Each operation is run in its own thread, so we don't need to recover
      // its original context anyway.
      threadContext.setContext(req);
      current.set(req);

      PermissionBackend.ForProject perm = permissionBackend.currentUser().project(req.project);
      try {
        perm.check(ProjectPermission.RUN_UPLOAD_PACK);
      } catch (AuthException e) {
        throw new ServiceNotAuthorizedException(e.getMessage(), e);
      } catch (PermissionBackendException e) {
        throw new RuntimeException(e);
      }

      ProjectState projectState;
      try {
        projectState = projectCache.checkedGet(req.project);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (projectState == null) {
        throw new RuntimeException("can't load project state for " + req.project.get());
      }
      Repository permissionAwareRepository = PermissionAwareRepositoryManager.wrap(repo, perm);
      UploadPack up = new UploadPack(permissionAwareRepository);
      up.setPackConfig(transferConfig.getPackConfig());
      up.setTimeout(transferConfig.getTimeout());
      if (projectState.isAllUsers()) {
        up.setAdvertiseRefsHook(usersSelfAdvertiseRefsHook);
      }
      List<PreUploadHook> hooks = Lists.newArrayList(preUploadHooks);
      hooks.add(
          uploadValidatorsFactory.create(
              projectState.getProject(), permissionAwareRepository, "localhost-test"));
      up.setPreUploadHook(PreUploadHookChain.newChain(hooks));
      uploadPackInitializers.runEach(initializer -> initializer.init(req.project, up));
      return up;
    }
  }

  private static class Receive implements ReceivePackFactory<Context> {
    private final Provider<CurrentUser> userProvider;
    private final ProjectCache projectCache;
    private final AsyncReceiveCommits.Factory factory;
    private final TransferConfig config;
    private final PluginSetContext<ReceivePackInitializer> receivePackInitializers;
    private final DynamicSet<PostReceiveHook> postReceiveHooks;
    private final ThreadLocalRequestContext threadContext;
    private final PermissionBackend permissionBackend;
    private final QuotaBackend quotaBackend;

    @Inject
    Receive(
        Provider<CurrentUser> userProvider,
        ProjectCache projectCache,
        AsyncReceiveCommits.Factory factory,
        TransferConfig config,
        PluginSetContext<ReceivePackInitializer> receivePackInitializers,
        DynamicSet<PostReceiveHook> postReceiveHooks,
        ThreadLocalRequestContext threadContext,
        PermissionBackend permissionBackend,
        QuotaBackend quotaBackend) {
      this.userProvider = userProvider;
      this.projectCache = projectCache;
      this.factory = factory;
      this.config = config;
      this.receivePackInitializers = receivePackInitializers;
      this.postReceiveHooks = postReceiveHooks;
      this.threadContext = threadContext;
      this.permissionBackend = permissionBackend;
      this.quotaBackend = quotaBackend;
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
        permissionBackend
            .currentUser()
            .project(req.project)
            .check(ProjectPermission.RUN_RECEIVE_PACK);
      } catch (AuthException e) {
        throw new ServiceNotAuthorizedException(e.getMessage(), e);
      } catch (PermissionBackendException e) {
        throw new RuntimeException(e);
      }
      try {
        IdentifiedUser identifiedUser = userProvider.get().asIdentifiedUser();
        ProjectState projectState = projectCache.checkedGet(req.project);
        if (projectState == null) {
          throw new RuntimeException(String.format("project %s not found", req.project));
        }

        AsyncReceiveCommits arc = factory.create(projectState, identifiedUser, db, null);
        if (arc.canUpload() != Capable.OK) {
          throw new ServiceNotAuthorizedException();
        }

        ReceivePack rp = arc.getReceivePack();
        rp.setRefLogIdent(identifiedUser.newRefLogIdent());
        rp.setTimeout(config.getTimeout());
        rp.setMaxObjectSizeLimit(config.getMaxObjectSizeLimit());

        receivePackInitializers.runEach(
            initializer -> initializer.init(projectState.getNameKey(), rp));
        QuotaResponse.Aggregated availableTokens =
            quotaBackend
                .user(identifiedUser)
                .project(req.project)
                .availableTokens(REPOSITORY_SIZE_GROUP);
        availableTokens.throwOnError();
        availableTokens.availableTokens().ifPresent(v -> rp.setMaxObjectSizeLimit(v));

        ImmutableList<PostReceiveHook> hooks =
            ImmutableList.<PostReceiveHook>builder()
                .add(
                    (pack, commands) -> {
                      if (affectsSize(pack)) {
                        try {
                          quotaBackend
                              .user(identifiedUser)
                              .project(req.project)
                              .requestTokens(REPOSITORY_SIZE_GROUP, pack.getPackSize())
                              .throwOnError();
                        } catch (QuotaException e) {
                          throw new RuntimeException(e);
                        }
                      }
                    })
                .addAll(postReceiveHooks)
                .build();
        rp.setPostReceiveHook(PostReceiveHookChain.newChain(hooks));
        return rp;
      } catch (IOException | PermissionBackendException | QuotaException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Inject
  InProcessProtocol(Upload uploadPackFactory, Receive receivePackFactory) {
    super(uploadPackFactory, receivePackFactory);
  }
}
