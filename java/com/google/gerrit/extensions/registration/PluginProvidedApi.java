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

/**
 * By implementing the PluginProvidedApi interface, a plugin can provide an API which can be
 * consumed by another plugin. The API thus provided will only be accessible to other plugins and
 * will not be exposed to Gerrit users directly. To provide an API for other plugins, bind a
 * PluginProvidedApi. For example:
 *
 * <pre>
 *   // Consider the below binding is provided by a plugin named 'my-api-plugin'.
 *   bind(com.google.gerrit.extensions.registration.PluginProvidedApi.class)
 *       .annotatedWith(Exports.named("MyApi"))
 *       .to(MyApiImpl.class);
 * </pre>
 *
 * <pre>
 *   public interface MyApi extends PluginProvidedApi {
 *     public String getData();
 *   }
 * </pre>
 *
 * <pre>
 *   public class MyApiImpl implements MyApi {
 *     @Override
 *     public String getData() {
 *       // implementation
 *       return data;
 *     }
 *   }
 * </pre>
 *
 * To consume the API provided by a plugin from other plugin, inject the PluginProvidedApi
 * DynamicMap and fetch the respective API. The required API must be available/registered when
 * trying to request the API from PluginProvidedApi DynamicMap. The registered API is implemented by
 * a class which is loaded in the implementing plugin's class loader which is different from the
 * class loader of the consuming plugin. Due to this reason, reflection is used to invoke the
 * required method. For example:
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
 *     public String getMyData() {
 *       PluginProvidedApi pluginProvidedApi = pluginProvidedApis.get("my-api-plugin", "MyApi");
 *       return getDataFromApi(pluginProvidedApi);
 *     }
 *
 *     protected String getDataFromApi(PluginProvidedApi pluginProvidedApi) {
 *       try {
 *         // use reflection to invoke the required method.
 *         return (String) pluginProvidedApi.getClass().getMethod("getData").invoke(pluginProvidedApi);
 *       } catch (Exception e) {
 *         return null;
 *       }
 *     }
 *   }
 * </pre>
 */
public interface PluginProvidedApi {}
