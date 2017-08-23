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

package com.google.gerrit.httpd.rpc;

import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

/** Creates {@link GerritJsonServlet} with a {@link RemoteJsonService}. */
class GerritJsonServletProvider implements Provider<GerritJsonServlet> {
  @Inject private Injector injector;

  private final Class<? extends RemoteJsonService> serviceClass;

  @Inject
  GerritJsonServletProvider(Class<? extends RemoteJsonService> c) {
    serviceClass = c;
  }

  @Override
  public GerritJsonServlet get() {
    final RemoteJsonService srv = injector.getInstance(serviceClass);
    return injector
        .createChildInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(RemoteJsonService.class).toInstance(srv);
              }
            })
        .getInstance(GerritJsonServlet.class);
  }
}
