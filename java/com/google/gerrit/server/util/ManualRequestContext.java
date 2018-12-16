// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.server.CurrentUser;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

/** Closeable version of a {@link RequestContext} with manually-specified providers. */
public class ManualRequestContext implements RequestContext, AutoCloseable {
  private final Provider<CurrentUser> userProvider;
  private final ThreadLocalRequestContext requestContext;
  private final RequestContext old;

  public ManualRequestContext(CurrentUser user, ThreadLocalRequestContext requestContext) {
    this(Providers.of(user), requestContext);
  }

  public ManualRequestContext(
      Provider<CurrentUser> userProvider, ThreadLocalRequestContext requestContext) {
    this.userProvider = userProvider;
    this.requestContext = requestContext;
    old = requestContext.setContext(this);
  }

  @Override
  public CurrentUser getUser() {
    return userProvider.get();
  }

  @Override
  public void close() {
    requestContext.setContext(old);
  }
}
