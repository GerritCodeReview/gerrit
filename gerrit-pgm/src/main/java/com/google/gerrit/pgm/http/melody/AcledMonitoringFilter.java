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

package com.google.gerrit.pgm.http.melody;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

import net.bull.javamelody.MonitoringFilter;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@Singleton
public class AcledMonitoringFilter extends MonitoringFilter {
  public static class Module extends ServletModule {

    private boolean monitoringEnabled;

    @Inject
    Module(@GerritServerConfig final Config cfg) {
      this.monitoringEnabled = cfg.getBoolean("melody", "monitoring", false);
    }

    @Override
    protected void configureServlets() {
      // Add the filter only if the melody - monitoring config option is true.
      if (monitoringEnabled) {
        filter("/*").through(AcledMonitoringFilter.class);
      }
    }
  }

  private final Provider<CurrentUser> userProvider;

  @Inject
  AcledMonitoringFilter(final Provider<CurrentUser> userProvider) {
    this.userProvider = userProvider;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletResponse httpResponse = (HttpServletResponse) response;
    HttpServletRequest httpRequest = (HttpServletRequest) request;

    if(isRequestAllowed(httpRequest)) {
      super.doFilter(request, response, chain);
    } else {
      httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden access");
    }
  }

  private boolean isRequestAllowed(HttpServletRequest httpRequest) {
    CurrentUser currentUser = userProvider.get();
    if(httpRequest.getRequestURI().equals(getMonitoringUrl(httpRequest))) {
      return currentUser.getCapabilities().canAdministrateServer();
    }
    return true;
  }
}
