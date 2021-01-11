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
 *   // Suppose the below binding is provided by a plugin named 'my-api-plugin'.
 *   bind(com.google.gerrit.extensions.registration.PluginProvidedApi.class)
 *     .annotatedWith(Exports.named("MyApi"))
 *     .to(MyApi.class);
 *
 *   public class MyApi implements PluginProvidedApi {
 *     public BranchNameKey getBranch(Project.NameKey project) {
 *       [...]
 *       return branch;
 *     }
 *
 *     public void startMyCommand(Set<Project.NameKey> projects) {
 *       [...]
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
 *     public Optional<BranchNameKey> getBranch(Project.NameKey project) {
 *       // Casting to original compile time class like below will fail:
 *       // MyApi api = (MyApi) pluginProvidedApis.get("my-api-plugin", "MyApi");
 *       PluginProvidedApi pluginProvidedApi = pluginProvidedApis.get("my-api-plugin", "MyApi");
 *       if (pluginProvidedApi == null) {
 *         return Optional.empty();
 *       }
 *       return getBranchFromApi(pluginProvidedApi, project);
 *     }
 *
 *     protected Optional<BranchNameKey> getBranchFromApi(
 *         PluginProvidedApi pluginProvidedApi, Project.NameKey project) {
 *       try {
 *         return Optional.of(
 *             (BranchNameKey)
 *                 pluginProvidedApi
 *                     .getClass()
 *                     .getMethod("getBranch")
 *                     .invoke(pluginProvidedApi, project));
 *       } catch (ClassCastException | IllegalAccessException | InvocationTargetException
 *           | NoSuchMethodException e) {
 *         return Optional.empty();
 *       }
 *     }
 *   }
 * </pre>
 */
public interface PluginProvidedApi {}
