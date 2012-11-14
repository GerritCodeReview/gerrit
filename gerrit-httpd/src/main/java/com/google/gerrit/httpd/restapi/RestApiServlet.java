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

package com.google.gerrit.httpd.restapi;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.InvalidApiCallException;
import com.google.gerrit.extensions.restapi.InvalidApiResourceException;
import com.google.gerrit.extensions.restapi.InvalidApiAuthException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gwtjsonrpc.common.JsonConstants;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class RestApiServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory
      .getLogger(RestApiServlet.class);

  /** MIME type used for a JSON response body. */
  private static final String JSON_TYPE = JsonConstants.JSON_TYPE;
  private static final String UTF_8 = "UTF-8";

  /**
   * Garbage prefix inserted before JSON output to prevent XSSI.
   * <p>
   * This prefix is ")]}'\n" and is designed to prevent a web browser from
   * executing the response body if the resource URI were to be referenced using
   * a &lt;script src="...&gt; HTML tag from another web site. Clients using the
   * HTTP interface will need to always strip the first line of response data to
   * remove this magic header.
   */
  private static final byte[] JSON_MAGIC;

  static {
    try {
      JSON_MAGIC = ")]}'\n".getBytes(UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 not supported", e);
    }
  }

  protected static class Globals {
    final Provider<CurrentUser> currentUser;
    final Provider<WebSession> webSession;
    final Provider<ParameterParser> paramParser;

    @Inject
    Globals(Provider<CurrentUser> currentUser,
        Provider<WebSession> webSession,
        Provider<ParameterParser> paramParser) {
      this.currentUser = currentUser;
      this.webSession = webSession;
      this.paramParser = paramParser;
    }
  }

  private final Globals globals;
  private final Provider<RestCollection<RestResource>> members;

  protected RestApiServlet(Globals globals,
      Provider<? extends RestCollection<? extends RestResource>> members) {
    @SuppressWarnings("unchecked")
    Provider<RestCollection<RestResource>> n =
        (Provider<RestCollection<RestResource>>) checkNotNull((Object) members);
    this.globals = globals;
    this.members = n;
  }

  @Override
  protected final void service(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    res.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    res.setHeader("Pragma", "no-cache");
    res.setHeader("Cache-Control", "no-cache, must-revalidate");
    res.setHeader("Content-Disposition", "attachment");

    try {
      checkUserSession(req);

      List<String> path = splitPath(req);
      RestCollection<RestResource> rc = members.get();
      RestResource rsrc = path.isEmpty() ? null : rc.parse(path.remove(0));
      RestView<RestResource> view = view(rc, req.getMethod(), path);

      if (!path.isEmpty()) {
        // TODO: Handle nested collections automatically.
        throw new InvalidApiResourceException(path.get(0));
      }

      if (!globals.paramParser.get().parse(view, req, res)) {
        return;
      }

      Object result;
      if (view instanceof RestModifyView<?, ?>) {
        @SuppressWarnings("unchecked")
        RestModifyView<RestResource, Object> m =
            (RestModifyView<RestResource, Object>) view;

        if (!isJson(req.getContentType())) {
          throw new JsonSyntaxException("Expected " + JSON_TYPE);
        }
        result = m.apply(rsrc, parseJson(req, m.inputType()));
      } else if (view instanceof RestReadView<?>) {
        result = ((RestReadView<RestResource>) view).apply(rsrc);
      } else {
        log.warn(String.format(
            "View %s for %s has unknown interface",
            view.getClass(), req.getRequestURI()));
        throw new InvalidMethodException();
      }

      format(req, res, result);
    } catch (InvalidApiAuthException e) {
      sendError(res, SC_FORBIDDEN,
          Objects.firstNonNull(e.getMessage(), "Not signed-in"));
    } catch (InvalidMethodException e) {
      sendError(res, SC_METHOD_NOT_ALLOWED, "Method not allowed");
    } catch (InvalidApiCallException e) {
      sendError(res, SC_BAD_REQUEST, e.getMessage());
    } catch (InvalidApiResourceException e) {
      sendError(res, SC_NOT_FOUND, "Not found");
    } catch (AmbiguousViewException e) {
      sendError(res, SC_NOT_FOUND, e.getMessage());
    } catch (JsonParseException e) {
      sendError(res, SC_BAD_REQUEST, "Invalid " + JSON_TYPE);
    } catch (Exception e) {
      handleException(e, req, res);
    }
  }

  private Object parseJson(HttpServletRequest req, Class<Object> type) throws IOException {
    BufferedReader br = req.getReader();
    try {
      return OutputFormat.JSON.newGson().fromJson(br, type);
    } finally {
      br.close();
    }
  }

  private void format(HttpServletRequest req,
      HttpServletResponse res,
      Object result) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    buf.write(JSON_MAGIC);
    Writer w = new BufferedWriter(new OutputStreamWriter(buf, UTF_8));
    Gson gson = prettyPrint(req);
    if (result instanceof JsonElement) {
      gson.toJson((JsonElement) result, w);
    } else {
      gson.toJson(result, w);
    }
    w.write('\n');
    w.flush();

    res.setContentType(JSON_TYPE);
    res.setCharacterEncoding(UTF_8);
    sendBytes(req, res, buf.toByteArray());
  }

  private Gson prettyPrint(HttpServletRequest req) {
    String pp = req.getParameter("pp");
    if (pp == null ){
      pp = req.getParameter("prettyPrint");
    }
    if ("false".equals(pp) || "0".equals(pp) || acceptsJson(req)) {
      return OutputFormat.JSON_COMPACT.newGson();
    } else {
      return OutputFormat.JSON.newGson();
    }
  }

  private RestView<RestResource> view(RestCollection<RestResource> rc,
      String method, List<String> path) throws InvalidApiResourceException,
      InvalidMethodException, AmbiguousViewException {
    if (path.isEmpty()) {
      if (!"GET".equals(method)) {
        throw new InvalidMethodException();
      }
      return rc.list();
    }

    DynamicMap<RestView<RestResource>> views = rc.views();
    final String projection = path.remove(0);
    List<String> p = splitProjection(projection);
    if (p.size() == 2) {
      RestView<RestResource> view =
          views.get(p.get(0), method + "." + p.get(1));
      if (view != null) {
        return view;
      }
      throw new InvalidApiResourceException(projection);
    }

    String name = method + "." + p.get(0);
    RestView<RestResource> core = views.get("gerrit", name);
    if (core != null) {
      return core;
    }

    Map<String, RestView<RestResource>> r = Maps.newTreeMap();
    for (String plugin : views.plugins()) {
      RestView<RestResource> action = views.get(plugin, name);
      if (action != null) {
        r.put(plugin, action);
      }
    }

    if (r.size() == 1) {
      return Iterables.getFirst(r.values(), null);
    } else if (r.isEmpty()) {
      throw new InvalidApiResourceException(projection);
    } else {
      throw new AmbiguousViewException(String.format(
        "Projection %s is ambiguous: ",
        name,
        Joiner.on(", ").join(
          Iterables.transform(r.keySet(), new Function<String, String>() {
            @Override
            public String apply(String in) {
              return in + "~" + projection;
            }
          }))));
    }
  }

  private static List<String> splitPath(HttpServletRequest req) {
    String path = req.getPathInfo();
    if (Strings.isNullOrEmpty(path)) {
      return Collections.emptyList();
    }
    List<String> out = Lists.newArrayList(Splitter.on('/').split(path));
    if (out.size() > 0 && out.get(out.size() - 1).isEmpty()) {
      out.remove(out.size() - 1);
    }
    return out;
  }

  private static List<String> splitProjection(String projection) {
    return Lists.newArrayList(Splitter.on('~').limit(2).split(projection));
  }

  private void checkUserSession(HttpServletRequest req)
      throws InvalidApiAuthException {
    CurrentUser user = globals.currentUser.get();
    if (user instanceof AnonymousUser) {
      if (!"GET".equals(req.getMethod())) {
        throw new InvalidApiAuthException("Authentication required");
      }
    } else if (!globals.webSession.get().isAccessPathOk(AccessPath.REST_API)) {
      throw new InvalidApiAuthException("Invalid authentication method");
    }
    user.setAccessPath(AccessPath.REST_API);
  }

  private static void handleException(Throwable err, HttpServletRequest req,
      HttpServletResponse res) throws IOException {
    String uri = req.getRequestURI();
    if (!Strings.isNullOrEmpty(req.getQueryString())) {
      uri += "?" + req.getQueryString();
    }
    log.error(String.format("Error in %s %s", req.getMethod(), uri), err);

    if (!res.isCommitted()) {
      res.reset();
      sendError(res, SC_INTERNAL_SERVER_ERROR, "Internal server error");
    }
  }

  private static boolean acceptsJson(HttpServletRequest req) {
    return isJson(req.getHeader("Accept"));
  }

  private static boolean isJson(String accept) {
    if (accept == null) {
      return false;
    } else if (JSON_TYPE.equals(accept)) {
      return true;
    } else if (accept.startsWith(JSON_TYPE + ",")) {
      return true;
    }
    for (String p : accept.split("[ ,;][ ,;]*")) {
      if (JSON_TYPE.equals(p)) {
        return true;
      }
    }
    return false;
  }

  static void sendError(HttpServletResponse res, int statusCode, String msg)
      throws IOException {
    res.setStatus(statusCode);
    sendText(null, res, msg);
  }

  static void sendText(@Nullable HttpServletRequest req,
      HttpServletResponse res, String text) throws IOException {
    if (!text.endsWith("\n")) {
      text += "\n";
    }
    res.setContentType("text/plain");
    res.setCharacterEncoding(UTF_8);
    sendBytes(req, res, text.getBytes(UTF_8));
  }

  private static void sendBytes(@Nullable HttpServletRequest req,
      HttpServletResponse res, byte[] data) throws IOException {
    if (data.length > 256 && gzip(req)) {
      res.setHeader("Content-Encoding", "gzip");
      data = HtmlDomUtil.compress(data);
    }
    res.setContentLength(data.length);
    OutputStream out = res.getOutputStream();
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }

  private static boolean gzip(HttpServletRequest req) {
    return req != null && RPCServletUtils.acceptsGzipEncoding(req);
  }

  @SuppressWarnings("serial")
  private static class InvalidMethodException extends Exception {
  }

  @SuppressWarnings("serial")
  private static class AmbiguousViewException extends Exception {
    AmbiguousViewException(String message) {
      super(message);
    }
  }
}
