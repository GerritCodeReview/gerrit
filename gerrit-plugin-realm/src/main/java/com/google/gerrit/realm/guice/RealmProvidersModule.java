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

package com.google.gerrit.realm.guice;

import java.net.URL;
import java.util.Set;

import javax.annotation.Nullable;

import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.realm.RealmProvider;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

/**
 * Finds and binds all implementation of {@link RealmProvider} class. Then they
 * can be accessed using {@link DynamicSet}<RealmProvider>
 */
public class RealmProvidersModule extends AbstractModule {
  public static final String REQUIRED_NAME_PART = "realm";

  @Override
  protected void configure() {
    DynamicSet.setOf(binder(), RealmProvider.class);
    Set<Class<? extends RealmProvider>> realmProviders =
        findAllRealmProviders();

    for (Class<? extends RealmProvider> provider : realmProviders) {
      bindRealm(provider);
    }
  }

  private Set<Class<? extends RealmProvider>> findAllRealmProviders() {
    Set<URL> searchUrls = ClasspathHelper.forManifest();
    Iterables.removeIf(searchUrls, new Predicate<URL>() {
      @Override
      public boolean apply(@Nullable URL input) {
        return input == null || !input.getPath().contains(REQUIRED_NAME_PART);
      }
    });
    ConfigurationBuilder configuration = new ConfigurationBuilder();
    configuration.setUrls(searchUrls);
    configuration.useParallelExecutor();
    Reflections reflections = new Reflections(configuration);
    return reflections.getSubTypesOf(RealmProvider.class);
  }

  private void bindRealm(Class<? extends RealmProvider> provider) {
    String name = provider.getName();
    bind(RealmProvider.class).annotatedWith(Names.named(name)).to(provider);
  }
}
