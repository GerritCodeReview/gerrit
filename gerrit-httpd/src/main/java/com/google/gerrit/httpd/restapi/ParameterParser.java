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

import static com.google.gerrit.httpd.restapi.RestApiServlet.ALLOWED_CORS_METHODS;
import static com.google.gerrit.httpd.restapi.RestApiServlet.XD_AUTHORIZATION;
import static com.google.gerrit.httpd.restapi.RestApiServlet.XD_CONTENT_TYPE;
import static com.google.gerrit.httpd.restapi.RestApiServlet.XD_METHOD;
import static com.google.gerrit.httpd.restapi.RestApiServlet.replyBinaryResult;
import static com.google.gerrit.httpd.restapi.RestApiServlet.replyError;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.kohsuke.args4j.CmdLineException;

public class ParameterParser {
  private static final ImmutableSet<String> RESERVED_KEYS =
      ImmutableSet.of("pp", "prettyPrint", "strict", "callback", "alt", "fields");

  @AutoValue
  public abstract static class QueryParams {
    static final String I = QueryParams.class.getName();

    static QueryParams create(
        String accessToken,
        String xdMethod,
        String xdContentType,
        ImmutableListMultimap<String, String> config,
        ImmutableListMultimap<String, String> params) {
      return new AutoValue_ParameterParser_QueryParams(
          accessToken, xdMethod, xdContentType, config, params);
    }

    public abstract String accessToken();

    abstract String xdMethod();

    abstract String xdContentType();

    abstract ImmutableListMultimap<String, String> config();

    abstract ImmutableListMultimap<String, String> params();

    boolean hasXdOverride() {
      return xdMethod() != null || xdContentType() != null;
    }
  }

  public static QueryParams getQueryParams(HttpServletRequest req) throws BadRequestException {
    QueryParams qp = (QueryParams) req.getAttribute(QueryParams.I);
    if (qp != null) {
      return qp;
    }

    String accessToken = null;
    String xdMethod = null;
    String xdContentType = null;
    ListMultimap<String, String> config = MultimapBuilder.hashKeys(4).arrayListValues().build();
    ListMultimap<String, String> params = MultimapBuilder.hashKeys().arrayListValues().build();

    String queryString = req.getQueryString();
    if (!Strings.isNullOrEmpty(queryString)) {
      for (String kvPair : Splitter.on('&').split(queryString)) {
        Iterator<String> i = Splitter.on('=').limit(2).split(kvPair).iterator();
        String key = Url.decode(i.next());
        String val = i.hasNext() ? Url.decode(i.next()) : "";

        if (XD_AUTHORIZATION.equals(key)) {
          if (accessToken != null) {
            throw new BadRequestException("duplicate " + XD_AUTHORIZATION);
          }
          accessToken = val;
        } else if (XD_METHOD.equals(key)) {
          if (xdMethod != null) {
            throw new BadRequestException("duplicate " + XD_METHOD);
          } else if (!ALLOWED_CORS_METHODS.contains(val)) {
            throw new BadRequestException("invalid " + XD_METHOD);
          }
          xdMethod = val;
        } else if (XD_CONTENT_TYPE.equals(key)) {
          if (xdContentType != null) {
            throw new BadRequestException("duplicate " + XD_CONTENT_TYPE);
          }
          xdContentType = val;
        } else if (RESERVED_KEYS.contains(key)) {
          config.put(key, val);
        } else {
          params.put(key, val);
        }
      }
    }

    qp =
        QueryParams.create(
            accessToken,
            xdMethod,
            xdContentType,
            ImmutableListMultimap.copyOf(config),
            ImmutableListMultimap.copyOf(params));
    req.setAttribute(QueryParams.I, qp);
    return qp;
  }

  private final CmdLineParser.Factory parserFactory;
  private final Injector injector;
  private final DynamicMap<DynamicOptions.DynamicBean> dynamicBeans;

  @Inject
  ParameterParser(
      CmdLineParser.Factory pf,
      Injector injector,
      DynamicMap<DynamicOptions.DynamicBean> dynamicBeans) {
    this.parserFactory = pf;
    this.injector = injector;
    this.dynamicBeans = dynamicBeans;
  }

  <T> boolean parse(
      T param, ListMultimap<String, String> in, HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    CmdLineParser clp = parserFactory.create(param);
    DynamicOptions pluginOptions = new DynamicOptions(param, injector, dynamicBeans);
    pluginOptions.parseDynamicBeans(clp);
    pluginOptions.setDynamicBeans();
    pluginOptions.onBeanParseStart();
    try {
      clp.parseOptionMap(in);
    } catch (CmdLineException | NumberFormatException e) {
      if (!clp.wasHelpRequestedByOption()) {
        replyError(req, res, SC_BAD_REQUEST, e.getMessage(), e);
        return false;
      }
    }

    if (clp.wasHelpRequestedByOption()) {
      StringWriter msg = new StringWriter();
      clp.printQueryStringUsage(req.getRequestURI(), msg);
      msg.write('\n');
      msg.write('\n');
      clp.printUsage(msg, null);
      msg.write('\n');
      CacheHeaders.setNotCacheable(res);
      replyBinaryResult(req, res, BinaryResult.create(msg.toString()).setContentType("text/plain"));
      return false;
    }
    pluginOptions.onBeanParseEnd();

    return true;
  }

  private static Set<String> query(HttpServletRequest req) {
    Set<String> params = new HashSet<>();
    if (!Strings.isNullOrEmpty(req.getQueryString())) {
      for (String kvPair : Splitter.on('&').split(req.getQueryString())) {
        params.add(Iterables.getFirst(Splitter.on('=').limit(2).split(kvPair), null));
      }
    }
    return params;
  }

  /**
   * Convert a standard URL encoded form input into a parsed JSON tree.
   *
   * <p>Given an input such as:
   *
   * <pre>
   * message=Does+not+compile.&labels.Verified=-1
   * </pre>
   *
   * which is easily created using the curl command line tool:
   *
   * <pre>
   * curl --data 'message=Does not compile.' --data labels.Verified=-1
   * </pre>
   *
   * converts to a JSON object structure that is normally expected:
   *
   * <pre>
   * {
   *   "message": "Does not compile.",
   *   "labels": {
   *     "Verified": "-1"
   *   }
   * }
   * </pre>
   *
   * This input can then be further processed into the Java input type expected by a view using
   * Gson. Here we rely on Gson to perform implicit conversion of a string {@code "-1"} to a number
   * type when the Java input type expects a number.
   *
   * <p>Conversion assumes any field name that does not contain {@code "."} will be a property of
   * the top level input object. Any field with a dot will use the first segment as the top level
   * property name naming an object, and the rest of the field name as a property in the nested
   * object.
   *
   * @param req request to parse form input from and create JSON tree.
   * @return the converted JSON object tree.
   * @throws BadRequestException the request cannot be cast, as there are conflicting definitions
   *     for a nested object.
   */
  static JsonObject formToJson(HttpServletRequest req) throws BadRequestException {
    Map<String, String[]> map = req.getParameterMap();
    return formToJson(map, query(req));
  }

  @VisibleForTesting
  static JsonObject formToJson(Map<String, String[]> map, Set<String> query)
      throws BadRequestException {
    JsonObject inputObject = new JsonObject();
    for (Map.Entry<String, String[]> ent : map.entrySet()) {
      String key = ent.getKey();
      String[] values = ent.getValue();

      if (query.contains(key) || values.length == 0) {
        // Disallow processing query parameters as input body fields.
        // Implementations of views should avoid duplicate naming.
        continue;
      }

      JsonObject obj = inputObject;
      int dot = key.indexOf('.');
      if (0 <= dot) {
        String property = key.substring(0, dot);
        JsonElement e = inputObject.get(property);
        if (e == null) {
          obj = new JsonObject();
          inputObject.add(property, obj);
        } else if (e.isJsonObject()) {
          obj = e.getAsJsonObject();
        } else {
          throw new BadRequestException(String.format("key %s conflicts with %s", key, property));
        }
        key = key.substring(dot + 1);
      }

      if (obj.get(key) != null) {
        // This error should never happen. If all form values are handled
        // together in a single pass properties are set only once. Setting
        // again indicates something has gone very wrong.
        throw new BadRequestException("invalid form input, use JSON instead");
      } else if (values.length == 1) {
        obj.addProperty(key, values[0]);
      } else {
        JsonArray list = new JsonArray();
        for (String v : values) {
          list.add(new JsonPrimitive(v));
        }
        obj.add(key, list);
      }
    }
    return inputObject;
  }
}
