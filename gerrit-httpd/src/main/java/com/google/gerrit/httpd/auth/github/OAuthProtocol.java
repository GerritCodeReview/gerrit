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
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.servlet.SessionScoped;

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

@SessionScoped
public class OAuthProtocol {

  private static final String ME_SEPARATOR = ",";
  private static final Logger LOG = LoggerFactory
      .getLogger(OAuthProtocol.class);

  private final GitHubOAuthConfig config;
  private final HttpClient http;
  private final Gson gson;
  private final String state;

  public static class AccessToken {
    public String access_token;

    public AccessToken() {
      this("");
    }

    public AccessToken(String token) {
      this.access_token = token;
    }

    @Override
    public String toString() {
      return access_token;
    }

    @Override
    public int hashCode() {
      return access_token.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return Objects.equal(this, obj);
    }
  }

  @Inject
  public OAuthProtocol(GitHubOAuthConfig config,
      Provider<HttpClient> httpClientProvider, Gson gson) {
    this.config = config;
    this.http = httpClientProvider.get();
    this.gson = gson;
    this.state = generateRandomState();
  }

  private String generateRandomState() {
    byte[] randomState = new byte[32];
    new SecureRandom(new SecureRandom().generateSeed(32))
        .nextBytes(randomState);
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

  public AccessToken loginPhase2(HttpServletRequest request,
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

      AccessToken token =
          gson.fromJson(new InputStreamReader(postResponse.getEntity()
              .getContent(), "UTF-8"), AccessToken.class);
      return token;
    } catch (IOException e) {
      LOG.error("POST " + config.gitHubOAuthAccessTokenUrl
          + " request for access token failed", e);
      response.sendError(HttpURLConnection.HTTP_UNAUTHORIZED,
          "Request for access token not authorised");
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
      return state(request).substring(meEnd + 1);
    } else {
      LOG.warn("Illegal request state '" + requestState + "' on OAuthProtocol "
          + this);
      return null;
    }
  }

  private static String state(ServletRequest request) {
    return Strings.nullToEmpty(request.getParameter("state"));
  }

  public String getTargetOAuthFinal(HttpServletRequest httpRequest) {
    String targetUrl = getTargetUrl(httpRequest);
    if (targetUrl != null) {
      String state = getURLEncoded(httpRequest.getParameter("state"));
      return targetUrl + (targetUrl.indexOf('?') < 0 ? '?' : '&') + "state="
          + state;
    } else {
      return null;
    }
  }

  @Override
  public String toString() {
    return "OAuthProtocol/" + state;
  }

}
