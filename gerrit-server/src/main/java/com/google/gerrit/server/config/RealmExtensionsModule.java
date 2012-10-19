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

package com.google.gerrit.server.config;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.RealmExtension;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;

import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.net.URL;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

class RealmExtensionsModule extends AbstractModule {

  @Inject
  private Injector injector;

  @Override
  protected void configure() {
    DynamicSet.setOf(binder(), RealmExtension.class);

    Set<Class<? extends RealmExtension>> modules = findAllRealmExtensions();
    bindRealmExtensions(modules);

    bind(Realm.class).toProvider(RealmProvider.class);
    bind(RealmExtension.class).toProvider(RealmExtensionProvider.class);
    bind(RealmCacheModule.class).toProvider(RealmCacheModuleProvider.class);
    bind(ServletModule.class).annotatedWith(RealmWebModule.class).toProvider(
        RealmWebModuleProvider.class);
  }

  private Set<Class<? extends RealmExtension>> findAllRealmExtensions() {
    Set<URL> classPath = ClasspathHelper.forJavaClassPath();
    // TODO we should some how limit list of scanned url's, for now we just scan
    // those that contains 'gerrit'
    Iterables.removeIf(classPath, new Predicate<URL>() {
      @Override
      public boolean apply(@Nullable URL input) {
        return input == null || !input.getPath().contains("gerrit");
      }
    });
    ConfigurationBuilder config = new ConfigurationBuilder().setUrls(classPath);
    Reflections reflections = new Reflections(config);
    Set<Class<? extends RealmExtension>> modules =
        reflections.getSubTypesOf(RealmExtension.class);
    return modules;
  }

  private void bindRealmExtensions(Set<Class<? extends RealmExtension>> modules) {
    for (Class<? extends RealmExtension> module : modules) {
      bind(RealmExtension.class).annotatedWith(Names.named(module.getName()))
          .to(module);
    }
  }
}
