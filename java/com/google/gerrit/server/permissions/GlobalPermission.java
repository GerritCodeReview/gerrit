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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.CapabilityScope;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.access.GlobalOrPluginPermission;
import com.google.gerrit.extensions.api.access.PluginPermission;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Global server permissions built into Gerrit. */
public enum GlobalPermission implements GlobalOrPluginPermission {
  ACCESS_DATABASE(GlobalCapability.ACCESS_DATABASE),
  ADMINISTRATE_SERVER(GlobalCapability.ADMINISTRATE_SERVER),
  CREATE_ACCOUNT(GlobalCapability.CREATE_ACCOUNT),
  CREATE_GROUP(GlobalCapability.CREATE_GROUP),
  CREATE_PROJECT(GlobalCapability.CREATE_PROJECT),
  EMAIL_REVIEWERS(GlobalCapability.EMAIL_REVIEWERS),
  FLUSH_CACHES(GlobalCapability.FLUSH_CACHES),
  KILL_TASK(GlobalCapability.KILL_TASK),
  MAINTAIN_SERVER(GlobalCapability.MAINTAIN_SERVER),
  MODIFY_ACCOUNT(GlobalCapability.MODIFY_ACCOUNT),
  RUN_AS(GlobalCapability.RUN_AS),
  RUN_GC(GlobalCapability.RUN_GC),
  STREAM_EVENTS(GlobalCapability.STREAM_EVENTS),
  VIEW_ALL_ACCOUNTS(GlobalCapability.VIEW_ALL_ACCOUNTS),
  VIEW_CACHES(GlobalCapability.VIEW_CACHES),
  VIEW_CONNECTIONS(GlobalCapability.VIEW_CONNECTIONS),
  VIEW_PLUGINS(GlobalCapability.VIEW_PLUGINS),
  VIEW_QUEUE(GlobalCapability.VIEW_QUEUE);

  private static final Logger log = LoggerFactory.getLogger(GlobalPermission.class);
  private static final ImmutableMap<String, GlobalPermission> BY_NAME;

  static {
    ImmutableMap.Builder<String, GlobalPermission> m = ImmutableMap.builder();
    for (GlobalPermission p : values()) {
      m.put(p.permissionName(), p);
    }
    BY_NAME = m.build();
  }

  @Nullable
  public static GlobalPermission byName(String name) {
    return BY_NAME.get(name);
  }

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

  private final String name;

  GlobalPermission(String name) {
    this.name = name;
  }

  /** @return name used in {@code project.config} permissions. */
  @Override
  public String permissionName() {
    return name;
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

    GlobalPermission perm = byName(capability);
    if (perm == null) {
      log.error(
          String.format("Class %s requires unknown capability %s", clazz.getName(), capability));
      throw new PermissionBackendException("cannot extract permission");
    }
    return perm;
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
}
