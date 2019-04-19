// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.httpd;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.audit.HttpAuditEvent;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.git.DefaultAdvertiseRefsHook;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.UploadPackInitializer;
import com.google.gerrit.server.git.receive.AsyncReceiveCommits;
import com.google.gerrit.server.git.validators.UploadValidators;
import com.google.gerrit.server.group.GroupAuditService;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.GitSmartHttpTools;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostUploadHook;
import org.eclipse.jgit.transport.PostUploadHookChain;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.PreUploadHookChain;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

/** Serves Git repositories over HTTP. */
@Singleton
public class GitOverHttpServlet extends GitServlet {
  private static final long serialVersionUID = 1L;

  private static final String ATT_STATE = ProjectState.class.getName();
  private static final String ATT_ARC = AsyncReceiveCommits.class.getName();
  private static final String ID_CACHE = "adv_bases";

  public static final String URL_REGEX;

  static {
    StringBuilder url = new StringBuilder();
    url.append("^(?:/a)?(?:/p/|/)(.*/(?:info/refs");
    for (String name : GitSmartHttpTools.VALID_SERVICES) {
      url.append('|').append(name);
    }
    url.append("))$");
    URL_REGEX = url.toString();
  }

  static class Module extends AbstractModule {

    private final boolean enableReceive;

    Module(boolean enableReceive) {
      this.enableReceive = enableReceive;
    }

    @Override
    protected void configure() {
      bind(Resolver.class);
      bind(UploadFactory.class);
      bind(UploadFilter.class);
      bind(new TypeLiteral<ReceivePackFactory<HttpServletRequest>>() {})
          .to(enableReceive ? ReceiveFactory.class : DisabledReceiveFactory.class);
      bind(ReceiveFilter.class);
      install(
          new CacheModule() {
            @Override
            protected void configure() {
              cache(ID_CACHE, AdvertisedObjectsCacheKey.class, new TypeLiteral<Set<ObjectId>>() {})
                  .maximumWeight(4096)
                  .expireAfterWrite(Duration.ofMinutes(10));
            }
          });

      // Don't bind Metrics, which is bound in a parent injector in tests.
    }
  }

  @VisibleForTesting
  @Singleton
  public static class Metrics {
    // Recording requests separately in this class is only necessary because of a bug in the
    // implementation of the generic RequestMetricsFilter; see
    // https://gerrit-review.googlesource.com/c/gerrit/+/211692
    private final AtomicLong requestsStarted = new AtomicLong();

    void requestStarted() {
      requestsStarted.incrementAndGet();
    }

    public long getRequestsStarted() {
      return requestsStarted.get();
    }
  }

  static class HttpServletResponseWithStatusWrapper extends HttpServletResponseWrapper {
    private int responseStatus;

    HttpServletResponseWithStatusWrapper(HttpServletResponse response) {
      super(response);
      /* Even if we could read the status from response, we assume that it is all
       * fine because we entered the filter without any prior issues.
       * When Google will have upgraded to Servlet 3.0, we could actually
       * call response.getStatus() and the code will be clearer.
       */
      responseStatus = HttpServletResponse.SC_OK;
    }

    @Override
    public void setStatus(int sc) {
      responseStatus = sc;
      super.setStatus(sc);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setStatus(int sc, String sm) {
      responseStatus = sc;
      super.setStatus(sc, sm);
    }

    @Override
    public void sendError(int sc) throws IOException {
      this.responseStatus = sc;
      super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      this.responseStatus = sc;
      super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
      this.responseStatus = HttpServletResponse.SC_MOVED_TEMPORARILY;
      super.sendRedirect(location);
    }

    public int getResponseStatus() {
      return responseStatus;
    }
  }

  @Inject
  GitOverHttpServlet(
      Resolver resolver,
      UploadFactory upload,
      UploadFilter uploadFilter,
      ReceivePackFactory<HttpServletRequest> receive,
      ReceiveFilter receiveFilter) {
    setRepositoryResolver(resolver);
    setAsIsFileService(AsIsFileService.DISABLED);

    setUploadPackFactory(upload);
    addUploadPackFilter(uploadFilter);

    setReceivePackFactory(receive);
    addReceivePackFilter(receiveFilter);
  }

  private static String extractWhat(HttpServletRequest request) {
    StringBuilder commandName = new StringBuilder(request.getRequestURL());
    if (request.getQueryString() != null) {
      commandName.append("?").append(request.getQueryString());
    }
    return commandName.toString();
  }

  private static ListMultimap<String, String> extractParameters(HttpServletRequest request) {
    if (request.getQueryString() == null) {
      return ImmutableListMultimap.of();
    }
    // Explicit cast is required to compile under Servlet API 2.5, where the return type is raw Map.
    @SuppressWarnings("cast")
    Map<String, String[]> parameterMap = (Map<String, String[]>) request.getParameterMap();
    ImmutableListMultimap.Builder<String, String> b = ImmutableListMultimap.builder();
    parameterMap.forEach(b::putAll);
    return b.build();
  }

  static class Resolver implements RepositoryResolver<HttpServletRequest> {
    private final GitRepositoryManager manager;
    private final PermissionBackend permissionBackend;
    private final Provider<CurrentUser> userProvider;
    private final ProjectCache projectCache;

    @Inject
    Resolver(
        GitRepositoryManager manager,
        PermissionBackend permissionBackend,
        Provider<CurrentUser> userProvider,
        ProjectCache projectCache) {
      this.manager = manager;
      this.permissionBackend = permissionBackend;
      this.userProvider = userProvider;
      this.projectCache = projectCache;
    }

    @Override
    public Repository open(HttpServletRequest req, String projectName)
        throws RepositoryNotFoundException, ServiceNotAuthorizedException,
            ServiceNotEnabledException, ServiceMayNotContinueException {
      while (projectName.endsWith("/")) {
        projectName = projectName.substring(0, projectName.length() - 1);
      }

      if (projectName.endsWith(".git")) {
        // Be nice and drop the trailing ".git" suffix, which we never keep
        // in our database, but clients might mistakenly provide anyway.
        //
        projectName = projectName.substring(0, projectName.length() - 4);
        while (projectName.endsWith("/")) {
          projectName = projectName.substring(0, projectName.length() - 1);
        }
      }

      CurrentUser user = userProvider.get();
      user.setAccessPath(AccessPath.GIT);

      try {
        Project.NameKey nameKey = Project.nameKey(projectName);
        ProjectState state = projectCache.checkedGet(nameKey);
        if (state == null || !state.statePermitsRead()) {
          throw new RepositoryNotFoundException(nameKey.get());
        }
        req.setAttribute(ATT_STATE, state);

        try {
          permissionBackend.user(user).project(nameKey).check(ProjectPermission.ACCESS);
        } catch (AuthException e) {
          if (user instanceof AnonymousUser) {
            throw new ServiceNotAuthorizedException();
          }
          throw new ServiceNotEnabledException(e.getMessage());
        }

        return manager.openRepository(nameKey);
      } catch (IOException | PermissionBackendException err) {
        throw new ServiceMayNotContinueException(projectName + " unavailable", err);
      }
    }
  }

  static class UploadFactory implements UploadPackFactory<HttpServletRequest> {
    private final TransferConfig config;
    private final DynamicSet<PreUploadHook> preUploadHooks;
    private final DynamicSet<PostUploadHook> postUploadHooks;
    private final PluginSetContext<UploadPackInitializer> uploadPackInitializers;

    @Inject
    UploadFactory(
        TransferConfig tc,
        DynamicSet<PreUploadHook> preUploadHooks,
        DynamicSet<PostUploadHook> postUploadHooks,
        PluginSetContext<UploadPackInitializer> uploadPackInitializers) {
      this.config = tc;
      this.preUploadHooks = preUploadHooks;
      this.postUploadHooks = postUploadHooks;
      this.uploadPackInitializers = uploadPackInitializers;
    }

    @Override
    public UploadPack create(HttpServletRequest req, Repository repo) {
      UploadPack up = new UploadPack(repo);
      up.setPackConfig(config.getPackConfig());
      up.setTimeout(config.getTimeout());
      up.setPreUploadHook(PreUploadHookChain.newChain(Lists.newArrayList(preUploadHooks)));
      up.setPostUploadHook(PostUploadHookChain.newChain(Lists.newArrayList(postUploadHooks)));
      ProjectState state = (ProjectState) req.getAttribute(ATT_STATE);
      uploadPackInitializers.runEach(initializer -> initializer.init(state.getNameKey(), up));
      return up;
    }
  }

  static class UploadFilter implements Filter {
    private final UploadValidators.Factory uploadValidatorsFactory;
    private final PermissionBackend permissionBackend;
    private final Provider<CurrentUser> userProvider;
    private final GroupAuditService groupAuditService;
    private final Metrics metrics;

    @Inject
    UploadFilter(
        UploadValidators.Factory uploadValidatorsFactory,
        PermissionBackend permissionBackend,
        Provider<CurrentUser> userProvider,
        GroupAuditService groupAuditService,
        Metrics metrics) {
      this.uploadValidatorsFactory = uploadValidatorsFactory;
      this.permissionBackend = permissionBackend;
      this.userProvider = userProvider;
      this.groupAuditService = groupAuditService;
      this.metrics = metrics;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain next)
        throws IOException, ServletException {
      metrics.requestStarted();
      // The Resolver above already checked READ access for us.
      Repository repo = ServletUtils.getRepository(request);
      ProjectState state = (ProjectState) request.getAttribute(ATT_STATE);
      UploadPack up = (UploadPack) request.getAttribute(ServletUtils.ATTRIBUTE_HANDLER);
      PermissionBackend.ForProject perm =
          permissionBackend.currentUser().project(state.getNameKey());
      HttpServletResponseWithStatusWrapper responseWrapper =
          new HttpServletResponseWithStatusWrapper((HttpServletResponse) response);
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      String sessionId = httpRequest.getSession().getId();

      try {
        try {
          perm.check(ProjectPermission.RUN_UPLOAD_PACK);
        } catch (AuthException e) {
          GitSmartHttpTools.sendError(
              (HttpServletRequest) request,
              responseWrapper,
              HttpServletResponse.SC_FORBIDDEN,
              "upload-pack not permitted on this server");
          return;
        } catch (PermissionBackendException e) {
          responseWrapper.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
          throw new ServletException(e);
        }

        // We use getRemoteHost() here instead of getRemoteAddr() because REMOTE_ADDR
        // may have been overridden by a proxy server -- we'll try to avoid this.
        UploadValidators uploadValidators =
            uploadValidatorsFactory.create(state.getProject(), repo, request.getRemoteHost());
        up.setPreUploadHook(
            PreUploadHookChain.newChain(
                Lists.newArrayList(up.getPreUploadHook(), uploadValidators)));
        up.setAdvertiseRefsHook(new DefaultAdvertiseRefsHook(perm, RefFilterOptions.defaults()));
        next.doFilter(httpRequest, responseWrapper);
      } finally {
        groupAuditService.dispatch(
            new HttpAuditEvent(
                sessionId,
                userProvider.get(),
                extractWhat(httpRequest),
                TimeUtil.nowMs(),
                extractParameters(httpRequest),
                httpRequest.getMethod(),
                httpRequest,
                responseWrapper.getResponseStatus(),
                responseWrapper));
      }
    }

    @Override
    public void init(FilterConfig config) {}

    @Override
    public void destroy() {}
  }

  static class ReceiveFactory implements ReceivePackFactory<HttpServletRequest> {
    private final AsyncReceiveCommits.Factory factory;
    private final Provider<CurrentUser> userProvider;

    @Inject
    ReceiveFactory(AsyncReceiveCommits.Factory factory, Provider<CurrentUser> userProvider) {
      this.factory = factory;
      this.userProvider = userProvider;
    }

    @Override
    public ReceivePack create(HttpServletRequest req, Repository db)
        throws ServiceNotAuthorizedException {
      final ProjectState state = (ProjectState) req.getAttribute(ATT_STATE);

      if (!(userProvider.get().isIdentifiedUser())) {
        // Anonymous users are not permitted to push.
        throw new ServiceNotAuthorizedException();
      }

      AsyncReceiveCommits arc =
          factory.create(state, userProvider.get().asIdentifiedUser(), db, null);
      ReceivePack rp = arc.getReceivePack();
      req.setAttribute(ATT_ARC, arc);
      return rp;
    }
  }

  static class DisabledReceiveFactory implements ReceivePackFactory<HttpServletRequest> {
    @Override
    public ReceivePack create(HttpServletRequest req, Repository db)
        throws ServiceNotEnabledException {
      throw new ServiceNotEnabledException();
    }
  }

  static class ReceiveFilter implements Filter {
    private final Cache<AdvertisedObjectsCacheKey, Set<ObjectId>> cache;
    private final PermissionBackend permissionBackend;
    private final Provider<CurrentUser> userProvider;
    private final GroupAuditService groupAuditService;
    private final Metrics metrics;

    @Inject
    ReceiveFilter(
        @Named(ID_CACHE) Cache<AdvertisedObjectsCacheKey, Set<ObjectId>> cache,
        PermissionBackend permissionBackend,
        Provider<CurrentUser> userProvider,
        GroupAuditService groupAuditService,
        Metrics metrics) {
      this.cache = cache;
      this.permissionBackend = permissionBackend;
      this.userProvider = userProvider;
      this.groupAuditService = groupAuditService;
      this.metrics = metrics;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
      metrics.requestStarted();
      boolean isGet = "GET".equalsIgnoreCase(((HttpServletRequest) request).getMethod());

      AsyncReceiveCommits arc = (AsyncReceiveCommits) request.getAttribute(ATT_ARC);

      // Send refs down the wire.
      ReceivePack rp = arc.getReceivePack();
      rp.getAdvertiseRefsHook().advertiseRefs(rp);

      ProjectState state = (ProjectState) request.getAttribute(ATT_STATE);
      HttpServletResponseWithStatusWrapper responseWrapper =
          new HttpServletResponseWithStatusWrapper((HttpServletResponse) response);
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      Capable canUpload;
      try {
        try {
          permissionBackend
              .currentUser()
              .project(state.getNameKey())
              .check(ProjectPermission.RUN_RECEIVE_PACK);
          canUpload = arc.canUpload();
        } catch (AuthException e) {
          GitSmartHttpTools.sendError(
              httpRequest,
              responseWrapper,
              HttpServletResponse.SC_FORBIDDEN,
              "receive-pack not permitted on this server");
          return;
        } catch (PermissionBackendException e) {
          throw new RuntimeException(e);
        }
      } finally {
        groupAuditService.dispatch(
            new HttpAuditEvent(
                httpRequest.getSession().getId(),
                userProvider.get(),
                extractWhat(httpRequest),
                TimeUtil.nowMs(),
                extractParameters(httpRequest),
                httpRequest.getMethod(),
                httpRequest,
                responseWrapper.getResponseStatus(),
                responseWrapper));
      }

      if (canUpload != Capable.OK) {
        GitSmartHttpTools.sendError(
            httpRequest,
            responseWrapper,
            HttpServletResponse.SC_FORBIDDEN,
            "\n" + canUpload.getMessage());
        return;
      }

      if (!rp.isCheckReferencedObjectsAreReachable()) {
        chain.doFilter(request, responseWrapper);
        return;
      }

      if (!(userProvider.get().isIdentifiedUser())) {
        chain.doFilter(request, responseWrapper);
        return;
      }

      AdvertisedObjectsCacheKey cacheKey =
          AdvertisedObjectsCacheKey.create(userProvider.get().getAccountId(), state.getNameKey());

      if (isGet) {
        cache.invalidate(cacheKey);
      } else {
        Set<ObjectId> ids = cache.getIfPresent(cacheKey);
        if (ids != null) {
          rp.getAdvertisedObjects().addAll(ids);
          cache.invalidate(cacheKey);
        }
      }

      chain.doFilter(request, responseWrapper);

      if (isGet) {
        cache.put(cacheKey, Collections.unmodifiableSet(new HashSet<>(rp.getAdvertisedObjects())));
      }
    }

    @Override
    public void init(FilterConfig arg0) {}

    @Override
    public void destroy() {}
  }
}
