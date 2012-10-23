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

package com.google.gerrit.realm;

import com.google.gerrit.realm.cache.RealmCacheModule;

/**
 * Configuration of Gerrit Relam mechanism
 */
public interface RealmProvider {

  /**
   * Empty implementation of {@link RealmServletModule} that should be used when
   * given realm provider don't need additional server side configuration.
   */
  public static final RealmServletModule EMPTY_SERVLET_MODULE =
      new RealmServletModule();

  /**
   * Empty implementation of {@link RealmCacheModule} that should be used when
   * given realm don't need caches.
   */
  public static final RealmCacheModule EMPTY_CACHE_MODULE =
      new RealmCacheModule() {
        @Override
        protected void configure() {
        }
      };

  /**
   * Empty implementation of {@link RealmConfigurationInitializer} that should
   * be used when given realm don't contribute additional config options to
   * {@code gerrit.config} file.
   */
  public static final Class<? extends RealmConfigurationInitializer> EMPTY_CONFIGURATION_INITIALIZER =
      EmptyConfigurationInitializer.class;

  static final class EmptyConfigurationInitializer implements
      RealmConfigurationInitializer {
    @Override
    public void init() {
    }
  }

  /**
   * @return realm name, users would set value of {@code authType} property in
   *         {@code auth} section of {@code gerrit.config} file for this name.
   */
  public String getName();

  /**
   * @return {@link Realm} imlemenation that should be associaded with this
   *         provider
   */
  public Class<? extends Realm> getRealm();

  /**
   * @return instance of module for server side instance and servlet bindings.
   *         When implementation doesn't bind anything on server side
   *         {@link RealmProvider#EMPTY_SERVLET_MODULE} should be returned.
   */
  public RealmServletModule getServletModule();

  /**
   * @return instance of cache module that should be binded for this realm
   *         provider. When implemenation doesn't use caches
   *         {@link RealmProvider#EMPTY_CACHE_MODULE} should be returned.
   */
  public RealmCacheModule getCacheModule();

  /**
   * @return class that should be executed during Gerrit init process for asking
   *         user for additional required configuration for this realm. When
   *         given realm doesn't contribute additonal configuration
   *         {@link RealmProvider#EMPTY_CONFIGURATION_INITIALIZER} should be
   *         returned.
   */
  public Class<? extends RealmConfigurationInitializer> getConfigurationInitializer();
}
