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

package com.google.gerrit.httpd.auth.github;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OAuthProtocol {
  private static final String ME_SEPARATOR = ",";
  private static final Logger LOG = LoggerFactory
      .getLogger(OAuthProtocol.class);

  private final GitHubOAuthConfig config;
  private final HttpClient http;
  private final Gson gson;
  private final String state;

  @Inject
  public OAuthProtocol(GitHubOAuthConfig config,
      HttpClient http, Gson gson) {
    this.config = config;
    this.http = http;
    this.gson = gson;
    this.state = generateRandomState();
  }

  private String generateRandomState() {
    byte[] randomState = new byte[32];
    new SecureRandom().nextBytes(randomState);
    return Base64.encodeBase64URLSafeString(randomState);
  }

  public void loginPhase1(HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    LOG.debug("Initiating GitHub Login for ClientId=" + config.gitHubClientId);
    response.sendRedirect(String.format(
        "%s?client_id=%s&redirect_uri=%s&state=%s%s", config.gitHubOAuthUrl,
        config.gitHubClientId, getURLEncoded(config.oAuthFinalRedirectUrl),
        me(), getURLEncoded(request.getRequestURI().toString())));
  }

  public static boolean isOAuthFinal(HttpServletRequest request) {
    return Strings.emptyToNull(request.getParameter("code")) != null;
  }

  public String me() {
    return "" + state + ME_SEPARATOR;
  }

  public static boolean isOAuthLogin(HttpServletRequest request) {
    return request.getRequestURI().indexOf(GitHubOAuthConfig.OAUTH_LOGIN) >= 0;
  }

  public boolean isOAuthRequest(HttpServletRequest httpRequest) {
    return OAuthProtocol.isOAuthLogin(httpRequest)
        || OAuthProtocol.isOAuthFinal(httpRequest);
  }

  public String loginPhase2(HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    HttpPost post = null;

    post = new HttpPost(config.gitHubOAuthAccessTokenUrl);
    post.setHeader("Accept", "application/json");
    List<NameValuePair> nvps = new ArrayList<NameValuePair>();
    nvps.add(new BasicNameValuePair("client_id", config.gitHubClientId));
    nvps.add(new BasicNameValuePair("client_secret", config.gitHubClientSecret));
    nvps.add(new BasicNameValuePair("code", request.getParameter("code")));
    post.setEntity(new UrlEncodedFormEntity(nvps, Charsets.UTF_8));

    try {
      HttpResponse postResponse = http.execute(post);
      if (postResponse.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
        LOG.error("POST " + config.gitHubOAuthAccessTokenUrl
            + " request for access token failed with status "
            + postResponse.getStatusLine());
        response.sendError(HttpURLConnection.HTTP_UNAUTHORIZED,
            "Request for access token not authorised");
        EntityUtils.consume(postResponse.getEntity());
        return null;
      }

      JsonObject accessTokenJson = getAccessTokenJson(postResponse);
      if (accessTokenJson != null) {
        return getAccessToken(accessTokenJson);
      } else {
        LOG.error("Invalid response received from GitHub: {}",
            postResponse.toString());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return null;
      }
    } catch (IOException e) {
      LOG.error("POST " + config.gitHubOAuthAccessTokenUrl
          + " request for access token failed", e);
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          "Request for access token not authorised");
      return null;
    }
  }

  private String getAccessToken(JsonElement accessTokenJson) {
    JsonElement accessTokenString =
        accessTokenJson.getAsJsonObject().get("access_token");
    if(accessTokenString != null) {
      return accessTokenString.getAsString();
    } else {
      return null;
    }
  }

  private JsonObject getAccessTokenJson(HttpResponse postResponse)
      throws UnsupportedEncodingException, IOException {
    JsonElement accessTokenJson =
        gson.fromJson(new InputStreamReader(postResponse.getEntity()
            .getContent(), "UTF-8"), JsonElement.class);
    if(accessTokenJson.isJsonObject()) {
      return accessTokenJson.getAsJsonObject();
    } else {
      return null;
    }
  }

  private static String getURLEncoded(String url) {
    try {
      return URLEncoder.encode(url, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // UTF-8 is hardcoded, cannot fail
      return null;
    }
  }

  public String getTargetUrl(ServletRequest request) {
    String requestState = state(request);
    int meEnd = requestState.indexOf(ME_SEPARATOR);
    if (meEnd >= 0 && requestState.subSequence(0, meEnd).equals(state)) {
      return requestState.substring(meEnd + 1);
    } else {
      LOG.warn("Illegal request state '" + requestState + "' on OAuthProtocol "
          + this);
      return null;
    }
  }

  private static String state(ServletRequest request) {
    return Strings.nullToEmpty(request.getParameter("state"));
  }

  @Override
  public String toString() {
    return "OAuthProtocol/" + state;
  }
}
