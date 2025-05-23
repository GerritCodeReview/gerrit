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

import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.servlet.ServletScopes;
import com.google.inject.util.Providers;
import com.google.inject.util.Types;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.servlet.http.HttpServletRequest;

/** Propagator for Guice's built-in servlet scope. */
public class GuiceRequestScopePropagator extends RequestScopePropagator {

  private final HttpCanonicalWebUrlProvider urlProvider;
  private final SocketAddress peer;
  private final Provider<HttpServletRequest> request;

  @Inject
  GuiceRequestScopePropagator(
      HttpCanonicalWebUrlProvider urlProvider,
      @RemotePeer Provider<SocketAddress> remotePeerProvider,
      ThreadLocalRequestContext local,
      Provider<HttpServletRequest> request) {
    super(ServletScopes.REQUEST, local);
    this.urlProvider = urlProvider;
    this.peer = remotePeerProvider.get();
    this.request = request;
    // Ensure HttpServletRequest is propagated to HttpCanonicalWebUrlProvider
    // so that it won't fallback to the default host name.
    urlProvider.setHttpServletRequest(request);
  }

  /**
   * @see RequestScopePropagator#wrap(Callable)
   */
  // ServletScopes#continueRequest is deprecated, but it's not obvious their
  // recommended replacement is an appropriate drop-in solution; see
  // https://gerrit-review.googlesource.com/83971
  @SuppressWarnings("deprecation")
  @Override
  protected <T> Callable<T> wrapImpl(Callable<T> callable) {
    Map<Key<?>, Object> seedMap = new HashMap<>();
    String canonicalWebUrl = urlProvider.get();

    // Request scopes appear to use specific keys in their map, instead of only
    // providers. Add bindings for both the key to the instance directly and the
    // provider to the instance to be safe.
    seedMap.put(
        Key.get(typeOfProvider(String.class), CanonicalWebUrl.class),
        Providers.of(canonicalWebUrl));
    seedMap.put(Key.get(String.class, CanonicalWebUrl.class), canonicalWebUrl);

    seedMap.put(Key.get(typeOfProvider(SocketAddress.class), RemotePeer.class), Providers.of(peer));
    seedMap.put(Key.get(SocketAddress.class, RemotePeer.class), peer);

    Key<?> webSessionAttrKey = Key.get(WebSession.class);
    Object webSessionAttrValue = request.get().getAttribute(webSessionAttrKey.toString());
    seedMap.put(webSessionAttrKey, webSessionAttrValue);
    seedMap.put(Key.get(typeOfProvider(WebSession.class)), Providers.of(webSessionAttrValue));

    return ServletScopes.continueRequest(callable, seedMap);
  }

  private ParameterizedType typeOfProvider(Type type) {
    return Types.newParameterizedType(Provider.class, type);
  }
}
