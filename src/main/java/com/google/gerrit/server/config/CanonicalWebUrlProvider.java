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

package com.google.gerrit.server.config;

import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.spearce.jgit.lib.Config;

import javax.servlet.http.HttpServletRequest;

/** Provides {@link String} annotated with {@link CanonicalWebUrl}. */
public class CanonicalWebUrlProvider implements Provider<String> {
  private final String canonicalUrl;
  private Provider<HttpServletRequest> requestProvider;

  @Inject
  CanonicalWebUrlProvider(@GerritServerConfig final Config config) {
    String u = config.getString("gerrit", null, "canonicalweburl");
    if (u != null && !u.endsWith("/")) {
      u += "/";
    }
    canonicalUrl = u;
  }

  @Inject(optional = true)
  public void setHttpServletRequest(final Provider<HttpServletRequest> hsr) {
    requestProvider = hsr;
  }

  @Override
  public String get() {
    if (canonicalUrl != null) {
      return canonicalUrl;
    }

    if (requestProvider != null) {
      // No canonical URL configured? Maybe we can get a reasonable
      // guess from the incoming HTTP request, if we are currently
      // inside of an HTTP request scope.
      //
      final HttpServletRequest req;
      try {
        req = requestProvider.get();
      } catch (ProvisionException noWeb) {
        if (noWeb.getCause() instanceof OutOfScopeException) {
          // We can't obtain the request as we are not inside of
          // an HTTP request scope. Callers must handle null.
          //
          return null;
        } else {
          throw noWeb;
        }
      }

      final StringBuffer url = req.getRequestURL();
      url.setLength(url.length() - req.getServletPath().length());
      if (url.charAt(url.length() - 1) != '/') {
        url.append('/');
      }
      return url.toString();
    }

    // We have no way of guessing our HTTP url.
    //
    return null;
  }
}
