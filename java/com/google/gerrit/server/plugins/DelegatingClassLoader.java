// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

public class DelegatingClassLoader extends ClassLoader {
  private final ClassLoader target;

  public DelegatingClassLoader(ClassLoader parent, ClassLoader target) {
    super(parent);
    this.target = target;
  }

  @Override
  public Class<?> findClass(String name) throws ClassNotFoundException {
    String path = name.replace('.', '/') + ".class";
    try (InputStream resource = target.getResourceAsStream(path)) {
      if (resource != null) {
        try {
          byte[] bytes = ByteStreams.toByteArray(resource);
          return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
          // throws ClassNotFoundException later
        }
      }
    } catch (IOException e) {
      // throws ClassNotFoundException later
    }
    throw new ClassNotFoundException(name);
  }

  @Override
  public URL getResource(String name) {
    URL rtn = getParent().getResource(name);
    if (rtn == null) {
      rtn = target.getResource(name);
    }
    return rtn;
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    Enumeration<URL> rtn = getParent().getResources(name);
    if (rtn == null) {
      rtn = target.getResources(name);
    }
    return rtn;
  }

  @Override
  public InputStream getResourceAsStream(String name) {
    InputStream rtn = getParent().getResourceAsStream(name);
    if (rtn == null) {
      rtn = target.getResourceAsStream(name);
    }
    return rtn;
  }
}
