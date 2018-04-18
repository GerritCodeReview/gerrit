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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_MAX_AGE;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.ORIGIN;
import static com.google.common.net.HttpHeaders.VARY;
import static java.math.RoundingMode.CEILING;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static javax.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NOT_IMPLEMENTED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import com.google.common.io.CountingOutputStream;
import com.google.common.math.IntMath;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AcceptsDelete;
import com.google.gerrit.extensions.restapi.AcceptsPost;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ETagView;
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
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.restapi.ParameterParser.QueryParams;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OptionUtil;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.audit.ExtendedHttpAuditEvent;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.util.http.RequestUtil;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestApiServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(RestApiServlet.class);

  /** MIME type used for a JSON response body. */
  private static final String JSON_TYPE = "application/json";

  private static final String FORM_TYPE = "application/x-www-form-urlencoded";

  // HTTP 422 Unprocessable Entity.
  // TODO: Remove when HttpServletResponse.SC_UNPROCESSABLE_ENTITY is available
  private static final int SC_UNPROCESSABLE_ENTITY = 422;
  private static final String X_REQUESTED_WITH = "X-Requested-With";
  private static final String X_GERRIT_AUTH = "X-Gerrit-Auth";
  static final ImmutableSet<String> ALLOWED_CORS_METHODS =
      ImmutableSet.of("GET", "HEAD", "POST", "PUT", "DELETE");
  private static final ImmutableSet<String> ALLOWED_CORS_REQUEST_HEADERS =
      Stream.of(AUTHORIZATION, CONTENT_TYPE, X_GERRIT_AUTH, X_REQUESTED_WITH)
          .map(s -> s.toLowerCase(Locale.US))
          .collect(ImmutableSet.toImmutableSet());

  public static final String XD_AUTHORIZATION = "access_token";
  public static final String XD_CONTENT_TYPE = "$ct";
  public static final String XD_METHOD = "$m";

  private static final int HEAP_EST_SIZE = 10 * 8 * 1024; // Presize 10 blocks.
  private static final String PLAIN_TEXT = "text/plain";
  private static final Pattern TYPE_SPLIT_PATTERN = Pattern.compile("[ ,;][ ,;]*");

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
    final PermissionBackend permissionBackend;
    final AuditService auditService;
    final RestApiMetrics metrics;
    final Pattern allowOrigin;

    @Inject
    Globals(
        Provider<CurrentUser> currentUser,
        DynamicItem<WebSession> webSession,
        Provider<ParameterParser> paramParser,
        PermissionBackend permissionBackend,
        AuditService auditService,
        RestApiMetrics metrics,
        @GerritServerConfig Config cfg) {
      this.currentUser = currentUser;
      this.webSession = webSession;
      this.paramParser = paramParser;
      this.permissionBackend = permissionBackend;
      this.auditService = auditService;
      this.metrics = metrics;
      allowOrigin = makeAllowOrigin(cfg);
    }

    private static Pattern makeAllowOrigin(Config cfg) {
      String[] allow = cfg.getStringList("site", null, "allowOriginRegex");
      if (allow.length > 0) {
        return Pattern.compile(Joiner.on('|').join(allow));
      }
      return null;
    }
  }

  private final Globals globals;
  private final Provider<RestCollection<RestResource, RestResource>> members;

  public RestApiServlet(
      Globals globals, RestCollection<? extends RestResource, ? extends RestResource> members) {
    this(globals, Providers.of(members));
  }

  public RestApiServlet(
      Globals globals,
      Provider<? extends RestCollection<? extends RestResource, ? extends RestResource>> members) {
    @SuppressWarnings("unchecked")
    Provider<RestCollection<RestResource, RestResource>> n =
        (Provider<RestCollection<RestResource, RestResource>>) checkNotNull((Object) members);
    this.globals = globals;
    this.members = n;
  }

  @Override
  protected final void service(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    final long startNanos = System.nanoTime();
    long auditStartTs = TimeUtil.nowMs();
    res.setHeader("Content-Disposition", "attachment");
    res.setHeader("X-Content-Type-Options", "nosniff");
    int status = SC_OK;
    long responseBytes = -1;
    Object result = null;
    QueryParams qp = null;
    Object inputRequestBody = null;
    RestResource rsrc = TopLevelResource.INSTANCE;
    ViewData viewData = null;

    try (PerThreadCache ignored = PerThreadCache.create()) {
      if (isCorsPreflight(req)) {
        doCorsPreflight(req, res);
        return;
      }

      qp = ParameterParser.getQueryParams(req);
      checkCors(req, res, qp.hasXdOverride());
      if (qp.hasXdOverride()) {
        req = applyXdOverrides(req, qp);
      }
      checkUserSession(req);

      List<IdString> path = splitPath(req);
      RestCollection<RestResource, RestResource> rc = members.get();
      globals
          .permissionBackend
          .user(globals.currentUser.get())
          .checkAny(GlobalPermission.fromAnnotation(rc.getClass()));

      viewData = new ViewData(null, null);

      if (path.isEmpty()) {
        if (rc instanceof NeedsParams) {
          ((NeedsParams) rc).setParams(qp.params());
        }

        if (isRead(req)) {
          viewData = new ViewData(null, rc.list());
        } else if (rc instanceof AcceptsPost && isPost(req)) {
          @SuppressWarnings("unchecked")
          AcceptsPost<RestResource> ac = (AcceptsPost<RestResource>) rc;
          viewData = new ViewData(null, ac.post(rsrc));
        } else {
          throw new MethodNotAllowedException();
        }
      } else {
        IdString id = path.remove(0);
        try {
          rsrc = rc.parse(rsrc, id);
          if (path.isEmpty()) {
            checkPreconditions(req);
          }
        } catch (ResourceNotFoundException e) {
          if (rc instanceof AcceptsCreate && path.isEmpty() && (isPost(req) || isPut(req))) {
            @SuppressWarnings("unchecked")
            AcceptsCreate<RestResource> ac = (AcceptsCreate<RestResource>) rc;
            viewData = new ViewData(null, ac.create(rsrc, id));
            status = SC_CREATED;
          } else {
            throw e;
          }
        }
        if (viewData.view == null) {
          viewData = view(rsrc, rc, req.getMethod(), path);
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
          } else if (c instanceof AcceptsPost && isPost(req)) {
            @SuppressWarnings("unchecked")
            AcceptsPost<RestResource> ac = (AcceptsPost<RestResource>) c;
            viewData = new ViewData(null, ac.post(rsrc));
          } else if (c instanceof AcceptsDelete && isDelete(req)) {
            @SuppressWarnings("unchecked")
            AcceptsDelete<RestResource> ac = (AcceptsDelete<RestResource>) c;
            viewData = new ViewData(null, ac.delete(rsrc, null));
          } else {
            throw new MethodNotAllowedException();
          }
          break;
        }
        IdString id = path.remove(0);
        try {
          rsrc = c.parse(rsrc, id);
          checkPreconditions(req);
          viewData = new ViewData(null, null);
        } catch (ResourceNotFoundException e) {
          if (c instanceof AcceptsCreate && path.isEmpty() && (isPost(req) || isPut(req))) {
            @SuppressWarnings("unchecked")
            AcceptsCreate<RestResource> ac = (AcceptsCreate<RestResource>) c;
            viewData = new ViewData(viewData.pluginName, ac.create(rsrc, id));
            status = SC_CREATED;
          } else if (c instanceof AcceptsDelete && path.isEmpty() && isDelete(req)) {
            @SuppressWarnings("unchecked")
            AcceptsDelete<RestResource> ac = (AcceptsDelete<RestResource>) c;
            viewData = new ViewData(viewData.pluginName, ac.delete(rsrc, id));
            status = SC_NO_CONTENT;
          } else {
            throw e;
          }
        }
        if (viewData.view == null) {
          viewData = view(rsrc, c, req.getMethod(), path);
        }
        checkRequiresCapability(viewData);
      }

      if (notModified(req, rsrc, viewData.view)) {
        res.sendError(SC_NOT_MODIFIED);
        return;
      }

      if (!globals.paramParser.get().parse(viewData.view, qp.params(), req, res)) {
        return;
      }

      if (viewData.view instanceof RestReadView<?> && isRead(req)) {
        result = ((RestReadView<RestResource>) viewData.view).apply(rsrc);
      } else if (viewData.view instanceof RestModifyView<?, ?>) {
        @SuppressWarnings("unchecked")
        RestModifyView<RestResource, Object> m =
            (RestModifyView<RestResource, Object>) viewData.view;

        Type type = inputType(m);
        inputRequestBody = parseRequest(req, type);
        result = m.apply(rsrc, inputRequestBody);
        if (inputRequestBody instanceof RawInput) {
          try (InputStream is = req.getInputStream()) {
            ServletUtils.consumeRequestBody(is);
          }
        }
      } else {
        throw new ResourceNotFoundException();
      }

      if (result instanceof Response) {
        @SuppressWarnings("rawtypes")
        Response<?> r = (Response) result;
        status = r.statusCode();
        configureCaching(req, res, rsrc, viewData.view, r.caching());
      } else if (result instanceof Response.Redirect) {
        CacheHeaders.setNotCacheable(res);
        res.sendRedirect(((Response.Redirect) result).location());
        return;
      } else if (result instanceof Response.Accepted) {
        CacheHeaders.setNotCacheable(res);
        res.setStatus(SC_ACCEPTED);
        res.setHeader(HttpHeaders.LOCATION, ((Response.Accepted) result).location());
        return;
      } else {
        CacheHeaders.setNotCacheable(res);
      }
      res.setStatus(status);

      if (result != Response.none()) {
        result = Response.unwrap(result);
        if (result instanceof BinaryResult) {
          responseBytes = replyBinaryResult(req, res, (BinaryResult) result);
        } else {
          responseBytes = replyJson(req, res, qp.config(), result);
        }
      }
    } catch (MalformedJsonException | JsonParseException e) {
      responseBytes =
          replyError(req, res, status = SC_BAD_REQUEST, "Invalid " + JSON_TYPE + " in request", e);
    } catch (BadRequestException e) {
      responseBytes =
          replyError(
              req, res, status = SC_BAD_REQUEST, messageOr(e, "Bad Request"), e.caching(), e);
    } catch (AuthException e) {
      responseBytes =
          replyError(req, res, status = SC_FORBIDDEN, messageOr(e, "Forbidden"), e.caching(), e);
    } catch (AmbiguousViewException e) {
      responseBytes = replyError(req, res, status = SC_NOT_FOUND, messageOr(e, "Ambiguous"), e);
    } catch (ResourceNotFoundException e) {
      responseBytes =
          replyError(req, res, status = SC_NOT_FOUND, messageOr(e, "Not Found"), e.caching(), e);
    } catch (MethodNotAllowedException e) {
      responseBytes =
          replyError(
              req,
              res,
              status = SC_METHOD_NOT_ALLOWED,
              messageOr(e, "Method Not Allowed"),
              e.caching(),
              e);
    } catch (ResourceConflictException e) {
      responseBytes =
          replyError(req, res, status = SC_CONFLICT, messageOr(e, "Conflict"), e.caching(), e);
    } catch (PreconditionFailedException e) {
      responseBytes =
          replyError(
              req,
              res,
              status = SC_PRECONDITION_FAILED,
              messageOr(e, "Precondition Failed"),
              e.caching(),
              e);
    } catch (UnprocessableEntityException e) {
      responseBytes =
          replyError(
              req,
              res,
              status = SC_UNPROCESSABLE_ENTITY,
              messageOr(e, "Unprocessable Entity"),
              e.caching(),
              e);
    } catch (NotImplementedException e) {
      responseBytes =
          replyError(req, res, status = SC_NOT_IMPLEMENTED, messageOr(e, "Not Implemented"), e);
    } catch (UpdateException e) {
      Throwable t = e.getCause();
      if (t instanceof LockFailureException) {
        responseBytes =
            replyError(req, res, status = SC_SERVICE_UNAVAILABLE, messageOr(t, "Lock failure"), e);
      } else {
        status = SC_INTERNAL_SERVER_ERROR;
        responseBytes = handleException(e, req, res);
      }
    } catch (Exception e) {
      status = SC_INTERNAL_SERVER_ERROR;
      responseBytes = handleException(e, req, res);
    } finally {
      String metric =
          viewData != null && viewData.view != null ? globals.metrics.view(viewData) : "_unknown";
      globals.metrics.count.increment(metric);
      if (status >= SC_BAD_REQUEST) {
        globals.metrics.errorCount.increment(metric, status);
      }
      if (responseBytes != -1) {
        globals.metrics.responseBytes.record(metric, responseBytes);
      }
      globals.metrics.serverLatency.record(
          metric, System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
      globals.auditService.dispatch(
          new ExtendedHttpAuditEvent(
              globals.webSession.get().getSessionId(),
              globals.currentUser.get(),
              req,
              auditStartTs,
              qp != null ? qp.params() : ImmutableListMultimap.of(),
              inputRequestBody,
              status,
              result,
              rsrc,
              viewData == null ? null : viewData.view));
    }
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

  private void checkCors(HttpServletRequest req, HttpServletResponse res, boolean isXd)
      throws BadRequestException {
    String origin = req.getHeader(ORIGIN);
    if (isXd) {
      // Cross-domain, non-preflighted requests must come from an approved origin.
      if (Strings.isNullOrEmpty(origin) || !isOriginAllowed(origin)) {
        throw new BadRequestException("origin not allowed");
      }
      res.addHeader(VARY, ORIGIN);
      res.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
      res.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    } else if (!Strings.isNullOrEmpty(origin)) {
      // All other requests must be processed, but conditionally set CORS headers.
      if (globals.allowOrigin != null) {
        res.addHeader(VARY, ORIGIN);
      }
      if (isOriginAllowed(origin)) {
        setCorsHeaders(res, origin);
      }
    }
  }

  private static boolean isCorsPreflight(HttpServletRequest req) {
    return "OPTIONS".equals(req.getMethod())
        && !Strings.isNullOrEmpty(req.getHeader(ORIGIN))
        && !Strings.isNullOrEmpty(req.getHeader(ACCESS_CONTROL_REQUEST_METHOD));
  }

  private void doCorsPreflight(HttpServletRequest req, HttpServletResponse res)
      throws BadRequestException {
    CacheHeaders.setNotCacheable(res);
    setHeaderList(
        res,
        VARY,
        ImmutableList.of(ORIGIN, ACCESS_CONTROL_REQUEST_METHOD, ACCESS_CONTROL_REQUEST_HEADERS));

    String origin = req.getHeader(ORIGIN);
    if (Strings.isNullOrEmpty(origin) || !isOriginAllowed(origin)) {
      throw new BadRequestException("CORS not allowed");
    }

    String method = req.getHeader(ACCESS_CONTROL_REQUEST_METHOD);
    if (!ALLOWED_CORS_METHODS.contains(method)) {
      throw new BadRequestException(method + " not allowed in CORS");
    }

    String headers = req.getHeader(ACCESS_CONTROL_REQUEST_HEADERS);
    if (headers != null) {
      for (String reqHdr : Splitter.on(',').trimResults().split(headers)) {
        if (!ALLOWED_CORS_REQUEST_HEADERS.contains(reqHdr.toLowerCase(Locale.US))) {
          throw new BadRequestException(reqHdr + " not allowed in CORS");
        }
      }
    }

    res.setStatus(SC_OK);
    setCorsHeaders(res, origin);
    res.setContentType(PLAIN_TEXT);
    res.setContentLength(0);
  }

  private static void setCorsHeaders(HttpServletResponse res, String origin) {
    res.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    res.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    res.setHeader(ACCESS_CONTROL_MAX_AGE, "600");
    setHeaderList(
        res,
        ACCESS_CONTROL_ALLOW_METHODS,
        Iterables.concat(ALLOWED_CORS_METHODS, ImmutableList.of("OPTIONS")));
    setHeaderList(res, ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_CORS_REQUEST_HEADERS);
  }

  private static void setHeaderList(HttpServletResponse res, String name, Iterable<String> values) {
    res.setHeader(name, Joiner.on(", ").join(values));
  }

  private boolean isOriginAllowed(String origin) {
    return globals.allowOrigin != null && globals.allowOrigin.matcher(origin).matches();
  }

  private static String messageOr(Throwable t, String defaultMessage) {
    if (!Strings.isNullOrEmpty(t.getMessage())) {
      return t.getMessage();
    }
    return defaultMessage;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static boolean notModified(
      HttpServletRequest req, RestResource rsrc, RestView<RestResource> view) {
    if (!isRead(req)) {
      return false;
    }

    if (view instanceof ETagView) {
      String have = req.getHeader(HttpHeaders.IF_NONE_MATCH);
      if (have != null) {
        return have.equals(((ETagView) view).getETag(rsrc));
      }
    }

    if (rsrc instanceof RestResource.HasETag) {
      String have = req.getHeader(HttpHeaders.IF_NONE_MATCH);
      if (have != null) {
        return have.equals(((RestResource.HasETag) rsrc).getETag());
      }
    }

    if (rsrc instanceof RestResource.HasLastModified) {
      Timestamp m = ((RestResource.HasLastModified) rsrc).getLastModified();
      long d = req.getDateHeader(HttpHeaders.IF_MODIFIED_SINCE);

      // HTTP times are in seconds, database may have millisecond precision.
      return d / 1000L == m.getTime() / 1000L;
    }
    return false;
  }

  private static <R extends RestResource> void configureCaching(
      HttpServletRequest req, HttpServletResponse res, R rsrc, RestView<R> view, CacheControl c) {
    if (isRead(req)) {
      switch (c.getType()) {
        case NONE:
        default:
          CacheHeaders.setNotCacheable(res);
          break;
        case PRIVATE:
          addResourceStateHeaders(res, rsrc, view);
          CacheHeaders.setCacheablePrivate(res, c.getAge(), c.getUnit(), c.isMustRevalidate());
          break;
        case PUBLIC:
          addResourceStateHeaders(res, rsrc, view);
          CacheHeaders.setCacheable(req, res, c.getAge(), c.getUnit(), c.isMustRevalidate());
          break;
      }
    } else {
      CacheHeaders.setNotCacheable(res);
    }
  }

  private static <R extends RestResource> void addResourceStateHeaders(
      HttpServletResponse res, R rsrc, RestView<R> view) {
    if (view instanceof ETagView) {
      res.setHeader(HttpHeaders.ETAG, ((ETagView<R>) view).getETag(rsrc));
    } else if (rsrc instanceof RestResource.HasETag) {
      res.setHeader(HttpHeaders.ETAG, ((RestResource.HasETag) rsrc).getETag());
    }
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

  private Object parseRequest(HttpServletRequest req, Type type)
      throws IOException, BadRequestException, SecurityException, IllegalArgumentException,
          NoSuchMethodException, IllegalAccessException, InstantiationException,
          InvocationTargetException, MethodNotAllowedException {
    // HTTP/1.1 requires consuming the request body before writing non-error response (less than
    // 400). Consume the request body for all but raw input request types here.
    if (isType(JSON_TYPE, req.getContentType())) {
      try (BufferedReader br = req.getReader();
          JsonReader json = new JsonReader(br)) {
        try {
          json.setLenient(true);

          JsonToken first;
          try {
            first = json.peek();
          } catch (EOFException e) {
            throw new BadRequestException("Expected JSON object");
          }
          if (first == JsonToken.STRING) {
            return parseString(json.nextString(), type);
          }
          return OutputFormat.JSON.newGson().fromJson(json, type);
        } finally {
          // Reader.close won't consume the rest of the input. Explicitly consume the request body.
          br.skip(Long.MAX_VALUE);
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
      throws SecurityException, NoSuchMethodException, IllegalArgumentException,
          InstantiationException, IllegalAccessException, InvocationTargetException,
          MethodNotAllowedException {
    Object obj = createInstance(type);
    for (Field f : obj.getClass().getDeclaredFields()) {
      if (f.getType() == RawInput.class) {
        f.setAccessible(true);
        f.set(obj, RawInputUtil.create(req));
        return obj;
      }
    }
    throw new MethodNotAllowedException();
  }

  private Object parseString(String value, Type type)
      throws BadRequestException, SecurityException, NoSuchMethodException,
          IllegalArgumentException, IllegalAccessException, InstantiationException,
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
      throws NoSuchMethodException, InstantiationException, IllegalAccessException,
          InvocationTargetException {
    if (type instanceof Class) {
      @SuppressWarnings("unchecked")
      Class<Object> clazz = (Class<Object>) type;
      Constructor<Object> c = clazz.getDeclaredConstructor();
      c.setAccessible(true);
      return c.newInstance();
    }
    throw new InstantiationException("Cannot make " + type);
  }

  public static long replyJson(
      @Nullable HttpServletRequest req,
      HttpServletResponse res,
      ListMultimap<String, String> config,
      Object result)
      throws IOException {
    TemporaryBuffer.Heap buf = heap(HEAP_EST_SIZE, Integer.MAX_VALUE);
    buf.write(JSON_MAGIC);
    Writer w = new BufferedWriter(new OutputStreamWriter(buf, UTF_8));
    Gson gson = newGson(config, req);
    if (result instanceof JsonElement) {
      gson.toJson((JsonElement) result, w);
    } else {
      gson.toJson(result, w);
    }
    w.write('\n');
    w.flush();
    return replyBinaryResult(
        req, res, asBinaryResult(buf).setContentType(JSON_TYPE).setCharacterEncoding(UTF_8));
  }

  private static Gson newGson(
      ListMultimap<String, String> config, @Nullable HttpServletRequest req) {
    GsonBuilder gb = OutputFormat.JSON_COMPACT.newGsonBuilder();

    enablePrettyPrint(gb, config, req);
    enablePartialGetFields(gb, config);

    return gb.create();
  }

  private static void enablePrettyPrint(
      GsonBuilder gb, ListMultimap<String, String> config, @Nullable HttpServletRequest req) {
    String pp = Iterables.getFirst(config.get("pp"), null);
    if (pp == null) {
      pp = Iterables.getFirst(config.get("prettyPrint"), null);
      if (pp == null && req != null) {
        pp = acceptsJson(req) ? "0" : "1";
      }
    }
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
      RestResource rsrc,
      RestCollection<RestResource, RestResource> rc,
      String method,
      List<IdString> path)
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
      if (view != null && view instanceof AcceptsPost && "POST".equals(method)) {
        @SuppressWarnings("unchecked")
        AcceptsPost<RestResource> ap = (AcceptsPost<RestResource>) view;
        return new ViewData(p.get(0), ap.post(rsrc));
      }
      throw new ResourceNotFoundException(projection);
    }

    String name = method + "." + p.get(0);
    RestView<RestResource> core = views.get("gerrit", name);
    if (core != null) {
      return new ViewData(null, core);
    }
    core = views.get("gerrit", "GET." + p.get(0));
    if (core instanceof AcceptsPost && "POST".equals(method)) {
      @SuppressWarnings("unchecked")
      AcceptsPost<RestResource> ap = (AcceptsPost<RestResource>) core;
      return new ViewData(null, ap.post(rsrc));
    }

    Map<String, RestView<RestResource>> r = new TreeMap<>();
    for (String plugin : views.plugins()) {
      RestView<RestResource> action = views.get(plugin, name);
      if (action != null) {
        r.put(plugin, action);
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
      return Collections.emptyList();
    }
    List<IdString> out = new ArrayList<>();
    for (String p : Splitter.on('/').split(path)) {
      out.add(IdString.fromUrl(p));
    }
    if (out.size() > 0 && out.get(out.size() - 1).isEmpty()) {
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
      user.setAccessPath(AccessPath.REST_API);
    } else if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if (!globals.webSession.get().isAccessPathOk(AccessPath.REST_API)) {
      throw new AuthException(
          "Invalid authentication method. In order to authenticate, "
              + "prefix the REST endpoint URL with /a/ (e.g. http://example.com/a/projects/).");
    }
    if (user.isIdentifiedUser()) {
      user.setLastLoginExternalIdKey(globals.webSession.get().getLastLoginExternalId());
    }
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

  private void checkRequiresCapability(ViewData d)
      throws AuthException, PermissionBackendException {
    globals
        .permissionBackend
        .user(globals.currentUser.get())
        .checkAny(GlobalPermission.fromAnnotation(d.pluginName, d.view.getClass()));
  }

  private static long handleException(
      Throwable err, HttpServletRequest req, HttpServletResponse res) throws IOException {
    String uri = req.getRequestURI();
    if (!Strings.isNullOrEmpty(req.getQueryString())) {
      uri += "?" + req.getQueryString();
    }
    log.error("Error in {} {}", req.getMethod(), uri, err);

    if (!res.isCommitted()) {
      res.reset();
      return replyError(req, res, SC_INTERNAL_SERVER_ERROR, "Internal server error", err);
    }
    return 0;
  }

  public static long replyError(
      HttpServletRequest req,
      HttpServletResponse res,
      int statusCode,
      String msg,
      @Nullable Throwable err)
      throws IOException {
    return replyError(req, res, statusCode, msg, CacheControl.NONE, err);
  }

  public static long replyError(
      HttpServletRequest req,
      HttpServletResponse res,
      int statusCode,
      String msg,
      CacheControl c,
      @Nullable Throwable err)
      throws IOException {
    if (err != null) {
      RequestUtil.setErrorTraceAttribute(req, err);
    }
    configureCaching(req, res, null, null, c);
    checkArgument(statusCode >= 400, "non-error status: %s", statusCode);
    res.setStatus(statusCode);
    return replyText(req, res, msg);
  }

  static long replyText(@Nullable HttpServletRequest req, HttpServletResponse res, String text)
      throws IOException {
    if ((req == null || isRead(req)) && isMaybeHTML(text)) {
      return replyJson(req, res, ImmutableListMultimap.of("pp", "0"), new JsonPrimitive(text));
    }
    if (!text.endsWith("\n")) {
      text += "\n";
    }
    return replyBinaryResult(req, res, BinaryResult.create(text).setContentType(PLAIN_TEXT));
  }

  private static boolean isMaybeHTML(String text) {
    return CharMatcher.anyOf("<&").matchesAnyOf(text);
  }

  private static boolean acceptsJson(HttpServletRequest req) {
    return req != null && isType(JSON_TYPE, req.getHeader(HttpHeaders.ACCEPT));
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

  @SuppressWarnings("serial")
  private static class AmbiguousViewException extends Exception {
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
