// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.gerrit.common.data.GlobalCapability.FLUSH_CACHES;
import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;

import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.FlushCache.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@RequiresAnyCapability({FLUSH_CACHES, MAINTAIN_SERVER})
@Singleton
public class FlushCache implements RestModifyView<CacheResource, Input> {
  public static class Input {}

  public static final String WEB_SESSIONS = "web_sessions";

  private final Provider<CurrentUser> self;

  @Inject
  public FlushCache(Provider<CurrentUser> self) {
    this.self = self;
  }

  @Override
  public Response<String> apply(CacheResource rsrc, Input input) throws AuthException {
    if (WEB_SESSIONS.equals(rsrc.getName()) && !self.get().getCapabilities().canMaintainServer()) {
      throw new AuthException(String.format("only site maintainers can flush %s", WEB_SESSIONS));
    }

    rsrc.getCache().invalidateAll();
    return Response.ok("");
  }
}
