// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.gerrit.server.permissions.DefaultPermissionMappings.globalPermission;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.CapabilityScope;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.access.GerritPermission;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.api.access.PluginPermission;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Global server permissions built into Gerrit. */
public enum GlobalPermission implements GlobalOrPluginPermission {
  ACCESS_DATABASE,
  ADMINISTRATE_SERVER,
  CREATE_ACCOUNT,
  CREATE_GROUP,
  CREATE_PROJECT,
  EMAIL_REVIEWERS,
  FLUSH_CACHES,
  KILL_TASK,
  MAINTAIN_SERVER,
  MODIFY_ACCOUNT,
  RUN_AS,
  RUN_GC,
  STREAM_EVENTS,
  VIEW_ALL_ACCOUNTS,
  VIEW_CACHES,
  VIEW_CONNECTIONS,
  VIEW_PLUGINS,
  VIEW_QUEUE,
  VIEW_ACCESS;

  private static final Logger log = LoggerFactory.getLogger(GlobalPermission.class);

  /**
   * Extracts the {@code @RequiresCapability} or {@code @RequiresAnyCapability} annotation.
   *
   * @param pluginName name of the declaring plugin. May be {@code null} or {@code "gerrit"} for
   *     classes originating from the core server.
   * @param clazz target class to extract annotation from.
   * @return empty set if no annotations were found, or a collection of permissions, any of which
   *     are suitable to enable access.
   * @throws PermissionBackendException the annotation could not be parsed.
   */
  public static Set<GlobalOrPluginPermission> fromAnnotation(
      @Nullable String pluginName, Class<?> clazz) throws PermissionBackendException {
    RequiresCapability rc = findAnnotation(clazz, RequiresCapability.class);
    RequiresAnyCapability rac = findAnnotation(clazz, RequiresAnyCapability.class);
    if (rc != null && rac != null) {
      log.error(
          String.format(
              "Class %s uses both @%s and @%s",
              clazz.getName(),
              RequiresCapability.class.getSimpleName(),
              RequiresAnyCapability.class.getSimpleName()));
      throw new PermissionBackendException("cannot extract permission");
    } else if (rc != null) {
      return Collections.singleton(
          resolve(
              pluginName,
              rc.value(),
              rc.scope(),
              rc.fallBackToAdmin(),
              clazz,
              RequiresCapability.class));
    } else if (rac != null) {
      Set<GlobalOrPluginPermission> r = new LinkedHashSet<>();
      for (String capability : rac.value()) {
        r.add(
            resolve(
                pluginName,
                capability,
                rac.scope(),
                rac.fallBackToAdmin(),
                clazz,
                RequiresAnyCapability.class));
      }
      return Collections.unmodifiableSet(r);
    } else {
      return Collections.emptySet();
    }
  }

  public static Set<GlobalOrPluginPermission> fromAnnotation(Class<?> clazz)
      throws PermissionBackendException {
    return fromAnnotation(null, clazz);
  }

  private static GlobalOrPluginPermission resolve(
      @Nullable String pluginName,
      String capability,
      CapabilityScope scope,
      boolean fallBackToAdmin,
      Class<?> clazz,
      Class<?> annotationClass)
      throws PermissionBackendException {
    if (pluginName != null
        && !"gerrit".equals(pluginName)
        && (scope == CapabilityScope.PLUGIN || scope == CapabilityScope.CONTEXT)) {
      return new PluginPermission(pluginName, capability, fallBackToAdmin);
    }

    if (scope == CapabilityScope.PLUGIN) {
      log.error(
          String.format(
              "Class %s uses @%s(scope=%s), but is not within a plugin",
              clazz.getName(), annotationClass.getSimpleName(), scope.name()));
      throw new PermissionBackendException("cannot extract permission");
    }

    Optional<GlobalPermission> perm = globalPermission(capability);
    if (!perm.isPresent()) {
      log.error(
          String.format("Class %s requires unknown capability %s", clazz.getName(), capability));
      throw new PermissionBackendException("cannot extract permission");
    }
    return perm.get();
  }

  @Nullable
  private static <T extends Annotation> T findAnnotation(Class<?> clazz, Class<T> annotation) {
    for (; clazz != null; clazz = clazz.getSuperclass()) {
      T t = clazz.getAnnotation(annotation);
      if (t != null) {
        return t;
      }
    }
    return null;
  }

  @Override
  public String describeForException() {
    return GerritPermission.describeEnumValue(this);
  }
}
