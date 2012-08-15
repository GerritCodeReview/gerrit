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

package com.google.gerrit.httpd;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.gerrit.httpd.RestTokenVerifier.InvalidTokenException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.Map;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

public abstract class TokenVerifiedRestApiServlet extends RestApiServlet {
  private static final long serialVersionUID = 1L;
  private static final String FORM_ENCODED = "application/x-www-form-urlencoded";
  private static final String UTF_8 = "UTF-8";
  private static final String AUTHKEY_NAME = "_authkey";
  private static final String AUTHKEY_HEADER = "X-authkey";

  private final Gson gson;
  private final Provider<CurrentUser> userProvider;
  private final RestTokenVerifier verifier;

  @Inject
  protected TokenVerifiedRestApiServlet(Provider<CurrentUser> userProvider,
      RestTokenVerifier verifier) {
    super(userProvider);
    this.gson = OutputFormat.JSON_COMPACT.newGson();
    this.userProvider = userProvider;
    this.verifier = verifier;
  }

  /**
   * Process the (possibly state changing) request.
   *
   * @param req incoming HTTP request.
   * @param res outgoing response.
   * @param requestData JSON object representing the HTTP request parameters.
   *        Null if the request body was not supplied in JSON format.
   * @throws IOException
   * @throws ServletException
   */
  protected abstract void doRequest(HttpServletRequest req,
      HttpServletResponse res,
      @Nullable JsonObject requestData) throws IOException, ServletException;

  @Override
  protected final void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    CurrentUser user = userProvider.get();
    if (!(user instanceof IdentifiedUser)) {
      sendError(res, SC_UNAUTHORIZED, "API requires authentication");
      return;
    }

    TokenInfo info = new TokenInfo();
    info._authkey = verifier.sign(
        ((IdentifiedUser) user).getAccountId(),
        computeUrl(req));

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    String type;
    buf.write(JSON_MAGIC);
    if (acceptsJson(req)) {
      type = JSON_TYPE;
      buf.write(gson.toJson(info).getBytes(UTF_8));
    } else {
      type = FORM_ENCODED;
      buf.write(String.format("%s=%s",
          AUTHKEY_NAME,
          URLEncoder.encode(info._authkey, UTF_8)).getBytes(UTF_8));
    }

    res.setContentType(type);
    res.setCharacterEncoding(UTF_8);
    res.setHeader("Content-Disposition", "attachment");
    send(req, res, buf.toByteArray());
  }

  @Override
  protected final void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException, ServletException {
    CurrentUser user = userProvider.get();
    if (!(user instanceof IdentifiedUser)) {
      sendError(res, SC_UNAUTHORIZED, "API requires authentication");
      return;
    }

    ParsedBody body;
    if (JSON_TYPE.equals(req.getContentType())) {
      body = parseJson(req, res);
    } else if (FORM_ENCODED.equals(req.getContentType())) {
      body = parseForm(req, res);
    } else {
      sendError(res, SC_BAD_REQUEST, String.format(
          "Expected Content-Type: %s or %s",
          JSON_TYPE, FORM_ENCODED));
      return;
    }

    if (body == null) {
      return;
    }

    if (Strings.isNullOrEmpty(body._authkey)) {
      String h = req.getHeader(AUTHKEY_HEADER);
      if (Strings.isNullOrEmpty(h)) {
        sendError(res, SC_BAD_REQUEST, String.format(
            "Expected %s in request body or %s in HTTP headers",
            AUTHKEY_NAME, AUTHKEY_HEADER));
        return;
      }
      body._authkey = URLDecoder.decode(h, UTF_8);
    }

    try {
      verifier.verify(
          ((IdentifiedUser) user).getAccountId(),
          computeUrl(req),
          body._authkey);
    } catch (InvalidTokenException err) {
      sendError(res, SC_BAD_REQUEST,
          String.format("Invalid or expired %s", AUTHKEY_NAME));
      return;
    }

    doRequest(body.req, res, body.json);
  }

  private static ParsedBody parseJson(HttpServletRequest req,
      HttpServletResponse res) throws IOException {
    try {
      JsonElement element = new JsonParser().parse(req.getReader());
      if (!element.isJsonObject()) {
        sendError(res, SC_BAD_REQUEST, "Expected JSON object in request body");
        return null;
      }

      ParsedBody body = new ParsedBody();
      body.req = req;
      body.json = (JsonObject) element;
      JsonElement authKey = body.json.remove(AUTHKEY_NAME);
      if (authKey != null
          && authKey.isJsonPrimitive()
          && authKey.getAsJsonPrimitive().isString()) {
        body._authkey = authKey.getAsString();
      }
      return body;
    } catch (JsonParseException e) {
      sendError(res, SC_BAD_REQUEST, "Invalid JSON object in request body");
      return null;
    }
  }

  private static ParsedBody parseForm(HttpServletRequest req,
      HttpServletResponse res) throws IOException {
    ParsedBody body = new ParsedBody();
    body.req = new WrappedRequest(req);
    body._authkey = req.getParameter(AUTHKEY_NAME);
    return body;
  }

  private static String computeUrl(HttpServletRequest req) {
    StringBuffer url = req.getRequestURL();
    String qs = req.getQueryString();
    if (!Strings.isNullOrEmpty(qs)) {
      url.append('?').append(qs);
    }
    return url.toString();
  }

  private static class TokenInfo {
    String _authkey;
  }

  private static class ParsedBody {
    HttpServletRequest req;
    String _authkey;
    JsonObject json;
  }

  private static class WrappedRequest extends HttpServletRequestWrapper {
    @SuppressWarnings("rawtypes")
    private Map parameters;

    WrappedRequest(HttpServletRequest req) {
      super(req);
    }

    @Override
    public String getParameter(String name) {
      if (AUTHKEY_NAME.equals(name)) {
        return null;
      }
      return super.getParameter(name);
    }

    @Override
    public String[] getParameterValues(String name) {
      if (AUTHKEY_NAME.equals(name)) {
        return null;
      }
      return super.getParameterValues(name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Map getParameterMap() {
      Map m = parameters;
      if (m == null) {
        m = super.getParameterMap();
        if (m.containsKey(AUTHKEY_NAME)) {
          m = Maps.newHashMap(m);
          m.remove(AUTHKEY_NAME);
        }
        parameters = m;
      }
      return m;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Enumeration getParameterNames() {
      return Iterators.asEnumeration(getParameterMap().keySet().iterator());
    }
  }
}

