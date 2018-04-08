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

package com.google.gerrit.httpd;

import com.google.gerrit.config.CanonicalWebUrlProvider;
import com.google.gerrit.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.SystemReader;

/** Sets {@code CanonicalWebUrl} to current HTTP request if not configured. */
public class HttpCanonicalWebUrlProvider extends CanonicalWebUrlProvider {
  private Provider<HttpServletRequest> requestProvider;

  @Inject
  HttpCanonicalWebUrlProvider(@GerritServerConfig Config config) {
    super(config);
  }

  @Inject(optional = true)
  public void setHttpServletRequest(Provider<HttpServletRequest> hsr) {
    requestProvider = hsr;
  }

  @Override
  public String get() {
    String canonicalUrl = super.get();
    if (canonicalUrl != null) {
      return canonicalUrl;
    }

    return guessUrlFromHttpRequest()
        .orElseGet(() -> "http://" + SystemReader.getInstance().getHostname() + '/');
  }

  private Optional<String> guessUrlFromHttpRequest() {
    if (requestProvider == null) {
      // We have no way of guessing our HTTP url.
      return Optional.empty();
    }

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
        return Optional.empty();
      }
      throw noWeb;
    }
    return Optional.of(CanonicalWebUrl.computeFromRequest(req));
  }
}
