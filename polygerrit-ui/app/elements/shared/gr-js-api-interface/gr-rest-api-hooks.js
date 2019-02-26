/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  // Prevent redefinition.
  if (window.GrRestApiHooks) { return; }

  // Stores a map of endpointNames and functions to add parameters to REST API
  // calls.
  const _apiInstances = {};

  function GrRestApiHooks(plugin) {
    this.plugin = plugin;
    this._addParameterFunctions = {};
  }

  /**
   * Registers an api hook for a particular api endpoint.
   * This is called by a plugin.
   *
   * @param {string} endpointName the name of the endpoint.
   * @param {Function} addParameterFunction the function that returns params
   * for the plugin. Takes in current params.
   */
  GrRestApiHooks.prototype.registerRestApiParams = function(endpointName,
      addParameterFunction) {
    if (this._addParameterFunctions[endpointName]) {
      console.warn(`Rewriting rest api parameter function for
        ${this.plugin.getPluginName()}`);
    }
    this._addParameterFunctions[endpointName] = addParameterFunction;
    if (!_apiInstances[endpointName]) {
      _apiInstances[endpointName] = [];
    }
    _apiInstances[endpointName].push(this);
  };

  /**
   * Returns params for a registered api hook for a particular api endpoint or
   * null.
   * This is called by the application, not the plugin.
   * It will either return params or null if there are no params.
   * @param {string} endpointName the name of the endpoint.
   * @param {!Object} initialParams the params of the rest api call.
   */
  GrRestApiHooks.prototype.getRestApiParams = function(endpointName,
      initialParams) {
    const addParameterFunction = this._addParameterFunctions[endpointName];
    if (!addParameterFunction) return null;
    return addParameterFunction(initialParams);
  };

  /**
   * Gets the params for a particular mutation endpoint.
   *
   * This is called by the application and should not be called by plugins.
  */
  GrRestApiHooks.pluginParams = function(endpointName, initialParams) {
    return (_apiInstances[endpointName] || []).reduce((accum, apiInstance) => {
      const pluginParams = apiInstance.getRestApiParams(
          endpointName, initialParams);
      if (pluginParams) {
        accum[apiInstance.plugin.getPluginName()] =
          JSON.stringify(pluginParams);
      }
      return accum;
    }, {});
  };

  window.GrRestApiHooks = GrRestApiHooks;
})(window);
