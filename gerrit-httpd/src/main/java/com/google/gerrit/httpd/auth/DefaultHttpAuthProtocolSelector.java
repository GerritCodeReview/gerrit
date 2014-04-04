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

package com.google.gerrit.httpd.auth;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Selects the HttpAuthProtocolHandler to use when an Anonymous request is
 * received.
 */
@Singleton
public class DefaultHttpAuthProtocolSelector {

  private final DynamicSet<HttpAuthProtocolHandler> handlers;
  private final AnonymousHttpAuthProtocolHandler anonymousHandler;

  @Inject
  DefaultHttpAuthProtocolSelector(DynamicSet<HttpAuthProtocolHandler> handlers,
      AnonymousHttpAuthProtocolHandler anonymousHandler) {
    this.handlers = handlers;
    this.anonymousHandler = anonymousHandler;
  }

  public HttpAuthProtocolHandler selectDefault(HttpServletRequest req,
      HttpServletResponse resp) {
    for (HttpAuthProtocolHandler handler : handlers) {
      if (handler.canHandle(req)) {
        return handler;
      }
    }

    return anonymousHandler;
  }
}
