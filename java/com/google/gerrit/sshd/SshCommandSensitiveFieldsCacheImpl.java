// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import org.kohsuke.args4j.Option;

public class SshCommandSensitiveFieldsCacheImpl implements SshCommandSensitiveFieldsCache {
  private static final String CACHE_NAME = "sshd_sensitive_command_params";
  private final LoadingCache<Class<?>, Set<String>> sshdCommandsCache;

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, new TypeLiteral<Class<?>>() {}, new TypeLiteral<Set<String>>() {})
            .loader(Loader.class);
        bind(SshCommandSensitiveFieldsCache.class).to(SshCommandSensitiveFieldsCacheImpl.class);
      }
    };
  }

  @Inject
  SshCommandSensitiveFieldsCacheImpl(@Named(CACHE_NAME) LoadingCache<Class<?>, Set<String>> cache) {
    sshdCommandsCache = cache;
  }

  @Override
  public Set<String> get(Class<?> cmd) {
    return sshdCommandsCache.getUnchecked(cmd);
  }

  @Override
  public void evictAll() {
    sshdCommandsCache.invalidateAll();
  }

  static class Loader extends CacheLoader<Class<?>, Set<String>> {

    @Override
    public Set<String> load(Class<?> cmd) throws Exception {
      Set<String> datas = new HashSet<>();
      for (Field field : cmd.getDeclaredFields()) {
        if (field.isAnnotationPresent(SensitiveData.class)) {
          Option option = field.getAnnotation(Option.class);
          datas.add(option.name());
          for (String opt : option.aliases()) {
            datas.add(opt);
          }
        }
      }
      return datas;
    }
  }
}
