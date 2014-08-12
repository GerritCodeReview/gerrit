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

package com.google.gerrit.server.securestore;

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecureStoreModule extends AbstractModule {
  private static final Logger log = LoggerFactory
      .getLogger(SecureStoreModule.class);

  private final String secureStoreClassName;

  public SecureStoreModule(String secureStoreClassName) {
    this.secureStoreClassName = secureStoreClassName;
  }

  public SecureStoreModule(Config gerritConfig) {
    secureStoreClassName =
        gerritConfig.getString("gerrit", null, "secureStoreClass");
  }

  @Override
  protected void configure() {
    bind(SecureStore.class).to(getSecureStoreImpl());
  }

  @SuppressWarnings("unchecked")
  private Class<? extends SecureStore> getSecureStoreImpl() {
    if (Strings.isNullOrEmpty(secureStoreClassName)) {
      return DefaultSecureStore.class;
    } else {
      try {
        return (Class<? extends SecureStore>) Class.forName(secureStoreClassName);
      } catch (Throwable e) {
        String msg =
            String.format("Cannot load secure store class: %s",
                secureStoreClassName);
        log.error(msg, e);
        throw new RuntimeException(msg, e);
      }
    }
  }
}
