// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.jakarta.ServletModule;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

public class UniversalWebLoginFilter implements Filter {
  private final PluginItemContext<WebSession> session;
  private final PluginSetContext<WebLoginListener> webLoginListeners;
  private final Provider<CurrentUser> userProvider;

  public static ServletModule module() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/login*", "/logout*").through(UniversalWebLoginFilter.class);
        bind(UniversalWebLoginFilter.class).in(Singleton.class);

        DynamicSet.setOf(binder(), WebLoginListener.class);
      }
    };
  }

  @Inject
  public UniversalWebLoginFilter(
      PluginItemContext<WebSession> session,
      PluginSetContext<WebLoginListener> webLoginListeners,
      Provider<CurrentUser> userProvider) {
    this.session = session;
    this.webLoginListeners = webLoginListeners;
    this.userProvider = userProvider;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponseRecorder wrappedResponse =
        new HttpServletResponseRecorder((HttpServletResponse) response);

    Optional<IdentifiedUser> loggedInUserBefore = loggedInUser();
    chain.doFilter(request, wrappedResponse);
    Optional<IdentifiedUser> loggedInUserAfter = loggedInUser();

    if (!loggedInUserBefore.isPresent() && loggedInUserAfter.isPresent()) {
      webLoginListeners.runEach(
          l -> l.onLogin(loggedInUserAfter.get(), httpRequest, wrappedResponse));
    } else if (loggedInUserBefore.isPresent() && !loggedInUserAfter.isPresent()) {
      webLoginListeners.runEach(
          l -> l.onLogout(loggedInUserBefore.get(), httpRequest, wrappedResponse));
    }

    wrappedResponse.play();
  }

  private Optional<IdentifiedUser> loggedInUser() {
    return session.call(WebSession::isSignedIn)
        ? Optional.of(userProvider.get().asIdentifiedUser())
        : Optional.empty();
  }

  @Override
  public void destroy() {}
}
