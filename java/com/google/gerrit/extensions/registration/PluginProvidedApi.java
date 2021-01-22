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

package com.google.gerrit.extensions.registration;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * By implementing the PluginProvidedApi interface, a plugin can provide an API which can be
 * consumed by another plugin. The API thus provided will only be accessible to other plugins and
 * will not be exposed to Gerrit users directly. To provide an API for other plugins, bind a
 * PluginProvidedApi. For example:
 *
 * <pre>
 *   // Suppose the below binding is provided by a plugin named 'my-api-plugin'.
 *   bind(com.google.gerrit.extensions.registration.PluginProvidedApi.class)
 *     .annotatedWith(Exports.named("MyApi"))
 *     .to(MyApi.class);
 *
 *   public class MyApi implements PluginProvidedApi {
 *     public String getData() {
 *       [...]
 *       return data;
 *     }
 *   }
 * </pre>
 *
 * To consume the API provided by a plugin from another plugin, inject the PluginProvidedApi
 * DynamicMap and fetch the respective API by name. The required API must be available/registered
 * when trying to request the API from PluginProvidedApi DynamicMap. The registered API is
 * implemented by a class that is loaded in the implementing plugin's classloader which is different
 * from the classloader of the consuming plugin. This means the consuming plugin cannot access the
 * MyAPI class at compile-time since there will be a class miss-match when the same compile-time
 * class is accessed via two different classloaders, i.e., if an attempt is made to assign the
 * implementation from the providing plugin's classloader to a class variable, argument, or return
 * value loaded by the consuming plugin's classloader. Due to this reason, reflection is used to
 * invoke the required method. For example:
 *
 * <pre>
 *   public class MyClass {
 *     protected DynamicMap<PluginProvidedApi> pluginProvidedApis;
 *
 *     @Inject
 *     public MyClass(DynamicMap<PluginProvidedApi> pluginProvidedApis) {
 *       this.pluginProvidedApis = pluginProvidedApis;
 *     }
 *
 *     public Optional<String> getMyData() {
 *       // Casting to original compile time class like below will fail:
 *       // MyApi api = (MyApi) pluginProvidedApis.get("my-api-plugin", "MyApi");
 *       PluginProvidedApi pluginProvidedApi = pluginProvidedApis.get("my-api-plugin", "MyApi");
 *       if (pluginProvidedApi == null) {
 *         return Optional.empty();
 *       }
 *       return getDataFromApi(pluginProvidedApi);
 *     }
 *
 *     protected Optional<String> getDataFromApi(PluginProvidedApi pluginProvidedApi) {
 *       try {
 *         return Optional.of((String) pluginProvidedApi.getClass().getMethod("getData").invoke(pluginProvidedApi));
 *       } catch (ClassCastException | IllegalAccessException | InvocationTargetException
 *           | NoSuchMethodException e) {
 *         return Optional.empty();
 *       }
 *     }
 *   }
 * </pre>
 *
 * As an alternative to the pure reflection, a proxy instance can also be created. To do this, the
 * API interface must be shared between the providing plugin and the consuming plugin. In this
 * approach, the API interface is available at compile time for the consuming plugin. This requires
 * all the API's arguments and return values to be types known to Gerrit's classloader else it will
 * result in ClassCastException. For example:
 *
 * <pre>
 *   public interface MyApi extends PluginProvidedApi {
 *     public String getData();
 *   }
 *
 *   public class MyClass {
 *     protected DynamicMap<PluginProvidedApi> pluginProvidedApis;
 *
 *     @Inject
 *     public MyClass(DynamicMap<PluginProvidedApi> pluginProvidedApis) {
 *       this.pluginProvidedApis = pluginProvidedApis;
 *     }
 *
 *     public Optional<String> getMyData() {
 *       // Casting to original compile time class like below will fail:
 *       // MyApi api = (MyApi) pluginProvidedApis.get("my-api-plugin", "MyApi");
 *       PluginProvidedApi pluginProvidedApi = pluginProvidedApis.get("my-api-plugin", "MyApi");
 *       if (pluginProvidedApi == null) {
 *         return Optional.empty();
 *       }
 *       return getDataFromApi(pluginProvidedApi);
 *     }
 *
 *     protected Optional<String> getDataFromApi(PluginProvidedApi pluginProvidedApi) {
 *       Optional<MyApi> myApi = pluginProvidedApi.getOptionalProxyInstance(MyApi.class);
 *       if (myApi.isPresent()) {
 *         try {
 *           return Optional.of(myApi.get().getData());
 *         } catch (RuntimeException e) {}
 *       }
 *       return Optional.empty();
 *     }
 *   }
 * </pre>
 */
public interface PluginProvidedApi {
  default <T extends PluginProvidedApi> T getProxyInstance(Class<T> type)
      throws ClassCastException, IllegalArgumentException {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (Object proxy, Method method, Object[] args) ->
                getClass()
                    .getMethod(method.getName(), method.getParameterTypes())
                    .invoke(this, args)));
  }

  default <T extends PluginProvidedApi> Optional<T> getOptionalProxyInstance(Class<T> type) {
    try {
      return Optional.of(getProxyInstance(type));
    } catch (ClassCastException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
