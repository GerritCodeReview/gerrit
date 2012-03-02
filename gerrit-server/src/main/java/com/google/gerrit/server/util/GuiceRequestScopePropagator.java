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

package com.google.gerrit.server.util;

import com.google.common.collect.Maps;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.servlet.ServletScopes;
import com.google.inject.util.Providers;
import com.google.inject.util.Types;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

/** Propagator for Guice's built-in servlet scope. */
public class GuiceRequestScopePropagator extends RequestScopePropagator {

  private final Provider<String> urlProvider;
  private final Provider<SocketAddress> socketAddressProvider;
  private final Provider<CurrentUser> currentUserProvider;

  @Inject
  GuiceRequestScopePropagator(
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      @RemotePeer Provider<SocketAddress> socketAddressProvider,
      Provider<CurrentUser> currentUserProvider) {
    this.urlProvider = urlProvider;
    this.socketAddressProvider = socketAddressProvider;
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Callable<T> wrap(Callable<T> callable) {
    Map<Key<?>, Object> seedMap = Maps.newHashMap();

    String url = urlProvider.get();
    seedMap.put(Key.get(typeOfProvider(String.class), CanonicalWebUrl.class),
        Providers.of(url));
    seedMap.put(Key.get(String.class, CanonicalWebUrl.class), url);

    SocketAddress addr = socketAddressProvider.get();
    seedMap.put(Key.get(typeOfProvider(SocketAddress.class), RemotePeer.class),
        Providers.of(addr));
    seedMap.put(Key.get(SocketAddress.class, RemotePeer.class), addr);

    CurrentUser user = currentUserProvider.get();
    seedMap.put(Key.get(typeOfProvider(CurrentUser.class)), Providers.of(user));
    seedMap.put(Key.get(CurrentUser.class), user);

    return ServletScopes.continueRequest(callable, seedMap);
  }

  private ParameterizedType typeOfProvider(Type type) {
    return Types.newParameterizedType(Provider.class, type);
  }
}
