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

package com.google.gerrit.server.rules.prolog;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import java.util.Set;

/** Loads the classes for Prolog predicates. */
class PredicateClassLoader extends ClassLoader {

  private final SetMultimap<String, ClassLoader> packageClassLoaderMap =
      LinkedHashMultimap.create();

  PredicateClassLoader(PluginSetContext<PredicateProvider> predicateProviders, ClassLoader parent) {
    super(parent);

    predicateProviders.runEach(
        predicateProvider -> {
          for (String pkg : predicateProvider.getPackages()) {
            packageClassLoaderMap.put(pkg, predicateProvider.getClass().getClassLoader());
          }
        });
  }

  @Override
  protected Class<?> findClass(String className) throws ClassNotFoundException {
    final Set<ClassLoader> classLoaders = packageClassLoaderMap.get(getPackageName(className));
    for (ClassLoader cl : classLoaders) {
      try {
        return Class.forName(className, true, cl);
      } catch (ClassNotFoundException e) {
        // ignore
      }
    }
    throw new ClassNotFoundException(className);
  }

  private static String getPackageName(String className) {
    final int pos = className.lastIndexOf('.');
    if (pos < 0) {
      return "";
    }
    return className.substring(0, pos);
  }
}
