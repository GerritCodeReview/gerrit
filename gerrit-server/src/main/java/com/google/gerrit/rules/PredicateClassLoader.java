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
package com.google.gerrit.rules;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.registration.DynamicSet;
import java.util.Collection;

/** Loads the classes for Prolog predicates. */
public class PredicateClassLoader extends ClassLoader {

  private final Multimap<String, ClassLoader> packageClassLoaderMap = LinkedHashMultimap.create();

  public PredicateClassLoader(
      final DynamicSet<PredicateProvider> predicateProviders, final ClassLoader parent) {
    super(parent);

    for (PredicateProvider predicateProvider : predicateProviders) {
      for (String pkg : predicateProvider.getPackages()) {
        packageClassLoaderMap.put(pkg, predicateProvider.getClass().getClassLoader());
      }
    }
  }

  @Override
  protected Class<?> findClass(final String className) throws ClassNotFoundException {
    final Collection<ClassLoader> classLoaders =
        packageClassLoaderMap.get(getPackageName(className));
    for (final ClassLoader cl : classLoaders) {
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
