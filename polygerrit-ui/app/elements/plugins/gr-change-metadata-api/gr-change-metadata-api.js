/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
(function(window) {
  'use strict';

  function GrChangeMetadataApi(plugin) {
    this._hook = null;
    this.plugin = plugin;
  }

  GrChangeMetadataApi.prototype._createHook = function() {
    this._hook = this.plugin.hook('change-metadata-item');
  };

  GrChangeMetadataApi.prototype.onLabelsChanged = function(callback) {
    if (!this._hook) {
      this._createHook();
    }
    this._hook.onAttached(element =>
        this.plugin.attributeHelper(element).bind('labels', callback));
    return this;
  };

  window.GrChangeMetadataApi = GrChangeMetadataApi;
})(window);
