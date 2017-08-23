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
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.servlet.ServletModule;

/** Binds {@link RemoteJsonService} implementations to a JSON servlet. */
public abstract class RpcServletModule extends ServletModule {
  public static final String PREFIX = "/gerrit_ui/rpc/";

  private final String prefix;

  protected RpcServletModule(String pathPrefix) {
    prefix = pathPrefix;
  }

  protected void rpc(Class<? extends RemoteJsonService> clazz) {
    String name = clazz.getSimpleName();
    if (name.endsWith("Impl")) {
      name = name.substring(0, name.length() - 4);
    }
    rpc(name, clazz);
  }

  protected void rpc(String name, Class<? extends RemoteJsonService> clazz) {
    final Key<GerritJsonServlet> srv = Key.get(GerritJsonServlet.class, UniqueAnnotations.create());
    final GerritJsonServletProvider provider = new GerritJsonServletProvider(clazz);
    bind(clazz);
    serve(prefix + name).with(srv);
    bind(srv).toProvider(provider).in(Scopes.SINGLETON);
  }
}
