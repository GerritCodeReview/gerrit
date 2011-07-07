// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test.util;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.MessageFormat;
import java.util.Properties;

public class PropertyAccessor implements InvocationHandler {

  @SuppressWarnings("unchecked")
  public static <T> T create(Class<T> t) {
    final String propertyFile =
        t.getName().replaceAll("\\.", "/") + ".properties";
    final Properties properties = new Properties();
    try {
      properties.load(t.getClassLoader().getResourceAsStream(
          propertyFile));
    } catch (IOException e) {
      throw new RuntimeException("Failed to load properties from file '"
          + propertyFile + "'.", e);
    }
    return (T) Proxy.newProxyInstance(t.getClassLoader(), new Class[] {t},
        new PropertyAccessor(properties));
  }

  private final Properties properties;

  private PropertyAccessor(final Properties properties) {
    this.properties = properties;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args)
      throws Throwable {
    final Object property = properties.get(method.getName());
    if (args != null && property instanceof String) {
      return (new MessageFormat((String)property)).format(args);
    }
    return property;
  }
}
