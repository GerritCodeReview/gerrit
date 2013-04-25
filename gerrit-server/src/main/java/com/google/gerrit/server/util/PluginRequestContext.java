// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PluginUser;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

/** RequestContext active while plugins load or unload. */
public class PluginRequestContext implements RequestContext {
  private final PluginUser user;

  public PluginRequestContext(PluginUser user) {
    this.user = user;
  }

  @Override
  public CurrentUser getCurrentUser() {
    return user;
  }

  @Override
  public Provider<ReviewDb> getReviewDbProvider() {
    return new Provider<ReviewDb>() {
      @Override
      public ReviewDb get() {
        throw new ProvisionException(
            "Automatic ReviewDb only available in request scope");
      }
    };
  }
}
