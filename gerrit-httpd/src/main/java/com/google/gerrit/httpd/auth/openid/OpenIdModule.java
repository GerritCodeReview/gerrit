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

import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.gerrit.httpd.rpc.RpcServletModule;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.TypeLiteral;
import com.google.inject.servlet.ServletModule;

import java.util.List;

/** Servlets and RPC support related to OpenID authentication. */
public class OpenIdModule extends ServletModule {
  @Override
  protected void configureServlets() {
    install(new CacheModule() {
      @SuppressWarnings("unchecked")
      @Override
      protected void configure() {
        final TypeLiteral<Cache<String, List>> type =
            new TypeLiteral<Cache<String, List>>() {};
        core(type, "openid") //
            .maxAge(5, MINUTES) // don't cache too long, might be stale
            .memoryLimit(64) // short TTL means we won't have many entries
        ;
      }
    });

    serve("/" + OpenIdServiceImpl.RETURN_URL).with(OpenIdLoginServlet.class);
    serve("/" + XrdsServlet.LOCATION).with(XrdsServlet.class);
    filter("/").through(XrdsFilter.class);

    install(new RpcServletModule(RpcServletModule.PREFIX) {
      @Override
      protected void configureServlets() {
        rpc(OpenIdServiceImpl.class);
      }
    });
  }
}
