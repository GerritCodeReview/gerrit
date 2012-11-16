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
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsCreate;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.PutInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gwtjsonrpc.common.JsonConstants;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.TemporaryBuffer.Heap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RestApiServlet extends HttpServlet {
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

  public static class Globals {
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
  private final Provider<RestCollection<RestResource, RestResource>> members;

  public RestApiServlet(Globals globals,
      RestCollection<? extends RestResource, ? extends RestResource> members) {
    this(globals, Providers.of(members));
  }

  public RestApiServlet(Globals globals,
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
    res.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    res.setHeader("Pragma", "no-cache");
    res.setHeader("Cache-Control", "no-cache, must-revalidate");
    res.setHeader("Content-Disposition", "attachment");

    try {
      checkUserSession(req);

      List<String> path = splitPath(req);
      RestCollection<RestResource, RestResource> rc = members.get();
      RestResource rsrc = TopLevelResource.INSTANCE;
      RestView<RestResource> view = null;
      if (path.isEmpty()) {
        view = rc.list();
      } else {
        String id = path.remove(0);
        try {
          rsrc = rc.parse(rsrc, id);
        } catch (ResourceNotFoundException e) {
          if (rc instanceof AcceptsCreate
              && ("POST".equals(req.getMethod())
                  || "PUT".equals(req.getMethod()))) {
            @SuppressWarnings("unchecked")
            AcceptsCreate<RestResource> ac = (AcceptsCreate<RestResource>) rc;
            view = ac.create(rsrc, id);
          } else {
            throw e;
          }
        }
        if (view == null) {
          view = view(rc, req.getMethod(), path);
        }
      }

      while (view instanceof RestCollection<?,?>) {
        @SuppressWarnings("unchecked")
        RestCollection<RestResource, RestResource> c =
            (RestCollection<RestResource, RestResource>) view;

        if (path.isEmpty()) {
          view = c.list();
          break;
        } else {
          rsrc = c.parse(rsrc, path.remove(0));
          view = view(c, req.getMethod(), path);
        }
      }

      Multimap<String, String> config = LinkedHashMultimap.create();
      Multimap<String, String> params = LinkedHashMultimap.create();
      ParameterParser.splitQueryString(req.getQueryString(), config, params);
      if (!globals.paramParser.get().parse(view, params, req, res)) {
        return;
      }

      Object result;
      if (view instanceof RestModifyView<?, ?>) {
        @SuppressWarnings("unchecked")
        RestModifyView<RestResource, Object> m =
            (RestModifyView<RestResource, Object>) view;

        result = m.apply(rsrc, parseRequest(req, m.inputType()));
      } else if (view instanceof RestReadView<?>) {
        result = ((RestReadView<RestResource>) view).apply(rsrc);
      } else {
        throw new ResourceNotFoundException();
      }

      if (result instanceof BinaryResult) {
        replyBinaryResult(req, res, (BinaryResult) result);
      } else {
        replyJson(req, res, config, result);
      }
    } catch (AuthException e) {
      replyError(res, SC_FORBIDDEN, e.getMessage());
    } catch (BadRequestException e) {
      replyError(res, SC_BAD_REQUEST, e.getMessage());
    } catch (InvalidMethodException e) {
      replyError(res, SC_METHOD_NOT_ALLOWED, "Method not allowed");
    } catch (ResourceConflictException e) {
      replyError(res, SC_CONFLICT, e.getMessage());
    } catch (ResourceNotFoundException e) {
      replyError(res, SC_NOT_FOUND, "Not found");
    } catch (AmbiguousViewException e) {
      replyError(res, SC_NOT_FOUND, e.getMessage());
    } catch (JsonParseException e) {
      replyError(res, SC_BAD_REQUEST, "Invalid " + JSON_TYPE + " in request");
    } catch (Exception e) {
      handleException(e, req, res);
    }
  }

  private Object parseRequest(HttpServletRequest req, Class<Object> type)
      throws IOException, BadRequestException, SecurityException,
      IllegalArgumentException, NoSuchMethodException, IllegalAccessException,
      InstantiationException, InvocationTargetException, InvalidMethodException {
    if (isType(JSON_TYPE, req.getContentType())) {
      BufferedReader br = req.getReader();
      try {
        JsonReader json = new JsonReader(br);
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
        br.close();
      }
    } else if ("PUT".equals(req.getMethod()) && acceptsPutInput(type)) {
      return parsePutInput(req, type);
    } else if ("DELETE".equals(req.getMethod()) && hasNoBody(req)) {
      return null;
    } else if (type.getDeclaredFields().length == 0 && hasNoBody(req)) {
      return createInstance(type);
    } else if (isType("text/plain", req.getContentType())) {
      BufferedReader br = req.getReader();
      try {
        char[] tmp = new char[256];
        StringBuilder sb = new StringBuilder();
        int n;
        while (0 < (n = br.read(tmp))) {
          sb.append(tmp, 0, n);
        }
        return parseString(sb.toString(), type);
      } finally {
        br.close();
      }
    } else {
      throw new BadRequestException("Expected Content-Type: " + JSON_TYPE);
    }
  }

  private static boolean hasNoBody(HttpServletRequest req) {
    int len = req.getContentLength();
    String type = req.getContentType();
    return (len <= 0 && type == null)
        || (len == 0 && isType("application/x-www-form-urlencoded", type));
  }

  private static boolean acceptsPutInput(Class<Object> type) {
    for (Field f : type.getDeclaredFields()) {
      if (f.getType() == PutInput.class) {
        return true;
      }
    }
    return false;
  }

  private Object parsePutInput(final HttpServletRequest req, Class<Object> type)
      throws SecurityException, NoSuchMethodException,
      IllegalArgumentException, InstantiationException, IllegalAccessException,
      InvocationTargetException, InvalidMethodException {
    Object obj = createInstance(type);
    for (Field f : type.getDeclaredFields()) {
      if (f.getType() == PutInput.class) {
        f.setAccessible(true);
        f.set(obj, new PutInput() {
          @Override
          public String getContentType() {
            return req.getContentType();
          }

          @Override
          public long getContentLength() {
            return req.getContentLength();
          }

          @Override
          public InputStream getInputStream() throws IOException {
            return req.getInputStream();
          }
        });
        return obj;
      }
    }
    throw new InvalidMethodException();
  }

  private Object parseString(String value, Class<Object> type)
      throws BadRequestException, SecurityException, NoSuchMethodException,
      IllegalArgumentException, IllegalAccessException, InstantiationException,
      InvocationTargetException {
    Object obj = createInstance(type);
    Field[] fields = type.getDeclaredFields();
    if (fields.length == 0 && Strings.isNullOrEmpty(value)) {
      return obj;
    }
    for (Field f : fields) {
      if (f.getAnnotation(DefaultInput.class) != null
          && f.getType() == String.class) {
        f.setAccessible(true);
        f.set(obj, value);
        return obj;
      }
    }
    throw new BadRequestException("Expected JSON object");
  }

  private static Object createInstance(Class<Object> type)
      throws NoSuchMethodException, InstantiationException,
      IllegalAccessException, InvocationTargetException {
    Constructor<Object> c = type.getDeclaredConstructor();
    c.setAccessible(true);
    return c.newInstance();
  }

  private static void replyJson(HttpServletRequest req,
      HttpServletResponse res,
      Multimap<String, String> config, Object result)
      throws IOException {
    final TemporaryBuffer.Heap buf = heap(Integer.MAX_VALUE);
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

    replyBinaryResult(req, res, new BinaryResult() {
      @Override
      public long getContentLength() {
        return buf.length();
      }

      @Override
      public void writeTo(OutputStream os) throws IOException {
        buf.writeTo(os, null);
      }
    }.setContentType(JSON_TYPE).setCharacterEncoding(UTF_8));
  }

  private static final FieldNamingPolicy NAMING =
      FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

  private static Gson newGson(Multimap<String, String> config,
      HttpServletRequest req) {
    GsonBuilder gb = OutputFormat.JSON_COMPACT.newGsonBuilder()
      .setFieldNamingPolicy(NAMING);

    enablePrettyPrint(gb, config, req);
    enablePartialGetFields(gb, config);

    return gb.create();
  }

  private static void enablePrettyPrint(GsonBuilder gb,
      Multimap<String, String> config, HttpServletRequest req) {
    String pp = Iterables.getFirst(config.get("pp"), null);
    if (pp == null) {
      pp = Iterables.getFirst(config.get("prettyPrint"), null);
      if (pp == null) {
        pp = acceptsJson(req) ? "0" : "1";
      }
    }
    if ("1".equals(pp) || "true".equals(pp)) {
      gb.setPrettyPrinting();
    }
  }

  private static void enablePartialGetFields(GsonBuilder gb,
      Multimap<String, String> config) {
    final Set<String> want = Sets.newHashSet(config.get("fields"));
    if (!want.isEmpty()) {
      gb.addSerializationExclusionStrategy(new ExclusionStrategy() {
        private final Map<String, String> names = Maps.newHashMap();

        @Override
        public boolean shouldSkipField(FieldAttributes field) {
          String name = names.get(field.getName());
          if (name == null) {
            // Names are supplied by Gson in terms of Java source.
            // Translate and cache the JSON lower_case_style used.
            try {
              name = NAMING.translateName(
                  field.getDeclaringClass().getDeclaredField(field.getName()));
              names.put(field.getName(), name);
            } catch (SecurityException e) {
              return true;
            } catch (NoSuchFieldException e) {
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

  private static void replyBinaryResult(HttpServletRequest req,
      HttpServletResponse res, BinaryResult bin) throws IOException {
    try {
      res.setContentType(bin.getContentType());
      OutputStream dst = res.getOutputStream();
      try {
        long len = bin.getContentLength();
        boolean gzip = bin.canGzip() && acceptsGzip(req);
        if (gzip && 256 <= len && len <= (10 << 20)) {
          TemporaryBuffer.Heap buf = compress(bin);
          res.setContentLength((int) buf.length());
          res.setHeader("Content-Encoding", "gzip");
          buf.writeTo(dst, null);
        } else if (gzip) {
          res.setHeader("Content-Encoding", "gzip");
          dst = new GZIPOutputStream(dst);
          bin.writeTo(dst);
        } else {
          if (0 <= len && len < Integer.MAX_VALUE) {
            res.setContentLength((int) len);
          } else if (0 <= len) {
            res.setHeader("Content-Length", Long.toString(len));
          }
          bin.writeTo(dst);
        }
      } finally {
        dst.close();
      }
    } finally {
      bin.close();
    }
  }

  private RestView<RestResource> view(
      RestCollection<RestResource, RestResource> rc,
      String method, List<String> path) throws ResourceNotFoundException,
      InvalidMethodException, AmbiguousViewException {
    DynamicMap<RestView<RestResource>> views = rc.views();
    final String projection = path.isEmpty() ? "/" : path.remove(0);
    if (!path.isEmpty()) {
      // If there are path components still remaining after this projection
      // is chosen, look for the projection based upon GET as the method as
      // the client thinks it is a nested collection.
      method = "GET";
    }

    List<String> p = splitProjection(projection);
    if (p.size() == 2) {
      RestView<RestResource> view =
          views.get(p.get(0), method + "." + p.get(1));
      if (view != null) {
        return view;
      }
      throw new ResourceNotFoundException(projection);
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
      throw new ResourceNotFoundException(projection);
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
      throws AuthException {
    CurrentUser user = globals.currentUser.get();
    if (isStateChange(req)) {
      if (user instanceof AnonymousUser) {
        throw new AuthException("Authentication required");
      } else if (!globals.webSession.get().isAccessPathOk(AccessPath.REST_API)) {
        throw new AuthException("Invalid authentication method");
      }
    }
    user.setAccessPath(AccessPath.REST_API);
  }

  private static boolean isStateChange(HttpServletRequest req) {
    String method = req.getMethod();
    return !("GET".equals(method) || "HEAD".equals(method));
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
      replyError(res, SC_INTERNAL_SERVER_ERROR, "Internal server error");
    }
  }

  static void replyError(HttpServletResponse res, int statusCode, String msg)
      throws IOException {
    res.setStatus(statusCode);
    replyText(null, res, msg);
  }

  static void replyText(@Nullable HttpServletRequest req,
      HttpServletResponse res, String text) throws IOException {
    if (!text.endsWith("\n")) {
      text += "\n";
    }
    replyBinaryResult(req, res,
        BinaryResult.create(text).setContentType("text/plain"));
  }

  private static boolean acceptsJson(HttpServletRequest req) {
    return req != null && isType(JSON_TYPE, req.getHeader("Accept"));
  }

  private static boolean acceptsGzip(HttpServletRequest req) {
    return req != null && RPCServletUtils.acceptsGzipEncoding(req);
  }

  private static boolean isType(String expect, String given) {
    if (given == null) {
      return false;
    } else if (expect.equals(given)) {
      return true;
    } else if (given.startsWith(expect + ",")) {
      return true;
    }
    for (String p : given.split("[ ,;][ ,;]*")) {
      if (expect.equals(p)) {
        return true;
      }
    }
    return false;
  }

  private static TemporaryBuffer.Heap compress(BinaryResult bin)
      throws IOException {
    TemporaryBuffer.Heap buf = heap(20 << 20);
    GZIPOutputStream gz = new GZIPOutputStream(buf);
    bin.writeTo(gz);
    gz.finish();
    gz.flush();
    return buf;
  }

  private static Heap heap(int max) {
    return new TemporaryBuffer.Heap(max);
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
