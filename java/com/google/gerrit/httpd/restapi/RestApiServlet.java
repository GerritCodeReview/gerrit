// Copyright (C) 2012 The Android Open Source Project
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

// WARNING: NoteDbUpdateManager cares about the package name RestApiServlet lives in.
package com.google.gerrit.httpd.restapi;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.gerrit.httpd.EnableTracingFilter.REQUEST_TRACE_CONTEXT;
import static java.math.RoundingMode.CEILING;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NOT_IMPLEMENTED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static javax.servlet.http.HttpServletResponse.SC_REQUEST_TIMEOUT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.BaseEncoding;
import com.google.common.io.CountingOutputStream;
import com.google.common.math.IntMath;
import com.google.common.net.HttpHeaders;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PluginName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.NeedsParams;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.PreconditionFailedException;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.RestCollectionDeleteMissingView;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.RestCollectionView;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.restapi.ParameterParser.QueryParams;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.AclInfoController;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CancellationMetrics;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.DeadlineChecker;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.InvalidDeadlineException;
import com.google.gerrit.server.OptionUtil;
import com.google.gerrit.server.RequestInfo;
import com.google.gerrit.server.RequestListener;
import com.google.gerrit.server.audit.ExtendedHttpAuditEvent;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.cancellation.RequestCancelledException;
import com.google.gerrit.server.cancellation.RequestStateContext;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupAuditService;
import com.google.gerrit.server.logging.PerformanceLogContext;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.quota.QuotaException;
import com.google.gerrit.server.restapi.change.ChangesCollection;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryableAction;
import com.google.gerrit.server.update.RetryableAction.Action;
import com.google.gerrit.server.update.RetryableAction.ActionType;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.util.http.CacheHeaders;
import com.google.gerrit.util.http.RequestUtil;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.util.Providers;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.TemporaryBuffer.Heap;

public class RestApiServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** MIME type used for a JSON response body. */
  private static final String JSON_TYPE = "application/json";

  private static final String FORM_TYPE = "application/x-www-form-urlencoded";

  @VisibleForTesting public static final String X_GERRIT_DEADLINE = "X-Gerrit-Deadline";
  @VisibleForTesting public static final String X_GERRIT_TRACE = "X-Gerrit-Trace";
  @VisibleForTesting public static final String X_GERRIT_UPDATED_REF = "X-Gerrit-UpdatedRef";

  @VisibleForTesting
  public static final String X_GERRIT_UPDATED_REF_ENABLED = "X-Gerrit-UpdatedRef-Enabled";

  public static final String XD_AUTHORIZATION = "access_token";
  public static final String XD_CONTENT_TYPE = "$ct";
  public static final String XD_METHOD = "$m";
  public static final int SC_UNPROCESSABLE_ENTITY = 422;
  public static final int SC_TOO_MANY_REQUESTS = 429;
  public static final int SC_CLIENT_CLOSED_REQUEST = 499;

  private static final int HEAP_EST_SIZE = 10 * 8 * 1024; // Presize 10 blocks.
  private static final String PLAIN_TEXT = "text/plain";
  private static final Pattern TYPE_SPLIT_PATTERN = Pattern.compile("[ ,;][ ,;]*");
  private static final long ONE_KB = 1024;

  /**
   * Garbage prefix inserted before JSON output to prevent XSSI.
   *
   * <p>This prefix is ")]}'\n" and is designed to prevent a web browser from executing the response
   * body if the resource URI were to be referenced using a &lt;script src="...&gt; HTML tag from
   * another web site. Clients using the HTTP interface will need to always strip the first line of
   * response data to remove this magic header.
   */
  public static final byte[] JSON_MAGIC;

  static {
    JSON_MAGIC = ")]}'\n".getBytes(UTF_8);
  }

  public static class Globals {
    final Provider<CurrentUser> currentUser;
    final DynamicItem<WebSession> webSession;
    final Provider<ParameterParser> paramParser;
    final PluginSetContext<RequestListener> requestListeners;
    final PermissionBackend permissionBackend;
    final GroupAuditService auditService;
    final RestApiMetrics metrics;
    final Pattern allowOrigin;
    final RestApiQuotaEnforcer quotaChecker;
    final Config config;
    final DynamicSet<PerformanceLogger> performanceLoggers;
    final ChangeFinder changeFinder;
    final RetryHelper retryHelper;
    final PluginSetContext<ExceptionHook> exceptionHooks;
    final Injector injector;
    final DynamicMap<DynamicOptions.DynamicBean> dynamicBeans;
    final DeadlineChecker.Factory deadlineCheckerFactory;
    final CancellationMetrics cancellationMetrics;
    final AclInfoController aclInfoController;
    final Provider<TraceContext> requestTraceContext;

    @Inject
    Globals(
        Provider<CurrentUser> currentUser,
        DynamicItem<WebSession> webSession,
        Provider<ParameterParser> paramParser,
        PluginSetContext<RequestListener> requestListeners,
        PermissionBackend permissionBackend,
        GroupAuditService auditService,
        RestApiMetrics metrics,
        RestApiQuotaEnforcer quotaChecker,
        @GerritServerConfig Config config,
        DynamicSet<PerformanceLogger> performanceLoggers,
        ChangeFinder changeFinder,
        RetryHelper retryHelper,
        PluginSetContext<ExceptionHook> exceptionHooks,
        Injector injector,
        DynamicMap<DynamicOptions.DynamicBean> dynamicBeans,
        DeadlineChecker.Factory deadlineCheckerFactory,
        CancellationMetrics cancellationMetrics,
        AclInfoController aclInfoController,
        @Named(REQUEST_TRACE_CONTEXT) Provider<TraceContext> requestTraceContext) {
      this.currentUser = currentUser;
      this.webSession = webSession;
      this.paramParser = paramParser;
      this.requestListeners = requestListeners;
      this.permissionBackend = permissionBackend;
      this.auditService = auditService;
      this.metrics = metrics;
      this.quotaChecker = quotaChecker;
      this.config = config;
      this.performanceLoggers = performanceLoggers;
      this.changeFinder = changeFinder;
      this.retryHelper = retryHelper;
      this.exceptionHooks = exceptionHooks;
      allowOrigin = CorsResponder.makeAllowOrigin(config);
      this.injector = injector;
      this.dynamicBeans = dynamicBeans;
      this.deadlineCheckerFactory = deadlineCheckerFactory;
      this.cancellationMetrics = cancellationMetrics;
      this.aclInfoController = aclInfoController;
      this.requestTraceContext = requestTraceContext;
    }
  }

  private final Globals globals;
  private final Provider<RestCollection<RestResource, RestResource>> members;
  private final CorsResponder corsResponder;

  public RestApiServlet(
      Globals globals, RestCollection<? extends RestResource, ? extends RestResource> members) {
    this(globals, Providers.of(members));
  }

  public RestApiServlet(
      Globals globals,
      Provider<? extends RestCollection<? extends RestResource, ? extends RestResource>> members) {
    @SuppressWarnings("unchecked")
    Provider<RestCollection<RestResource, RestResource>> n =
        (Provider<RestCollection<RestResource, RestResource>>) requireNonNull((Object) members);
    this.globals = globals;
    this.members = n;
    this.corsResponder = new CorsResponder(globals.allowOrigin);
  }

  @Override
  protected final void service(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    final long startNanos = System.nanoTime();
    long auditStartTs = TimeUtil.nowMs();
    res.setHeader("X-Content-Type-Options", "nosniff");
    // Nobody should be loading HTML from our API server, but if for some reason that happens, stop
    // it having any capabilities
    res.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
    res.setHeader("Referrer-Policy", "no-referrer");
    // Nobody should be iframing our API server.
    res.setHeader("X-Frame-Options", "deny");
    int statusCode = SC_OK;
    long responseBytes = -1;
    Optional<Exception> cause = Optional.empty();
    Response<?> response = null;
    QueryParams qp = null;
    Object inputRequestBody = null;
    RestResource rsrc = TopLevelResource.INSTANCE;
    ViewData viewData = null;
    String sessionId = globals.webSession.get().getSessionId();
    CurrentUser currentUser = globals.currentUser.get();

    String requestUri = requestUri(req);

    try (PerThreadCache ignored = PerThreadCache.create()) {
      List<IdString> path = splitPath(req);
      TraceContext traceContext = globals.requestTraceContext.get();
      RequestInfo requestInfo = createRequestInfo(traceContext, req, requestUri, path);
      globals.requestListeners.runEach(l -> l.onRequest(requestInfo));

      globals.aclInfoController.enableAclLoggingIfUserCanViewAccess(traceContext);

      // It's important that the PerformanceLogContext is closed before the response is sent to
      // the client. Only this way it is ensured that the invocation of the PerformanceLogger
      // plugins happens before the client sees the response. This is needed for being able to
      // test performance logging from an acceptance test (see
      // TraceIT#performanceLoggingForRestCall()).
      try (RequestStateContext requestStateContext =
              RequestStateContext.open()
                  .addRequestStateProvider(
                      globals.deadlineCheckerFactory.create(
                          requestInfo, req.getHeader(X_GERRIT_DEADLINE)));
          PerformanceLogContext performanceLogContext =
              new PerformanceLogContext(globals.config, globals.performanceLoggers)) {
        traceRequestData(req);

        if (corsResponder.filterCorsPreflight(req, res)) {
          return;
        }

        qp = ParameterParser.getQueryParams(req);
        corsResponder.checkCors(req, res, qp.hasXdOverride());
        if (qp.hasXdOverride()) {
          req = applyXdOverrides(req, qp);
        }
        checkUserSession(req);

        RestCollection<RestResource, RestResource> rc = members.get();
        globals
            .permissionBackend
            .currentUser()
            .checkAny(GlobalPermission.fromAnnotation(rc.getClass()));

        viewData = new ViewData(null, null);

        if (path.isEmpty()) {
          globals.quotaChecker.enforce(req);
          if (rc instanceof NeedsParams) {
            ((NeedsParams) rc).setParams(qp.params());
          }

          if (isRead(req)) {
            viewData = new ViewData(null, rc.list());
          } else if (isPost(req)) {
            RestView<RestResource> restCollectionView =
                rc.views().get(PluginName.GERRIT, "POST_ON_COLLECTION./");
            if (restCollectionView != null) {
              viewData = new ViewData(null, restCollectionView);
            } else {
              throw methodNotAllowed(req);
            }
          } else {
            // DELETE on root collections is not supported
            throw methodNotAllowed(req);
          }
        } else {
          IdString id = path.remove(0);
          try {
            rsrc = parseResourceWithRetry(req, traceContext, viewData.pluginName, rc, rsrc, id);
            globals.quotaChecker.enforce(rsrc, req);
            if (path.isEmpty()) {
              checkPreconditions(req);
            }
          } catch (ResourceNotFoundException e) {
            if (!path.isEmpty()) {
              throw e;
            }
            globals.quotaChecker.enforce(req);

            if (isPost(req) || isPut(req)) {
              RestView<RestResource> createView = rc.views().get(PluginName.GERRIT, "CREATE./");
              if (createView != null) {
                viewData = new ViewData(null, createView);
                path.add(id);
              } else {
                throw e;
              }
            } else if (isDelete(req)) {
              RestView<RestResource> deleteView =
                  rc.views().get(PluginName.GERRIT, "DELETE_MISSING./");
              if (deleteView != null) {
                viewData = new ViewData(null, deleteView);
                path.add(id);
              } else {
                throw e;
              }
            } else {
              throw e;
            }
          }
          if (viewData.view == null) {
            viewData = view(rc, req.getMethod(), path);
          }
        }
        checkRequiresCapability(viewData);

        while (viewData.view instanceof RestCollection<?, ?>) {
          @SuppressWarnings("unchecked")
          RestCollection<RestResource, RestResource> c =
              (RestCollection<RestResource, RestResource>) viewData.view;

          if (path.isEmpty()) {
            if (isRead(req)) {
              viewData = new ViewData(null, c.list());
            } else if (isPost(req)) {
              // TODO: Here and on other collection methods: There is a bug that binds child views
              // with pluginName="gerrit" instead of the real plugin name. This has never worked
              // correctly and should be fixed where the binding gets created (DynamicMapProvider)
              // and here.
              RestView<RestResource> restCollectionView =
                  c.views().get(PluginName.GERRIT, "POST_ON_COLLECTION./");
              if (restCollectionView != null) {
                viewData = new ViewData(null, restCollectionView);
              } else {
                throw methodNotAllowed(req);
              }
            } else if (isDelete(req)) {
              RestView<RestResource> restCollectionView =
                  c.views().get(PluginName.GERRIT, "DELETE_ON_COLLECTION./");
              if (restCollectionView != null) {
                viewData = new ViewData(null, restCollectionView);
              } else {
                throw methodNotAllowed(req);
              }
            } else {
              throw methodNotAllowed(req);
            }
            break;
          }
          IdString id = path.remove(0);
          try {
            rsrc = parseResourceWithRetry(req, traceContext, viewData.pluginName, c, rsrc, id);
            checkPreconditions(req);
            viewData = new ViewData(null, null);
          } catch (ResourceNotFoundException e) {
            if (!path.isEmpty()) {
              throw e;
            }

            if (isPost(req) || isPut(req)) {
              RestView<RestResource> createView = c.views().get(PluginName.GERRIT, "CREATE./");
              if (createView != null) {
                viewData = new ViewData(viewData.pluginName, createView);
                path.add(id);
              } else {
                throw e;
              }
            } else if (isDelete(req)) {
              RestView<RestResource> deleteView =
                  c.views().get(PluginName.GERRIT, "DELETE_MISSING./");
              if (deleteView != null) {
                viewData = new ViewData(viewData.pluginName, deleteView);
                path.add(id);
              } else {
                throw e;
              }
            } else {
              throw e;
            }
          }
          if (viewData.view == null) {
            viewData = view(c, req.getMethod(), path);
          }
          checkRequiresCapability(viewData);
        }

        if (notModified(req, rsrc)) {
          logger.atFinest().log("REST call succeeded: %d", SC_NOT_MODIFIED);
          res.sendError(SC_NOT_MODIFIED);
          return;
        }

        try (DynamicOptions pluginOptions =
            new DynamicOptions(globals.injector, globals.dynamicBeans)) {
          if (!globals
              .paramParser
              .get()
              .parse(viewData.view, pluginOptions, qp.params(), req, res)) {
            return;
          }

          if (viewData.view instanceof RestReadView<?> && isRead(req)) {
            response =
                invokeRestReadViewWithRetry(
                    req, traceContext, viewData, (RestReadView<RestResource>) viewData.view, rsrc);
          } else if (viewData.view instanceof RestModifyView<?, ?>) {
            RestModifyView<RestResource, Object> m =
                (RestModifyView<RestResource, Object>) viewData.view;

            Type type = inputType(m);
            inputRequestBody = parseRequest(req, type);
            response =
                invokeRestModifyViewWithRetry(
                    req, traceContext, viewData, m, rsrc, inputRequestBody);

            if (inputRequestBody instanceof RawInput) {
              try (InputStream is = req.getInputStream()) {
                ServletUtils.consumeRequestBody(is);
              }
            }
          } else if (viewData.view instanceof RestCollectionCreateView<?, ?, ?>) {
            RestCollectionCreateView<RestResource, RestResource, Object> m =
                (RestCollectionCreateView<RestResource, RestResource, Object>) viewData.view;

            Type type = inputType(m);
            inputRequestBody = parseRequest(req, type);
            response =
                invokeRestCollectionCreateViewWithRetry(
                    req, traceContext, viewData, m, rsrc, path.get(0), inputRequestBody);
            if (inputRequestBody instanceof RawInput) {
              try (InputStream is = req.getInputStream()) {
                ServletUtils.consumeRequestBody(is);
              }
            }
          } else if (viewData.view instanceof RestCollectionDeleteMissingView<?, ?, ?>) {
            RestCollectionDeleteMissingView<RestResource, RestResource, Object> m =
                (RestCollectionDeleteMissingView<RestResource, RestResource, Object>) viewData.view;

            Type type = inputType(m);
            inputRequestBody = parseRequest(req, type);
            response =
                invokeRestCollectionDeleteMissingViewWithRetry(
                    req, traceContext, viewData, m, rsrc, path.get(0), inputRequestBody);
            if (inputRequestBody instanceof RawInput) {
              try (InputStream is = req.getInputStream()) {
                ServletUtils.consumeRequestBody(is);
              }
            }
          } else if (viewData.view instanceof RestCollectionModifyView<?, ?, ?>) {
            RestCollectionModifyView<RestResource, RestResource, Object> m =
                (RestCollectionModifyView<RestResource, RestResource, Object>) viewData.view;

            Type type = inputType(m);
            inputRequestBody = parseRequest(req, type);
            response =
                invokeRestCollectionModifyViewWithRetry(
                    req, traceContext, viewData, m, rsrc, inputRequestBody);
            if (inputRequestBody instanceof RawInput) {
              try (InputStream is = req.getInputStream()) {
                ServletUtils.consumeRequestBody(is);
              }
            }
          } else {
            throw new ResourceNotFoundException();
          }
          String isUpdatedRefEnabled = req.getHeader(X_GERRIT_UPDATED_REF_ENABLED);
          if (!Strings.isNullOrEmpty(isUpdatedRefEnabled) && Boolean.valueOf(isUpdatedRefEnabled)) {
            setXGerritUpdatedRefResponseHeaders(req, res);
          }

          if (response instanceof Response.Redirect) {
            CacheHeaders.setNotCacheable(res);
            String location = ((Response.Redirect) response).location();
            res.sendRedirect(location);
            logger.atFinest().log("REST call redirected to: %s", location);
            return;
          } else if (response instanceof Response.Accepted) {
            CacheHeaders.setNotCacheable(res);
            res.setStatus(response.statusCode());
            res.setHeader(HttpHeaders.LOCATION, ((Response.Accepted) response).location());
            logger.atFinest().log("REST call succeeded: %d", response.statusCode());
            return;
          }

          statusCode = response.statusCode();
          response.headers().forEach((k, v) -> res.setHeader(k, v));
          configureCaching(req, res, rsrc, response.caching());
          res.setStatus(statusCode);
          logger.atFinest().log("REST call succeeded: %d", statusCode);
        }

        if (response != Response.none()) {
          Object value = Response.unwrap(response);
          if (value instanceof BinaryResult) {
            responseBytes = replyBinaryResult(req, res, (BinaryResult) value);
          } else {
            responseBytes = replyJson(req, res, false, qp.config(), value);
          }
        }
      }
    } catch (MalformedJsonException | JsonParseException e) {
      cause = Optional.of(e);
      logger.atFine().withCause(e).log("REST call failed on JSON parsing");
      responseBytes =
          replyError(
              req, res, statusCode = SC_BAD_REQUEST, "Invalid " + JSON_TYPE + " in request", e);
    } catch (BadRequestException e) {
      cause = Optional.of(e);
      responseBytes =
          replyError(
              req, res, statusCode = SC_BAD_REQUEST, messageOr(e, "Bad Request"), e.caching(), e);
    } catch (AuthException e) {
      cause = Optional.of(e);

      StringBuilder messageBuilder = new StringBuilder(messageOr(e, "Forbidden"));
      globals
          .aclInfoController
          .getAclInfoMessage()
          .ifPresent(aclInfo -> messageBuilder.append("\n\n").append(aclInfo));

      responseBytes =
          replyError(
              req, res, statusCode = SC_FORBIDDEN, messageBuilder.toString(), e.caching(), e);
    } catch (AmbiguousViewException e) {
      cause = Optional.of(e);
      responseBytes = replyError(req, res, statusCode = SC_NOT_FOUND, messageOr(e, "Ambiguous"), e);
    } catch (ResourceNotFoundException e) {
      cause = Optional.of(e);
      responseBytes =
          replyError(
              req, res, statusCode = SC_NOT_FOUND, messageOr(e, "Not Found"), e.caching(), e);
    } catch (MethodNotAllowedException e) {
      cause = Optional.of(e);
      responseBytes =
          replyError(
              req,
              res,
              statusCode = SC_METHOD_NOT_ALLOWED,
              messageOr(e, "Method Not Allowed"),
              e.caching(),
              e);
    } catch (ResourceConflictException e) {
      cause = Optional.of(e);
      responseBytes =
          replyError(req, res, statusCode = SC_CONFLICT, messageOr(e, "Conflict"), e.caching(), e);
    } catch (PreconditionFailedException e) {
      cause = Optional.of(e);
      responseBytes =
          replyError(
              req,
              res,
              statusCode = SC_PRECONDITION_FAILED,
              messageOr(e, "Precondition Failed"),
              e.caching(),
              e);
    } catch (UnprocessableEntityException e) {
      cause = Optional.of(e);
      responseBytes =
          replyError(
              req,
              res,
              statusCode = SC_UNPROCESSABLE_ENTITY,
              messageOr(e, "Unprocessable Entity"),
              e.caching(),
              e);
    } catch (NotImplementedException e) {
      cause = Optional.of(e);
      logger.atSevere().withCause(e).log("Error in %s %s", req.getMethod(), uriForLogging(req));
      responseBytes =
          replyError(req, res, statusCode = SC_NOT_IMPLEMENTED, messageOr(e, "Not Implemented"), e);
    } catch (QuotaException e) {
      cause = Optional.of(e);
      responseBytes =
          replyError(
              req,
              res,
              statusCode = SC_TOO_MANY_REQUESTS,
              messageOr(e, "Quota limit reached"),
              e.caching(),
              e);
    } catch (InvalidDeadlineException e) {
      cause = Optional.of(e);
      responseBytes =
          replyError(req, res, statusCode = SC_BAD_REQUEST, messageOr(e, "Bad Request"), e);
    } catch (Exception e) {
      cause = Optional.of(e);

      Optional<RequestCancelledException> requestCancelledException =
          RequestCancelledException.getFromCausalChain(e);
      if (requestCancelledException.isPresent()) {
        RequestStateProvider.Reason cancellationReason =
            requestCancelledException.get().getCancellationReason();
        globals.cancellationMetrics.countCancelledRequest(
            RequestInfo.RequestType.REST, requestUri, cancellationReason);
        statusCode = getCancellationStatusCode(cancellationReason);
        responseBytes =
            replyError(
                req, res, statusCode, getCancellationMessage(requestCancelledException.get()), e);
      } else {
        statusCode = SC_INTERNAL_SERVER_ERROR;

        Optional<ExceptionHook.Status> status = getStatus(e);
        statusCode = status.map(ExceptionHook.Status::statusCode).orElse(SC_INTERNAL_SERVER_ERROR);

        if (res.isCommitted()) {
          responseBytes = 0;
          if (statusCode == SC_INTERNAL_SERVER_ERROR) {
            logger.atSevere().withCause(e).log(
                "Error in %s %s, response already committed", req.getMethod(), uriForLogging(req));
          } else {
            logger.atWarning().log(
                "Response for %s %s already committed, wanted to set status %d",
                req.getMethod(), uriForLogging(req), statusCode);
          }
        } else {
          res.reset();
          TraceContext.getTraceIds().forEach(traceId -> res.addHeader(X_GERRIT_TRACE, traceId));

          if (status.isPresent()) {
            responseBytes = reply(req, res, e, status.get(), getUserMessages(e));
          } else {
            responseBytes =
                replyInternalServerError(req, res, e, getViewName(viewData), getUserMessages(e));
          }
        }
      }
    } finally {
      String metric = getViewName(viewData);
      String formattedCause = cause.map(globals.retryHelper::formatCause).orElse("_none");
      globals.metrics.count.increment(metric);
      if (statusCode >= SC_BAD_REQUEST) {
        globals.metrics.errorCount.increment(metric, statusCode, formattedCause);
      }
      if (responseBytes != -1) {
        globals.metrics.responseBytes.record(metric, responseBytes);
      }
      globals.metrics.serverLatency.record(
          metric, System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
      globals.auditService.dispatch(
          new ExtendedHttpAuditEvent(
              sessionId,
              currentUser,
              req,
              auditStartTs,
              qp != null ? qp.params() : ImmutableListMultimap.of(),
              inputRequestBody,
              statusCode,
              response,
              rsrc,
              viewData == null ? null : viewData.view));
    }
  }

  /**
   * Fill in the refs that were updated during this request in the response header. The updated refs
   * will be in the form of "project~ref~updated_SHA-1".
   */
  private void setXGerritUpdatedRefResponseHeaders(
      HttpServletRequest request, HttpServletResponse response) {
    for (GitReferenceUpdatedListener.Event refUpdate :
        globals.webSession.get().getRefUpdatedEvents()) {
      String refUpdateFormat =
          String.format(
              "%s~%s~%s~%s",
              // encode the project and ref names since they may contain `~`
              Url.encode(refUpdate.getProjectName()),
              Url.encode(refUpdate.getRefName()),
              refUpdate.getOldObjectId(),
              refUpdate.getNewObjectId());

      if (isRead(request)) {
        logger.atWarning().log(
            "request %s performed a ref update %s although the request is a READ request",
            request.getRequestURL(), refUpdateFormat);
      }
      response.addHeader(X_GERRIT_UPDATED_REF, refUpdateFormat);
    }
    globals.webSession.get().resetRefUpdatedEvents();
  }

  private RestResource parseResourceWithRetry(
      HttpServletRequest req,
      TraceContext traceContext,
      @Nullable String pluginName,
      RestCollection<RestResource, RestResource> restCollection,
      RestResource parentResource,
      IdString id)
      throws Exception {
    return invokeRestEndpointWithRetry(
        req,
        traceContext,
        globals.metrics.view(restCollection.getClass(), pluginName) + "#parse",
        ActionType.REST_READ_REQUEST,
        () -> restCollection.parse(parentResource, id));
  }

  private Response<?> invokeRestReadViewWithRetry(
      HttpServletRequest req,
      TraceContext traceContext,
      ViewData viewData,
      RestReadView<RestResource> view,
      RestResource rsrc)
      throws Exception {
    return invokeRestEndpointWithRetry(
        req,
        traceContext,
        getViewName(viewData),
        ActionType.REST_READ_REQUEST,
        () -> view.apply(rsrc));
  }

  private Response<?> invokeRestModifyViewWithRetry(
      HttpServletRequest req,
      TraceContext traceContext,
      ViewData viewData,
      RestModifyView<RestResource, Object> view,
      RestResource rsrc,
      Object inputRequestBody)
      throws Exception {
    return invokeRestEndpointWithRetry(
        req,
        traceContext,
        getViewName(viewData),
        ActionType.REST_WRITE_REQUEST,
        () -> view.apply(rsrc, inputRequestBody));
  }

  private Response<?> invokeRestCollectionCreateViewWithRetry(
      HttpServletRequest req,
      TraceContext traceContext,
      ViewData viewData,
      RestCollectionCreateView<RestResource, RestResource, Object> view,
      RestResource rsrc,
      IdString path,
      Object inputRequestBody)
      throws Exception {
    return invokeRestEndpointWithRetry(
        req,
        traceContext,
        getViewName(viewData),
        ActionType.REST_WRITE_REQUEST,
        () -> view.apply(rsrc, path, inputRequestBody));
  }

  private Response<?> invokeRestCollectionDeleteMissingViewWithRetry(
      HttpServletRequest req,
      TraceContext traceContext,
      ViewData viewData,
      RestCollectionDeleteMissingView<RestResource, RestResource, Object> view,
      RestResource rsrc,
      IdString path,
      Object inputRequestBody)
      throws Exception {
    return invokeRestEndpointWithRetry(
        req,
        traceContext,
        getViewName(viewData),
        ActionType.REST_WRITE_REQUEST,
        () -> view.apply(rsrc, path, inputRequestBody));
  }

  private Response<?> invokeRestCollectionModifyViewWithRetry(
      HttpServletRequest req,
      TraceContext traceContext,
      ViewData viewData,
      RestCollectionModifyView<RestResource, RestResource, Object> view,
      RestResource rsrc,
      Object inputRequestBody)
      throws Exception {
    return invokeRestEndpointWithRetry(
        req,
        traceContext,
        getViewName(viewData),
        ActionType.REST_WRITE_REQUEST,
        () -> view.apply(rsrc, inputRequestBody));
  }

  private <T> T invokeRestEndpointWithRetry(
      HttpServletRequest req,
      TraceContext traceContext,
      String caller,
      ActionType actionType,
      Action<T> action)
      throws Exception {
    RetryableAction<T> retryableAction = globals.retryHelper.action(actionType, caller, action);
    AtomicReference<Optional<String>> traceId = new AtomicReference<>(Optional.empty());
    if (!TraceContext.isTracing()) {
      // enable automatic retry with tracing in case of non-recoverable failure
      retryableAction
          .retryWithTrace(t -> !(t instanceof RestApiException))
          .onAutoTrace(
              autoTraceId -> {
                traceId.set(Optional.of(autoTraceId));

                // Include details of the request into the trace.
                traceRequestData(req);
              });
    }
    try {
      return retryableAction.call();
    } finally {
      // If auto-tracing got triggered due to a non-recoverable failure, also trace the rest of
      // this request. This means logging is forced for all further log statements and the logs are
      // associated with the same trace ID.
      traceId
          .get()
          .ifPresent(tid -> traceContext.addTag(RequestId.Type.TRACE_ID, tid).forceLogging());
    }
  }

  private String getViewName(ViewData viewData) {
    return viewData != null && viewData.view != null ? globals.metrics.view(viewData) : "_unknown";
  }

  private static HttpServletRequest applyXdOverrides(HttpServletRequest req, QueryParams qp)
      throws BadRequestException {
    if (!isPost(req)) {
      throw new BadRequestException("POST required");
    }

    String method = qp.xdMethod();
    String contentType = qp.xdContentType();
    if (method.equals("POST") || method.equals("PUT")) {
      if (!isType(PLAIN_TEXT, req.getContentType())) {
        throw new BadRequestException("invalid " + CONTENT_TYPE);
      }
      if (Strings.isNullOrEmpty(contentType)) {
        throw new BadRequestException(XD_CONTENT_TYPE + " required");
      }
    }

    return new HttpServletRequestWrapper(req) {
      @Override
      public String getMethod() {
        return method;
      }

      @Override
      public String getContentType() {
        return contentType;
      }
    };
  }

  private static String messageOr(Throwable t, String defaultMessage) {
    if (!Strings.isNullOrEmpty(t.getMessage())) {
      return t.getMessage();
    }
    return defaultMessage;
  }

  private boolean notModified(HttpServletRequest req, RestResource rsrc) {
    if (!isRead(req)) {
      return false;
    }

    if (rsrc instanceof RestResource.HasLastModified) {
      Timestamp m = ((RestResource.HasLastModified) rsrc).getLastModified();
      long d = req.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE);

      // HTTP times are in seconds, database may have millisecond precision.
      return d / 1000L == m.getTime() / 1000L;
    }
    return false;
  }

  private <R extends RestResource> void configureCaching(
      HttpServletRequest req, HttpServletResponse res, R rsrc, CacheControl cacheControl) {
    setCacheHeaders(req, res, cacheControl);
    if (isRead(req)) {
      switch (cacheControl.getType()) {
        case NONE:
        default:
          break;
        case PRIVATE:
          addResourceStateHeaders(res, rsrc);
          break;
        case PUBLIC:
          addResourceStateHeaders(res, rsrc);
          break;
      }
    }
  }

  private static void setCacheHeaders(
      HttpServletRequest req, HttpServletResponse res, CacheControl cacheControl) {
    if (isRead(req)) {
      switch (cacheControl.getType()) {
        case NONE:
        default:
          CacheHeaders.setNotCacheable(res);
          break;
        case PRIVATE:
          CacheHeaders.setCacheablePrivate(
              res, cacheControl.getAge(), cacheControl.getUnit(), cacheControl.isMustRevalidate());
          break;
        case PUBLIC:
          CacheHeaders.setCacheable(
              req,
              res,
              cacheControl.getAge(),
              cacheControl.getUnit(),
              cacheControl.isMustRevalidate());
          break;
      }
    } else {
      CacheHeaders.setNotCacheable(res);
    }
  }

  private void addResourceStateHeaders(HttpServletResponse res, RestResource rsrc) {
    if (rsrc instanceof RestResource.HasLastModified) {
      res.setDateHeader(
          HttpHeaders.LAST_MODIFIED,
          ((RestResource.HasLastModified) rsrc).getLastModified().getTime());
    }
  }

  private void checkPreconditions(HttpServletRequest req) throws PreconditionFailedException {
    if ("*".equals(req.getHeader(HttpHeaders.IF_NONE_MATCH))) {
      throw new PreconditionFailedException("Resource already exists");
    }
  }

  private static Type inputType(RestModifyView<RestResource, Object> m) {
    // MyModifyView implements RestModifyView<SomeResource, MyInput>
    TypeLiteral<?> typeLiteral = TypeLiteral.get(m.getClass());

    // RestModifyView<SomeResource, MyInput>
    // This is smart enough to resolve even when there are intervening subclasses, even if they have
    // reordered type arguments.
    TypeLiteral<?> supertypeLiteral = typeLiteral.getSupertype(RestModifyView.class);

    Type supertype = supertypeLiteral.getType();
    checkState(
        supertype instanceof ParameterizedType,
        "supertype of %s is not parameterized: %s",
        typeLiteral,
        supertypeLiteral);
    return ((ParameterizedType) supertype).getActualTypeArguments()[1];
  }

  private static Type inputType(RestCollectionView<RestResource, RestResource, Object> m) {
    // MyCollectionView implements RestCollectionView<SomeResource, SomeResource, MyInput>
    TypeLiteral<?> typeLiteral = TypeLiteral.get(m.getClass());

    // RestCollectionView<SomeResource, SomeResource, MyInput>
    // This is smart enough to resolve even when there are intervening subclasses, even if they have
    // reordered type arguments.
    TypeLiteral<?> supertypeLiteral = typeLiteral.getSupertype(RestCollectionView.class);

    Type supertype = supertypeLiteral.getType();
    checkState(
        supertype instanceof ParameterizedType,
        "supertype of %s is not parameterized: %s",
        typeLiteral,
        supertypeLiteral);
    return ((ParameterizedType) supertype).getActualTypeArguments()[2];
  }

  @Nullable
  private Object parseRequest(HttpServletRequest req, Type type)
      throws IOException,
          BadRequestException,
          SecurityException,
          IllegalArgumentException,
          NoSuchMethodException,
          IllegalAccessException,
          InstantiationException,
          InvocationTargetException,
          MethodNotAllowedException {
    // HTTP/1.1 requires consuming the request body before writing non-error response (less than
    // 400). Consume the request body for all but raw input request types here.
    if (isType(JSON_TYPE, req.getContentType())) {
      try (BufferedReader br = req.getReader();
          JsonReader json = new JsonReader(br)) {
        try {
          json.setStrictness(Strictness.LENIENT);

          JsonToken first;
          try {
            first = json.peek();
          } catch (EOFException e) {
            throw new BadRequestException("Expected JSON object", e);
          }
          if (first == JsonToken.STRING) {
            return parseString(json.nextString(), type);
          }
          return OutputFormat.JSON.newGson().fromJson(json, type);
        } finally {
          try {
            // Reader.close won't consume the rest of the input. Explicitly consume the request
            // body.
            br.skip(Long.MAX_VALUE);
          } catch (Exception e) {
            // ignore, e.g. trying to consume the rest of the input may fail if the request was
            // cancelled
          }
        }
      }
    }
    String method = req.getMethod();
    if (("PUT".equals(method) || "POST".equals(method)) && acceptsRawInput(type)) {
      return parseRawInput(req, type);
    }
    if (isDelete(req) && hasNoBody(req)) {
      return null;
    }
    if (hasNoBody(req)) {
      return createInstance(type);
    }
    if (isType(PLAIN_TEXT, req.getContentType())) {
      try (BufferedReader br = req.getReader()) {
        char[] tmp = new char[256];
        StringBuilder sb = new StringBuilder();
        int n;
        while (0 < (n = br.read(tmp))) {
          sb.append(tmp, 0, n);
        }
        return parseString(sb.toString(), type);
      }
    }
    if (isPost(req) && isType(FORM_TYPE, req.getContentType())) {
      return OutputFormat.JSON.newGson().fromJson(ParameterParser.formToJson(req), type);
    }
    throw new BadRequestException("Expected Content-Type: " + JSON_TYPE);
  }

  private static boolean hasNoBody(HttpServletRequest req) {
    int len = req.getContentLength();
    String type = req.getContentType();
    return (len <= 0 && type == null) || (len == 0 && isType(FORM_TYPE, type));
  }

  @SuppressWarnings("rawtypes")
  private static boolean acceptsRawInput(Type type) {
    if (type instanceof Class) {
      for (Field f : ((Class) type).getDeclaredFields()) {
        if (f.getType() == RawInput.class) {
          return true;
        }
      }
    }
    return false;
  }

  private Object parseRawInput(HttpServletRequest req, Type type)
      throws SecurityException,
          NoSuchMethodException,
          IllegalArgumentException,
          InstantiationException,
          IllegalAccessException,
          InvocationTargetException,
          MethodNotAllowedException {
    Object obj = createInstance(type);
    for (Field f : obj.getClass().getDeclaredFields()) {
      if (f.getType() == RawInput.class) {
        f.setAccessible(true);
        f.set(obj, RawInputUtil.create(req));
        return obj;
      }
    }
    throw new MethodNotAllowedException("raw input not supported");
  }

  private Object parseString(String value, Type type)
      throws BadRequestException,
          SecurityException,
          NoSuchMethodException,
          IllegalArgumentException,
          IllegalAccessException,
          InstantiationException,
          InvocationTargetException {
    if (type == String.class) {
      return value;
    }

    Object obj = createInstance(type);
    if (Strings.isNullOrEmpty(value)) {
      return obj;
    }
    Field[] fields = obj.getClass().getDeclaredFields();
    for (Field f : fields) {
      if (f.getAnnotation(DefaultInput.class) != null && f.getType() == String.class) {
        f.setAccessible(true);
        f.set(obj, value);
        return obj;
      }
    }
    throw new BadRequestException("Expected JSON object");
  }

  private static Object createInstance(Type type)
      throws NoSuchMethodException,
          InstantiationException,
          IllegalAccessException,
          InvocationTargetException {
    if (type instanceof Class) {
      Class<?> clazz = (Class<?>) type;
      Constructor<?> c = clazz.getDeclaredConstructor();
      c.setAccessible(true);
      return c.newInstance();
    }
    if (type instanceof ParameterizedType) {
      Type rawType = ((ParameterizedType) type).getRawType();
      if (rawType instanceof Class && List.class.isAssignableFrom((Class<?>) rawType)) {
        return new ArrayList<>();
      }
      if (rawType instanceof Class && Map.class.isAssignableFrom((Class<?>) rawType)) {
        return new HashMap<>();
      }
    }
    throw new InstantiationException("Cannot make " + type);
  }

  /**
   * Sets a JSON reply on the given HTTP servlet response.
   *
   * @param req the HTTP servlet request
   * @param res the HTTP servlet response on which the reply should be set
   * @param allowTracing whether it is allowed to log the reply if tracing is enabled, must not be
   *     set to {@code true} if the reply may contain sensitive data
   * @param config config parameters for the JSON formatting
   * @param result the object that should be formatted as JSON
   * @return the length of the response
   */
  @CanIgnoreReturnValue
  public static long replyJson(
      @Nullable HttpServletRequest req,
      HttpServletResponse res,
      boolean allowTracing,
      ListMultimap<String, String> config,
      Object result)
      throws IOException {
    TemporaryBuffer.Heap buf = heap(HEAP_EST_SIZE, Integer.MAX_VALUE);
    buf.write(JSON_MAGIC);
    Writer w = new BufferedWriter(new OutputStreamWriter(buf, UTF_8));
    Gson gson = newGson(config);
    if (result instanceof JsonElement) {
      gson.toJson((JsonElement) result, w);
    } else {
      gson.toJson(result, w);
    }
    w.write('\n');
    w.flush();

    BinaryResult binaryResult = asBinaryResult(buf);
    if (allowTracing && binaryResult.getContentLength() <= ONE_KB) {
      logger.atFinest().log("JSON response body:\n%s", binaryResult.asString());
    }
    return replyBinaryResult(
        req, res, binaryResult.setContentType(JSON_TYPE).setCharacterEncoding(UTF_8));
  }

  private static Gson newGson(ListMultimap<String, String> config) {
    GsonBuilder gb = OutputFormat.JSON_COMPACT.newGsonBuilder();

    enablePrettyPrint(gb, config);
    enablePartialGetFields(gb, config);

    return gb.create();
  }

  private static void enablePrettyPrint(GsonBuilder gb, ListMultimap<String, String> config) {
    String pp =
        Iterables.getFirst(config.get("pp"), Iterables.getFirst(config.get("prettyPrint"), "0"));
    if ("1".equals(pp) || "true".equals(pp)) {
      gb.setPrettyPrinting();
    }
  }

  private static void enablePartialGetFields(GsonBuilder gb, ListMultimap<String, String> config) {
    final Set<String> want = new HashSet<>();
    for (String p : config.get("fields")) {
      Iterables.addAll(want, OptionUtil.splitOptionValue(p));
    }
    if (!want.isEmpty()) {
      gb.addSerializationExclusionStrategy(
          new ExclusionStrategy() {
            private final Map<String, String> names = new HashMap<>();

            @Override
            public boolean shouldSkipField(FieldAttributes field) {
              String name = names.get(field.getName());
              if (name == null) {
                // Names are supplied by Gson in terms of Java source.
                // Translate and cache the JSON lower_case_style used.
                try {
                  name =
                      FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES.translateName( //
                          field.getDeclaringClass().getDeclaredField(field.getName()));
                  names.put(field.getName(), name);
                } catch (SecurityException | NoSuchFieldException e) {
                  return true;
                }
              }
              return !want.contains(name);
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
              return false;
            }
          });
    }
  }

  @SuppressWarnings("resource")
  @CanIgnoreReturnValue
  static long replyBinaryResult(
      @Nullable HttpServletRequest req, HttpServletResponse res, BinaryResult bin)
      throws IOException {
    final BinaryResult appResult = bin;
    try {
      if (bin.getAttachmentName() != null) {
        res.setHeader(
            "Content-Disposition", "attachment; filename=\"" + bin.getAttachmentName() + "\"");
      }
      if (bin.isBase64()) {
        if (req != null && JSON_TYPE.equals(req.getHeader(HttpHeaders.ACCEPT))) {
          bin = stackJsonString(res, bin);
        } else {
          bin = stackBase64(res, bin);
        }
      }
      if (bin.canGzip() && acceptsGzip(req)) {
        bin = stackGzip(res, bin);
      }

      res.setContentType(bin.getContentType());
      long len = bin.getContentLength();
      if (0 <= len && len < Integer.MAX_VALUE) {
        res.setContentLength((int) len);
      } else if (0 <= len) {
        res.setHeader("Content-Length", Long.toString(len));
      }

      if (req == null || !"HEAD".equals(req.getMethod())) {
        try (CountingOutputStream dst = new CountingOutputStream(res.getOutputStream())) {
          bin.writeTo(dst);
          return dst.getCount();
        }
      }
      return 0;
    } finally {
      appResult.close();
    }
  }

  private static BinaryResult stackJsonString(HttpServletResponse res, BinaryResult src)
      throws IOException {
    TemporaryBuffer.Heap buf = heap(HEAP_EST_SIZE, Integer.MAX_VALUE);
    buf.write(JSON_MAGIC);
    try (Writer w = new BufferedWriter(new OutputStreamWriter(buf, UTF_8));
        JsonWriter json = new JsonWriter(w)) {
      json.setLenient(true);
      json.setHtmlSafe(true);
      json.value(src.asString());
      w.write('\n');
    }
    res.setHeader("X-FYI-Content-Encoding", "json");
    res.setHeader("X-FYI-Content-Type", src.getContentType());
    return asBinaryResult(buf).setContentType(JSON_TYPE).setCharacterEncoding(UTF_8);
  }

  private static BinaryResult stackBase64(HttpServletResponse res, BinaryResult src)
      throws IOException {
    BinaryResult b64;
    long len = src.getContentLength();
    if (0 <= len && len <= (7 << 20)) {
      b64 = base64(src);
    } else {
      b64 =
          new BinaryResult() {
            @Override
            public void writeTo(OutputStream out) throws IOException {
              try (OutputStreamWriter w =
                      new OutputStreamWriter(
                          new FilterOutputStream(out) {
                            @Override
                            public void close() {
                              // Do not close out, but only w and e.
                            }
                          },
                          ISO_8859_1);
                  OutputStream e = BaseEncoding.base64().encodingStream(w)) {
                src.writeTo(e);
              }
            }
          };
    }
    res.setHeader("X-FYI-Content-Encoding", "base64");
    res.setHeader("X-FYI-Content-Type", src.getContentType());
    return b64.setContentType(PLAIN_TEXT).setCharacterEncoding(ISO_8859_1);
  }

  private static BinaryResult stackGzip(HttpServletResponse res, BinaryResult src)
      throws IOException {
    BinaryResult gz;
    long len = src.getContentLength();
    if (len < 256) {
      return src; // Do not compress very small payloads.
    }
    if (len <= (10 << 20)) {
      gz = compress(src);
      if (len <= gz.getContentLength()) {
        return src;
      }
    } else {
      gz =
          new BinaryResult() {
            @Override
            public void writeTo(OutputStream out) throws IOException {
              GZIPOutputStream gz = new GZIPOutputStream(out);
              src.writeTo(gz);
              gz.finish();
              gz.flush();
            }
          };
    }
    res.setHeader("Content-Encoding", "gzip");
    return gz.setContentType(src.getContentType());
  }

  private ViewData view(
      RestCollection<RestResource, RestResource> rc, String method, List<IdString> path)
      throws AmbiguousViewException, RestApiException {
    DynamicMap<RestView<RestResource>> views = rc.views();
    final IdString projection = path.isEmpty() ? IdString.fromUrl("/") : path.remove(0);
    if (!path.isEmpty()) {
      // If there are path components still remaining after this projection
      // is chosen, look for the projection based upon GET as the method as
      // the client thinks it is a nested collection.
      method = "GET";
    } else if ("HEAD".equals(method)) {
      method = "GET";
    }

    List<String> p = splitProjection(projection);
    if (p.size() == 2) {
      String viewname = p.get(1);
      if (Strings.isNullOrEmpty(viewname)) {
        viewname = "/";
      }
      RestView<RestResource> view = views.get(p.get(0), method + "." + viewname);
      if (view != null) {
        return new ViewData(p.get(0), view);
      }
      view = views.get(p.get(0), "GET." + viewname);
      if (view != null) {
        return new ViewData(p.get(0), view);
      }
      throw new ResourceNotFoundException(projection);
    }

    String name = method + "." + p.get(0);
    RestView<RestResource> core = views.get(PluginName.GERRIT, name);
    if (core != null) {
      return new ViewData(PluginName.GERRIT, core);
    }

    // Check if we want to delegate to a child collection. Child collections are bound with
    // GET.name so we have to check for this since we haven't found any other views.
    if (method.equals("GET")) {
      core = views.get(PluginName.GERRIT, "GET." + p.get(0));
      if (core != null) {
        return new ViewData(PluginName.GERRIT, core);
      }
    }

    Map<String, RestView<RestResource>> r = new TreeMap<>();
    for (String plugin : views.plugins()) {
      RestView<RestResource> action = views.get(plugin, name);
      if (action != null) {
        r.put(plugin, action);
      }
    }

    if (r.isEmpty()) {
      // Check if we want to delegate to a child collection. Child collections are bound with
      // GET.name so we have to check for this since we haven't found any other views.
      for (String plugin : views.plugins()) {
        RestView<RestResource> action = views.get(plugin, "GET." + p.get(0));
        if (action != null) {
          r.put(plugin, action);
        }
      }
    }

    if (r.size() == 1) {
      Map.Entry<String, RestView<RestResource>> entry = Iterables.getOnlyElement(r.entrySet());
      return new ViewData(entry.getKey(), entry.getValue());
    }
    if (r.isEmpty()) {
      throw new ResourceNotFoundException(projection);
    }
    throw new AmbiguousViewException(
        String.format(
            "Projection %s is ambiguous: %s",
            name, r.keySet().stream().map(in -> in + "~" + projection).collect(joining(", "))));
  }

  private static List<IdString> splitPath(HttpServletRequest req) {
    String path = RequestUtil.getEncodedPathInfo(req);
    if (Strings.isNullOrEmpty(path)) {
      return new ArrayList<>();
    }
    List<IdString> out = new ArrayList<>();
    for (String p : Splitter.on('/').split(path)) {
      out.add(IdString.fromUrl(p));
    }
    if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
      out.remove(out.size() - 1);
    }
    return out;
  }

  private static List<String> splitProjection(IdString projection) {
    List<String> p = Lists.newArrayListWithCapacity(2);
    Iterables.addAll(p, Splitter.on('~').limit(2).split(projection.get()));
    return p;
  }

  private void checkUserSession(HttpServletRequest req) throws AuthException {
    CurrentUser user = globals.currentUser.get();
    if (isRead(req)) {
      if (user.getAccessPath().equals(AccessPath.UNKNOWN)) {
        user.setAccessPath(AccessPath.REST_API);
      }
    } else if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if (!globals.webSession.get().isAccessPathOk(AccessPath.REST_API)) {
      throw new AuthException(
          "Invalid authentication method. In order to authenticate, "
              + "prefix the REST endpoint URL with /a/ (e.g. http://example.com/a/projects/).");
    }
  }

  private List<String> getParameterNames(HttpServletRequest req) {
    List<String> parameterNames = new ArrayList<>(req.getParameterMap().keySet());
    Collections.sort(parameterNames);
    return parameterNames;
  }

  private RequestInfo createRequestInfo(
      TraceContext traceContext, HttpServletRequest req, String requestUri, List<IdString> path) {
    RequestInfo.Builder requestInfo =
        RequestInfo.builder(RequestInfo.RequestType.REST, globals.currentUser.get(), traceContext)
            .requestUri(requestUri);

    if (req.getQueryString() != null) {
      requestInfo.requestQueryString(req.getQueryString());
    }

    Enumeration<String> headerNames = req.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      Enumeration<String> headerValues = req.getHeaders(headerName);
      while (headerValues.hasMoreElements()) {
        String headerValue = headerValues.nextElement();
        requestInfo.addHeader(headerName, headerValue);
      }
    }

    if (path.size() < 1) {
      return requestInfo.build();
    }

    RestCollection<?, ?> rootCollection = members.get();
    String resourceId = path.get(0).get();
    if (rootCollection instanceof ProjectsCollection) {
      requestInfo.project(Project.nameKey(resourceId));
    } else if (rootCollection instanceof ChangesCollection) {
      try {
        Optional<ChangeNotes> changeNotes =
            globals
                .retryHelper
                .action(
                    ActionType.INDEX_QUERY,
                    "find-change",
                    () -> globals.changeFinder.findOne(resourceId))
                .call();
        if (changeNotes.isPresent()) {
          requestInfo.project(changeNotes.get().getProjectName());
        }
      } catch (Exception e) {
        logger.atWarning().withCause(e).log(
            "failed looking up change %s to populate project in request info", resourceId);
      }
    }
    return requestInfo.build();
  }

  private void traceRequestData(HttpServletRequest req) {
    logger.atFinest().log(
        "Received REST request: %s %s (parameters: %s)",
        req.getMethod(), req.getRequestURI(), getParameterNames(req));
    Optional.ofNullable(req.getHeader(X_GERRIT_DEADLINE))
        .ifPresent(
            clientProvidedDeadline ->
                logger.atFine().log("%s = %s", X_GERRIT_DEADLINE, clientProvidedDeadline));
    logger.atFinest().log("Calling user: %s", globals.currentUser.get().getLoggableName());
  }

  private boolean isDelete(HttpServletRequest req) {
    return "DELETE".equals(req.getMethod());
  }

  private static boolean isPost(HttpServletRequest req) {
    return "POST".equals(req.getMethod());
  }

  private boolean isPut(HttpServletRequest req) {
    return "PUT".equals(req.getMethod());
  }

  private static boolean isRead(HttpServletRequest req) {
    return "GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod());
  }

  private static MethodNotAllowedException methodNotAllowed(HttpServletRequest req) {
    return new MethodNotAllowedException(
        String.format("Not implemented: %s %s", req.getMethod(), requestUri(req)));
  }

  private static String requestUri(HttpServletRequest req) {
    String uri = req.getRequestURI();
    if (uri.startsWith("/a/")) {
      return uri.substring(2);
    }
    return uri;
  }

  private void checkRequiresCapability(ViewData d)
      throws AuthException, PermissionBackendException {
    try {
      globals.permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    } catch (AuthException e) {
      // Skiping
      globals
          .permissionBackend
          .currentUser()
          .checkAny(GlobalPermission.fromAnnotation(d.pluginName, d.view.getClass()));
    }
  }

  private Optional<ExceptionHook.Status> getStatus(Throwable err) {
    return globals.exceptionHooks.stream()
        .map(h -> h.getStatus(err))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  private ImmutableList<String> getUserMessages(Throwable err) {
    return globals.exceptionHooks.stream()
        .flatMap(h -> h.getUserMessages(err, TraceContext.getTraceIds()).stream())
        .collect(toImmutableList());
  }

  private static long reply(
      HttpServletRequest req,
      HttpServletResponse res,
      Throwable err,
      ExceptionHook.Status status,
      ImmutableList<String> userMessages)
      throws IOException {
    res.setStatus(status.statusCode());

    StringBuilder msg = new StringBuilder(status.statusMessage());
    if (!userMessages.isEmpty()) {
      msg.append("\n");
      userMessages.forEach(m -> msg.append("\n* ").append(m));
    }

    if (status.statusCode() < SC_BAD_REQUEST) {
      logger.atFinest().withCause(err).log("REST call finished: %d", status.statusCode());
      return replyText(req, res, true, msg.toString());
    }
    if (status.statusCode() >= SC_INTERNAL_SERVER_ERROR) {
      logger.atSevere().withCause(err).log("Error in %s %s", req.getMethod(), uriForLogging(req));
    }
    return replyError(req, res, status.statusCode(), msg.toString(), err);
  }

  private long replyInternalServerError(
      HttpServletRequest req,
      HttpServletResponse res,
      Throwable err,
      String viewName,
      ImmutableList<String> userMessages)
      throws IOException {
    logger.atSevere().withCause(err).log(
        "Error in %s %s (view: %s): %s",
        req.getMethod(), uriForLogging(req), viewName, globals.retryHelper.formatCause(err));

    StringBuilder msg = new StringBuilder("Internal server error");
    if (!userMessages.isEmpty()) {
      msg.append("\n");
      userMessages.forEach(m -> msg.append("\n* ").append(m));
    }

    return replyError(req, res, SC_INTERNAL_SERVER_ERROR, msg.toString(), err);
  }

  private static String uriForLogging(HttpServletRequest req) {
    String uri = req.getRequestURI();
    if (!Strings.isNullOrEmpty(req.getQueryString())) {
      uri += "?" + LogRedactUtil.redactQueryString(req.getQueryString());
    }
    return uri;
  }

  @CanIgnoreReturnValue
  public static long replyError(
      HttpServletRequest req,
      HttpServletResponse res,
      int statusCode,
      String msg,
      @Nullable Throwable err)
      throws IOException {
    return replyError(req, res, statusCode, msg, CacheControl.NONE, err);
  }

  @CanIgnoreReturnValue
  public static long replyError(
      HttpServletRequest req,
      HttpServletResponse res,
      int statusCode,
      String msg,
      CacheControl cacheControl,
      @Nullable Throwable err)
      throws IOException {
    if (err != null) {
      RequestUtil.setErrorTraceAttribute(req, err);
    }
    setCacheHeaders(req, res, cacheControl);
    checkArgument(statusCode >= 400, "non-error status: %s", statusCode);
    res.setStatus(statusCode);
    logger.atFinest().withCause(err).log("REST call failed: %d", statusCode);
    return replyText(req, res, true, msg);
  }

  /**
   * Sets a text reply on the given HTTP servlet response.
   *
   * @param req the HTTP servlet request
   * @param res the HTTP servlet response on which the reply should be set
   * @param allowTracing whether it is allowed to log the reply if tracing is enabled, must not be
   *     set to {@code true} if the reply may contain sensitive data
   * @param text the text reply
   * @return the length of the response
   */
  @CanIgnoreReturnValue
  static long replyText(
      @Nullable HttpServletRequest req, HttpServletResponse res, boolean allowTracing, String text)
      throws IOException {
    if (!text.endsWith("\n")) {
      text += "\n";
    }
    if (allowTracing) {
      logger.atFinest().log("Text response body:\n%s", text);
    }
    return replyBinaryResult(req, res, BinaryResult.create(text).setContentType(PLAIN_TEXT));
  }

  private static int getCancellationStatusCode(RequestStateProvider.Reason cancellationReason) {
    return switch (cancellationReason) {
      case CLIENT_CLOSED_REQUEST -> SC_CLIENT_CLOSED_REQUEST;
      case CLIENT_PROVIDED_DEADLINE_EXCEEDED -> SC_REQUEST_TIMEOUT;
      case SERVER_DEADLINE_EXCEEDED -> SC_INTERNAL_SERVER_ERROR;
    };
  }

  private static String getCancellationMessage(
      RequestCancelledException requestCancelledException) {
    StringBuilder msg = new StringBuilder(requestCancelledException.formatCancellationReason());
    if (requestCancelledException.getCancellationMessage().isPresent()) {
      msg.append("\n\n");
      msg.append(requestCancelledException.getCancellationMessage().get());
    }
    return msg.toString();
  }

  private static boolean acceptsGzip(HttpServletRequest req) {
    if (req != null) {
      String accepts = req.getHeader(HttpHeaders.ACCEPT_ENCODING);
      return accepts != null && accepts.contains("gzip");
    }
    return false;
  }

  private static boolean isType(String expect, String given) {
    if (given == null) {
      return false;
    }
    if (expect.equals(given)) {
      return true;
    }
    if (given.startsWith(expect + ",")) {
      return true;
    }
    for (String p : Splitter.on(TYPE_SPLIT_PATTERN).split(given)) {
      if (expect.equals(p)) {
        return true;
      }
    }
    return false;
  }

  private static int base64MaxSize(long n) {
    return 4 * IntMath.divide((int) n, 3, CEILING);
  }

  private static BinaryResult base64(BinaryResult bin) throws IOException {
    int maxSize = base64MaxSize(bin.getContentLength());
    int estSize = Math.min(base64MaxSize(HEAP_EST_SIZE), maxSize);
    TemporaryBuffer.Heap buf = heap(estSize, maxSize);
    try (OutputStream encoded =
        BaseEncoding.base64().encodingStream(new OutputStreamWriter(buf, ISO_8859_1))) {
      bin.writeTo(encoded);
    }
    return asBinaryResult(buf);
  }

  private static BinaryResult compress(BinaryResult bin) throws IOException {
    TemporaryBuffer.Heap buf = heap(HEAP_EST_SIZE, 20 << 20);
    try (GZIPOutputStream gz = new GZIPOutputStream(buf)) {
      bin.writeTo(gz);
    }
    return asBinaryResult(buf).setContentType(bin.getContentType());
  }

  @SuppressWarnings("resource")
  private static BinaryResult asBinaryResult(TemporaryBuffer.Heap buf) {
    return new BinaryResult() {
      @Override
      public void writeTo(OutputStream os) throws IOException {
        buf.writeTo(os, null);
      }
    }.setContentLength(buf.length());
  }

  private static Heap heap(int est, int max) {
    return new TemporaryBuffer.Heap(est, max);
  }

  private static class AmbiguousViewException extends Exception {
    private static final long serialVersionUID = 1L;

    AmbiguousViewException(String message) {
      super(message);
    }
  }

  static class ViewData {
    String pluginName;
    RestView<RestResource> view;

    ViewData(String pluginName, RestView<RestResource> view) {
      this.pluginName = pluginName;
      this.view = view;
    }
  }
}
