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

  function GrRestApiHooks() {
    this._restApiHooks = {};
  }

  /**
   * Registers an api hook for a particular api endpoint.
   */
  GrRestApiHooks.prototype.register = function(endpointName, mutationFunction) {
    this._restApiHooks[endpointName] = mutationFunction;
  };

  /**
   * Returns the registered api hook for a particular api endpoint.
   */
  GrRestApiHooks.prototype.get = function(endpointName) {
    return this._restApiHooks[endpointName];
  };

  window.GrRestApiHooks = GrRestApiHooks;
})(window);
