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

import com.google.common.base.Objects;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import javax.annotation.Nullable;

/**
 * ThreadLocalRequestContext manages the current RequestContext using a
 * ThreadLocal. When the context is set, the fields exposed by the context
 * are considered in scope. Otherwise, the FallbackRequestContext is used.
 */
public class ThreadLocalRequestContext {

  public static Module module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(ThreadLocalRequestContext.class);
        bind(RequestContext.class).annotatedWith(Names.named("FALLBACK"))
            .to(FallbackRequestContext.class);
      }

      @Provides
      RequestContext provideRequestContext(
          @Named("FALLBACK") RequestContext fallback) {
        return Objects.firstNonNull(local.get(), fallback);
      }

      @Provides
      CurrentUser provideCurrentUser(RequestContext ctx) {
        return ctx.getCurrentUser();
      }

      @Provides
      IdentifiedUser provideCurrentUser(CurrentUser user) {
        if (user instanceof IdentifiedUser) {
          return (IdentifiedUser) user;
        }
        throw new ProvisionException(NotSignedInException.MESSAGE,
            new NotSignedInException());
      }
    };
  }
  private static final ThreadLocal<RequestContext> local =
      new ThreadLocal<RequestContext>();

  @Inject
  ThreadLocalRequestContext() {
  }

  public RequestContext setContext(@Nullable RequestContext ctx) {
    RequestContext old = getContext();
    local.set(ctx);
    return old;
  }

  @Nullable
  public RequestContext getContext() {
    return local.get();
  }
}
