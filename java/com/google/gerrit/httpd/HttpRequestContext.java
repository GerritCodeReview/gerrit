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

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.util.RequestContext;
import com.google.inject.Inject;

class HttpRequestContext implements RequestContext {
  private final DynamicItem<WebSession> session;

  @Inject
  HttpRequestContext(DynamicItem<WebSession> session) {
    this.session = session;
  }

  @Override
  public CurrentUser getUser() {
    return session.get().getUser();
  }
}
