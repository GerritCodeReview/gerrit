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

import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.cache.Cache;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSessionManager.Val;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.IdentifiedUser.RequestFactory;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Named;
import com.google.inject.servlet.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestScoped
public class H2CacheBasedWebSession extends CacheBasedWebSession {
  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(WebSessionManager.CACHE_NAME, String.class, Val.class)
            .maximumWeight(1024) // reasonable default for many sites
            .expireAfterWrite(
                CacheBasedWebSession.MAX_AGE_MINUTES,
                MINUTES) // expire sessions if they are inactive
        ;
        install(new FactoryModuleBuilder().build(WebSessionManagerFactory.class));
        DynamicItem.itemOf(binder(), WebSession.class);
        DynamicItem.bind(binder(), WebSession.class)
            .to(H2CacheBasedWebSession.class)
            .in(RequestScoped.class);
      }
    };
  }

  @Inject
  H2CacheBasedWebSession(
      HttpServletRequest request,
      @Nullable HttpServletResponse response,
      WebSessionManagerFactory managerFactory,
      @Named(WebSessionManager.CACHE_NAME) Cache<String, Val> cache,
      AuthConfig authConfig,
      Provider<AnonymousUser> anonymousProvider,
      RequestFactory identified) {
    super(
        request, response, managerFactory.create(cache), authConfig, anonymousProvider, identified);
  }
}
