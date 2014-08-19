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

package com.google.gerrit.server.account;

import com.google.gerrit.extensions.annotations.CapabilityScope;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;

public class CapabilityUtils {
  private static final Logger log = LoggerFactory
      .getLogger(CapabilityUtils.class);

  public static void checkRequiresCapability(Provider<CurrentUser> userProvider,
      String pluginName, Class<?> clazz)
      throws AuthException {
    RequiresCapability rc = getClassAnnotation(clazz, RequiresCapability.class);
    if (rc != null) {
      CurrentUser user = userProvider.get();
      CapabilityControl ctl = user.getCapabilities();
      if (ctl.canAdministrateServer()) {
        return;
      }

      String capability = rc.value();
      if (pluginName != null && !"gerrit".equals(pluginName)
         && (rc.scope() == CapabilityScope.PLUGIN
          || rc.scope() == CapabilityScope.CONTEXT)) {
        capability = String.format("%s-%s", pluginName, rc.value());
      } else if (rc.scope() == CapabilityScope.PLUGIN) {
        log.error(String.format(
            "Class %s uses @%s(scope=%s), but is not within a plugin",
            clazz.getName(),
            RequiresCapability.class.getSimpleName(),
            CapabilityScope.PLUGIN.name()));
        throw new AuthException("cannot check capability");
      }

      if (!ctl.canPerform(capability)) {
        throw new AuthException(String.format(
            "Capability %s is required to access this resource",
            capability));
      }
    }
  }

  /**
   * Find an instance of the specified annotation, walking up the inheritance
   * tree if necessary.
   *
   * @param <T> Annotation type to search for
   * @param clazz root class to search, may be null
   * @param annotationClass class object of Annotation subclass to search for
   * @return the requested annotation or null if none
   */
  private static <T extends Annotation> T getClassAnnotation(Class<?> clazz,
      Class<T> annotationClass) {
    for (; clazz != null; clazz = clazz.getSuperclass()) {
      T t = clazz.getAnnotation(annotationClass);
      if (t != null) {
        return t;
      }
    }
    return null;
  }
}
