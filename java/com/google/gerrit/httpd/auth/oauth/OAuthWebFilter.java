// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.oauth;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.LoginUrlToken;
import com.google.gerrit.httpd.template.SiteHeaderFooter;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Singleton
/* OAuth web filter uses active OAuth session to perform OAuth requests */
class OAuthWebFilter implements Filter {
  static final String GERRIT_LOGIN = "/login";

  private final Provider<String> urlProvider;
  private final Provider<OAuthSession> oauthSessionProvider;
  private final DynamicMap<OAuthServiceProvider> oauthServiceProviders;
  private final SiteHeaderFooter header;
  private OAuthServiceProvider ssoProvider;

  @Inject
  OAuthWebFilter(
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      DynamicMap<OAuthServiceProvider> oauthServiceProviders,
      Provider<OAuthSession> oauthSessionProvider,
      SiteHeaderFooter header) {
    this.urlProvider = urlProvider;
    this.oauthServiceProviders = oauthServiceProviders;
    this.oauthSessionProvider = oauthSessionProvider;
    this.header = header;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    pickSSOServiceProvider();
  }

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    OAuthSession oauthSession = oauthSessionProvider.get();
    if (request.getParameter("link") != null) {
      oauthSession.setLinkMode(true);
      oauthSession.setServiceProvider(null);
    }

    String provider = httpRequest.getParameter("provider");
    OAuthServiceProvider service =
        ssoProvider == null ? oauthSession.getServiceProvider() : ssoProvider;

    if (isGerritLogin(httpRequest) || oauthSession.isOAuthFinal(httpRequest)) {
      if (service == null && Strings.isNullOrEmpty(provider)) {
        selectProvider(httpRequest, httpResponse, null);
        return;
      }
      if (service == null) {
        service = findService(provider);
      }
      oauthSession.setServiceProvider(service);
      oauthSession.login(httpRequest, httpResponse, service);
    } else {
      chain.doFilter(httpRequest, response);
    }
  }

  private OAuthServiceProvider findService(String providerId) throws ServletException {
    Set<String> plugins = oauthServiceProviders.plugins();
    for (String pluginName : plugins) {
      Map<String, Provider<OAuthServiceProvider>> m = oauthServiceProviders.byPlugin(pluginName);
      for (Map.Entry<String, Provider<OAuthServiceProvider>> e : m.entrySet()) {
        if (providerId.equals(String.format("%s_%s", pluginName, e.getKey()))) {
          return e.getValue().get();
        }
      }
    }
    throw new ServletException("No provider found for: " + providerId);
  }

  private void selectProvider(
      HttpServletRequest req, HttpServletResponse res, @Nullable String errorMessage)
      throws IOException {
    String self = req.getRequestURI();
    String cancel = MoreObjects.firstNonNull(urlProvider != null ? urlProvider.get() : "/", "/");
    cancel += LoginUrlToken.getToken(req);

    Document doc = header.parse(OAuthWebFilter.class, "LoginForm.html");
    HtmlDomUtil.find(doc, "hostName").setTextContent(req.getServerName());
    HtmlDomUtil.find(doc, "login_form").setAttribute("action", self);
    HtmlDomUtil.find(doc, "cancel_link").setAttribute("href", cancel);

    Element emsg = HtmlDomUtil.find(doc, "error_message");
    if (Strings.isNullOrEmpty(errorMessage)) {
      emsg.getParentNode().removeChild(emsg);
    } else {
      emsg.setTextContent(errorMessage);
    }

    Element providers = HtmlDomUtil.find(doc, "providers");

    Set<String> plugins = oauthServiceProviders.plugins();
    for (String pluginName : plugins) {
      Map<String, Provider<OAuthServiceProvider>> m = oauthServiceProviders.byPlugin(pluginName);
      for (Map.Entry<String, Provider<OAuthServiceProvider>> e : m.entrySet()) {
        addProvider(providers, pluginName, e.getKey(), e.getValue().get().getName());
      }
    }

    sendHtml(res, doc);
  }

  private static void addProvider(Element form, String pluginName, String id, String serviceName) {
    Element div = form.getOwnerDocument().createElement("div");
    div.setAttribute("id", id);
    Element hyperlink = form.getOwnerDocument().createElement("a");
    hyperlink.setAttribute("href", String.format("?provider=%s_%s", pluginName, id));
    hyperlink.setTextContent(serviceName + " (" + pluginName + " plugin)");
    div.appendChild(hyperlink);
    form.appendChild(div);
  }

  private static void sendHtml(HttpServletResponse res, Document doc) throws IOException {
    byte[] bin = HtmlDomUtil.toUTF8(doc);
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setContentType("text/html");
    res.setCharacterEncoding(UTF_8.name());
    res.setContentLength(bin.length);
    try (ServletOutputStream out = res.getOutputStream()) {
      out.write(bin);
    }
  }

  private void pickSSOServiceProvider() throws ServletException {
    SortedSet<String> plugins = oauthServiceProviders.plugins();
    if (plugins.isEmpty()) {
      throw new ServletException("OAuth service provider wasn't installed");
    }
    if (plugins.size() == 1) {
      SortedMap<String, Provider<OAuthServiceProvider>> services =
          oauthServiceProviders.byPlugin(Iterables.getOnlyElement(plugins));
      if (services.size() == 1) {
        ssoProvider = Iterables.getOnlyElement(services.values()).get();
      }
    }
  }

  private static boolean isGerritLogin(HttpServletRequest request) {
    return request.getRequestURI().indexOf(GERRIT_LOGIN) >= 0;
  }
}
