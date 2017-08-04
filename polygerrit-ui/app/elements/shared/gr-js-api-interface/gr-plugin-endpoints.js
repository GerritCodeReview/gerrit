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
(function(window) {
  'use strict';

  function GrPluginEndpoints() {
    this._endpoints = {};
  }

  GrPluginEndpoints.prototype.registerModule = function(plugin, endpoint, type,
      moduleName) {
    if (!this._endpoints[endpoint]) {
      this._endpoints[endpoint] = [];
    }
    this._endpoints[endpoint].push({
      moduleName,
      plugin,
      pluginUrl: plugin._url,
      type,
    });
  };

  /**
   * Get detailed information about modules registered with an extension
   * endpoint.
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<{
   *   moduleName: string,
   *   plugin: Plugin,
   *   pluginUrl: String,
   *   type: EndpointType,
   * }>}
   */
  GrPluginEndpoints.prototype.getDetails = function(name, opt_options) {
    const type = opt_options && opt_options.type;
    const moduleName = opt_options && opt_options.moduleName;
    if (!this._endpoints[name]) {
      return [];
    }
    return this._endpoints[name]
        .filter(item => (!type || item.type === type) &&
                    (!moduleName || moduleName == item.moduleName));
  };

  /**
   * Get detailed module names for instantiating at the endpoint.
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<string>}
   */
  GrPluginEndpoints.prototype.getModules = function(name, opt_options) {
    const modulesData = this.getDetails(name, opt_options);
    if (!modulesData.length) {
      return [];
    }
    return modulesData.map(m => m.moduleName);
  };

  /**
   * Get .html plugin URLs with element and module definitions.
   * @param {string} name Endpoint name.
   * @param {?{
   *   type: (string|undefined),
   *   moduleName: (string|undefined)
   * }} opt_options
   * @return {!Array<!URL>}
   */
  GrPluginEndpoints.prototype.getPlugins = function(name, opt_options) {
    const modulesData =
          this.getDetails(name, opt_options).filter(
              data => data.pluginUrl.pathname.includes('.html'));
    if (!modulesData.length) {
      return [];
    }
    return Array.from(new Set(modulesData.map(m => m.pluginUrl)));
  };

  window.GrPluginEndpoints = GrPluginEndpoints;
})(window);
