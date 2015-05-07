// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.openid;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.auth.openid.OpenIdUrls;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.LoginUrlToken;
import com.google.gerrit.httpd.template.SiteHeaderFooter;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Handles OpenID based login flow. */
@SuppressWarnings("serial")
@Singleton
class LoginForm extends HttpServlet {
  private static final Logger log = LoggerFactory.getLogger(LoginForm.class);
  private static final ImmutableMap<String, String> ALL_PROVIDERS = ImmutableMap.of(
      "launchpad", OpenIdUrls.URL_LAUNCHPAD,
      "yahoo", OpenIdUrls.URL_YAHOO);

  private final ImmutableSet<String> suggestProviders;
  private final Provider<String> urlProvider;
  private final Provider<OAuthSessionOverOpenID> oauthSessionProvider;
  private final OpenIdServiceImpl impl;
  private final int maxRedirectUrlLength;
  private final String ssoUrl;
  private final SiteHeaderFooter header;
  private final Provider<CurrentUser> currentUserProvider;
  private final DynamicMap<OAuthServiceProvider> oauthServiceProviders;

  @Inject
  LoginForm(
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      @GerritServerConfig Config config,
      AuthConfig authConfig,
      OpenIdServiceImpl impl,
      SiteHeaderFooter header,
      Provider<OAuthSessionOverOpenID> oauthSessionProvider,
      Provider<CurrentUser> currentUserProvider,
      DynamicMap<OAuthServiceProvider> oauthServiceProviders) {
    this.urlProvider = urlProvider;
    this.impl = impl;
    this.header = header;
    this.maxRedirectUrlLength = config.getInt(
        "openid", "maxRedirectUrlLength",
        10);
    this.oauthSessionProvider = oauthSessionProvider;
    this.currentUserProvider = currentUserProvider;
    this.oauthServiceProviders = oauthServiceProviders;

    if (urlProvider == null || Strings.isNullOrEmpty(urlProvider.get())) {
      log.error("gerrit.canonicalWebUrl must be set in gerrit.config");
    }

    if (authConfig.getAuthType() == AuthType.OPENID_SSO) {
      suggestProviders = ImmutableSet.of();
      ssoUrl = authConfig.getOpenIdSsoUrl();
    } else {
      Set<String> providers = Sets.newHashSet();
      for (Map.Entry<String, String> e : ALL_PROVIDERS.entrySet()) {
        if (impl.isAllowedOpenID(e.getValue())) {
          providers.add(e.getKey());
        }
      }
      suggestProviders = ImmutableSet.copyOf(providers);
      ssoUrl = null;
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (ssoUrl != null) {
      String token = LoginUrlToken.getToken(req);
      SignInMode mode;
      if (PageLinks.REGISTER.equals(token)) {
        mode = SignInMode.REGISTER;
        token = PageLinks.MINE;
      } else {
        mode = SignInMode.SIGN_IN;
      }
      discover(req, res, false, ssoUrl, false, token, mode);
    } else {
      String id = Strings.nullToEmpty(req.getParameter("id")).trim();
      if (!id.isEmpty()) {
        doPost(req, res);
      } else {
        boolean link = req.getParameter("link") != null;
        sendForm(req, res, link, null);
      }
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    boolean link = req.getParameter("link") != null;
    String id = Strings.nullToEmpty(req.getParameter("id")).trim();
    if (id.isEmpty()) {
      sendForm(req, res, link, null);
      return;
    }
    if (!id.startsWith("http://") && !id.startsWith("https://")) {
      id = "http://" + id;
    }
    if ((ssoUrl != null && !ssoUrl.equals(id)) || !impl.isAllowedOpenID(id)) {
      sendForm(req, res, link, "OpenID provider not permitted by site policy.");
      return;
    }

    boolean remember = "1".equals(req.getParameter("rememberme"));
    String token = LoginUrlToken.getToken(req);
    SignInMode mode;
    if (link) {
      mode = SignInMode.LINK_IDENTIY;
    } else if (PageLinks.REGISTER.equals(token)) {
      mode = SignInMode.REGISTER;
      token = PageLinks.MINE;
    } else {
      mode = SignInMode.SIGN_IN;
    }

    OAuthServiceProvider oauthProvider = lookupOAuthServiceProvider(id);

    if (oauthProvider == null) {
      discover(req, res, link, id, remember, token, mode);
    } else {
      OAuthSessionOverOpenID oauthSession = oauthSessionProvider.get();
      if (!currentUserProvider.get().isIdentifiedUser()
          && oauthSession.isLoggedIn()) {
        oauthSession.logout();
      }
      if ((isGerritLogin(req)
          || oauthSession.isOAuthFinal(req))) {
        oauthSession.setServiceProvider(oauthProvider);
        oauthSession.setLinkMode(link);
        oauthSession.login(req, res, oauthProvider);
      }
    }
  }

  private void discover(HttpServletRequest req, HttpServletResponse res,
      boolean link, String id, boolean remember, String token, SignInMode mode)
      throws IOException {
    if (ssoUrl != null) {
      remember = false;
    }

    DiscoveryResult r = impl.discover(req, id, mode, remember, token);
    switch (r.status) {
      case VALID:
        redirect(r, res);
        break;

      case NO_PROVIDER:
        sendForm(req, res, link,
            "Provider is not supported, or was incorrectly entered.");
        break;

      case ERROR:
        sendForm(req, res, link, "Unable to connect with OpenID provider.");
        break;
    }
  }

  private void redirect(DiscoveryResult r, HttpServletResponse res)
      throws IOException {
    StringBuilder url = new StringBuilder();
    url.append(r.providerUrl);
    if (r.providerArgs != null && !r.providerArgs.isEmpty()) {
      boolean first = true;
      for(Map.Entry<String, String> arg : r.providerArgs.entrySet()) {
        if (first) {
          url.append('?');
          first = false;
        } else {
          url.append('&');
        }
        url.append(Url.encode(arg.getKey()))
           .append('=')
           .append(Url.encode(arg.getValue()));
      }
    }
    if (url.length() <= maxRedirectUrlLength) {
      res.sendRedirect(url.toString());
      return;
    }

    Document doc = HtmlDomUtil.parseFile(LoginForm.class, "RedirectForm.html");
    Element form = HtmlDomUtil.find(doc, "redirect_form");
    form.setAttribute("action", r.providerUrl);
    if (r.providerArgs != null && !r.providerArgs.isEmpty()) {
      for (Map.Entry<String, String> arg : r.providerArgs.entrySet()) {
        Element in = doc.createElement("input");
        in.setAttribute("type", "hidden");
        in.setAttribute("name", arg.getKey());
        in.setAttribute("value", arg.getValue());
        form.appendChild(in);
      }
    }
    sendHtml(res, doc);
  }

  private void sendForm(HttpServletRequest req, HttpServletResponse res,
      boolean link, @Nullable String errorMessage) throws IOException {
    String self = req.getRequestURI();
    String cancel = MoreObjects.firstNonNull(
        urlProvider != null ? urlProvider.get() : "/", "/");
    cancel += LoginUrlToken.getToken(req);

    Document doc = header.parse(LoginForm.class, "LoginForm.html");
    HtmlDomUtil.find(doc, "hostName").setTextContent(req.getServerName());
    HtmlDomUtil.find(doc, "login_form").setAttribute("action", self);
    HtmlDomUtil.find(doc, "cancel_link").setAttribute("href", cancel);

    if (!link || ssoUrl != null) {
      Element input = HtmlDomUtil.find(doc, "f_link");
      input.getParentNode().removeChild(input);
    }

    String last = getLastId(req);
    if (last != null) {
      HtmlDomUtil.find(doc, "f_openid").setAttribute("value", last);
    }

    Element emsg = HtmlDomUtil.find(doc, "error_message");
    if (Strings.isNullOrEmpty(errorMessage)) {
      emsg.getParentNode().removeChild(emsg);
    } else {
      emsg.setTextContent(errorMessage);
    }

    for (String name : ALL_PROVIDERS.keySet()) {
      Element div = HtmlDomUtil.find(doc, "provider_" + name);
      if (div == null) {
        continue;
      }
      if (!suggestProviders.contains(name)) {
        div.getParentNode().removeChild(div);
        continue;
      }
      Element a = HtmlDomUtil.find(div, "id_" + name);
      if (a == null) {
        div.getParentNode().removeChild(div);
        continue;
      }
      StringBuilder u = new StringBuilder();
      u.append(self).append(a.getAttribute("href"));
      if (link) {
        u.append("&link");
      }
      a.setAttribute("href", u.toString());
    }

    // OAuth: Add plugin based providers
    Element providers = HtmlDomUtil.find(doc, "providers");
    Set<String> plugins = oauthServiceProviders.plugins();
    for (String pluginName : plugins) {
      Map<String, Provider<OAuthServiceProvider>> m =
          oauthServiceProviders.byPlugin(pluginName);
        for (Map.Entry<String, Provider<OAuthServiceProvider>> e
            : m.entrySet()) {
          addProvider(providers, link, pluginName, e.getKey(),
              e.getValue().get().getName());
        }
    }

    sendHtml(res, doc);
  }

  private void sendHtml(HttpServletResponse res, Document doc)
      throws IOException {
    byte[] bin = HtmlDomUtil.toUTF8(doc);
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setContentType("text/html");
    res.setCharacterEncoding("UTF-8");
    res.setContentLength(bin.length);
    ServletOutputStream out = res.getOutputStream();
    try {
      out.write(bin);
    } finally {
      out.close();
    }
  }

  private static void addProvider(Element form, boolean link,
      String pluginName, String id, String serviceName) {
    Element div = form.getOwnerDocument().createElement("div");
    div.setAttribute("id", id);
    Element hyperlink = form.getOwnerDocument().createElement("a");
    StringBuilder u = new StringBuilder(String.format("?id=%s_%s",
        pluginName, id));
    if (link) {
      u.append("&link");
    }
    hyperlink.setAttribute("href", u.toString());

    hyperlink.setTextContent(serviceName +
        " (" + pluginName + " plugin)");
    div.appendChild(hyperlink);
    form.appendChild(div);
  }

  private OAuthServiceProvider lookupOAuthServiceProvider(String providerId) {
    if (providerId.startsWith("http://")) {
      providerId = providerId.substring("http://".length());
    }
    Set<String> plugins = oauthServiceProviders.plugins();
    for (String pluginName : plugins) {
      Map<String, Provider<OAuthServiceProvider>> m =
          oauthServiceProviders.byPlugin(pluginName);
        for (Map.Entry<String, Provider<OAuthServiceProvider>> e
            : m.entrySet()) {
          if (providerId.equals(
              String.format("%s_%s", pluginName, e.getKey()))) {
            return e.getValue().get();
          }
        }
    }
    return null;
  }

  private static String getLastId(HttpServletRequest req) {
    Cookie[] cookies = req.getCookies();
    if (cookies != null) {
      for (Cookie c : cookies) {
        if (OpenIdUrls.LASTID_COOKIE.equals(c.getName())) {
          return c.getValue();
        }
      }
    }
    return null;
  }

  private static boolean isGerritLogin(HttpServletRequest request) {
    return request.getRequestURI().indexOf(
        OAuthSessionOverOpenID.GERRIT_LOGIN) >= 0;
  }
}
