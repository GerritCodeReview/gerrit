// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git.validators;

import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.server.cache.CacheModule;

public class RateLimiterModule extends CacheModule {
  static final String CACHE_NAME_ACCOUNTID = "rate_limits_by_account";
  static final String CACHE_NAME_REMOTEHOST = "rate_limits_by_ip";

  @Override
  protected void configure() {
    DynamicSet.bind(binder(), UploadValidationListener.class).to(RateLimitUploadListener.class);
    cache(CACHE_NAME_ACCOUNTID, Account.Id.class, RateLimiter.class).loader(LoaderAccountId.class);
    cache(CACHE_NAME_REMOTEHOST, String.class, RateLimiter.class).loader(LoaderRemoteHost.class);
  }

  private static class LoaderAccountId extends CacheLoader<Account.Id, RateLimiter> {
    @Override
    public RateLimiter load(Id key) throws Exception {
      // TODO make hard-coded rate configurable
      // hard-code rate for now
      return RateLimitUploadListener.createSmoothBurstyRateLimiter(1.0 / 60, 3600);
    }
  }

  private static class LoaderRemoteHost extends CacheLoader<String, RateLimiter> {
    @Override
    public RateLimiter load(String key) throws Exception {
      // TODO make hard-coded rate configurable
      // hard-code rate for now
      return RateLimitUploadListener.createSmoothBurstyRateLimiter(0.5 / 60, 3600);
    }
  }
}
