// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MultiParentClassLoader extends ClassLoader {
  protected final Set<String> excludedClassNamePatterns;
  protected final LinkedHashSet<ClassLoader> parents = Sets.newLinkedHashSet();

  public MultiParentClassLoader(Set<String> excludedClassNamePatterns, List<ClassLoader> parents) {
    this.excludedClassNamePatterns = excludedClassNamePatterns;
    this.parents.addAll(parents);
  }

  public void add(ClassLoader classLoader) {
    parents.add(classLoader);
  }

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    if (anyMatchFromExclusionPatterns(name)) {
      throw new ClassNotFoundException(name);
    }
    return super.loadClass(name);
  }

  @Override
  public Class<?> findClass(String name) throws ClassNotFoundException {
    if (anyMatchFromExclusionPatterns(name)) {
      throw new ClassNotFoundException(name);
    }
    for (ClassLoader parent : parents) {
      try {
        return parent.loadClass(name);
      } catch (ClassNotFoundException e) {
      }
    }
    throw new ClassNotFoundException(name);
  }

  protected boolean anyMatchFromExclusionPatterns(String name) {
    for (String exclusionPattern : excludedClassNamePatterns) {
      if (name.matches(exclusionPattern)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public URL findResource(String name) {
    for (ClassLoader parent : parents) {
      URL url = parent.getResource(name);
      if (url != null) {
        return url;
      }
    }
    return null;
  }

  @Override
  protected Enumeration<URL> findResources(String name) throws IOException {
    List<URL> resources = new ArrayList<>();
    for (ClassLoader loader : parents) {
      resources.addAll(Collections.list(loader.getResources(name)));
    }
    return Collections.enumeration(resources);
  }
}
