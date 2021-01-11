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
 * To provide an API as an extension to other plugins, bind a PluginProvidedApi. For example:
 *
 * <pre>
 *   bind(com.google.gerrit.extensions.registration.PluginProvidedApi.class)
 *       .annotatedWith(Exports.named("MyApi"))
 *       .to(MyApiImpl.class);
 * </pre>
 *
 * To define the API as an extension, extend PluginProvidedApi interface. For example:
 *
 * <pre>
 *   public interface MyApi extends PluginProvidedApi {
 *     public List<String> getData();
 *   }
 * </pre>
 *
 * <pre>
 *   public class MyApiImpl implements MyApi {
 *     @Override
 *     public List<String> getData() {
 *       // implementation
 *       return data;
 *     }
 *   }
 * </pre>
 *
 * To consume the API provided by a plugin from other plugin, inject the PluginProvidedApi
 * DynamicMap and fetch the respective API. For example:
 *
 * <pre>
 *   public class MyClass {
 *     protected DynamicMap<PluginProvidedApi> pluginProvidedApi;
 *
 *     @Inject
 *     public MyClass(DynamicMap<PluginProvidedApi> pluginProvidedApi) {
 *       this.pluginProvidedApi = pluginProvidedApi;
 *     }
 *
 *     public List<String> getMyData() {
 *       PluginProvidedApi pluginProvidedApi = pluginProvidedApi.get("my-api-plugin", "MyApi");
 *       MyApi myApi = getProxyInstance(pluginProvidedApi); // generate a proxy instance to MyApi
 *                                                          // interface through reflection.
 *       return myApi.getData();
 *     }
 *   }
 * </pre>
 */
public interface PluginProvidedApi {}
