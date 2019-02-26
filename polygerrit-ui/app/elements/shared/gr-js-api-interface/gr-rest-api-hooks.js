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

  const _restHooks = {};

  function GrRestApiHooks(plugin) {
    this.plugin = plugin;
    this._restApiHooks = {};
  }

  /**
   * Registers an api hook for a particular api endpoint.
   * @param {string} endpointName the name of the endpoint.
   * @param {Function} addParameterFunction the function that returns params
   * for the plugin. Takes in current params.
   */
  GrRestApiHooks.prototype.registerRestApiParams = function(endpointName,
      addParameterFunction) {
    this._restApiHooks[endpointName] = addParameterFunction;
    if (!_restHooks[endpointName]) {
      _restHooks[endpointName] = [];
    }
    _restHooks[endpointName].push(this);
  };

  /**
   * Returns the registered api hook for a particular api endpoint.
   * @param {string} endpointName the name of the endpoint.
   * @param {!Object} initialParams the params of the rest api call.

   */
  GrRestApiHooks.prototype.getRestApiParams = function(endpointName,
      initialParams) {
    const addParameterFunction = this._restApiHooks[endpointName];
    if (!addParameterFunction) return null;
    return addParameterFunction(initialParams);
  };

  /** Gets the params for a particular mutation endpoint. */
  GrRestApiHooks.pluginParams = function(endpointName, initialParams) {
    return (_restHooks[endpointName] || []).reduce((accum, restApiHooks) => {
      const pluginParams = restApiHooks.getRestApiParams(
          endpointName, initialParams);
      if (pluginParams) {
        accum[restApiHooks.plugin.getPluginName()] =
          JSON.stringify(pluginParams);
      }
      return accum;
    }, {});
  };

  window.GrRestApiHooks = GrRestApiHooks;
})(window);
