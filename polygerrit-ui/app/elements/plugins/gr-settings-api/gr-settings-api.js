/**
 * @license
 * Copyright (C) 2017 The Android Open Source Settings
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
import '../../../scripts/bundled-polymer.js';

import '../../settings/gr-settings-view/gr-settings-item.js';
import '../../settings/gr-settings-view/gr-settings-menu-item.js';
const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `<dom-module id="gr-settings-api">
  
</dom-module>`;

document.head.appendChild($_documentContainer.content);

(function(window) {
  'use strict';

  /** @constructor */
  function GrSettingsApi(plugin) {
    this._title = '(no title)';
    // Generate default screen URL token, specific to plugin, and unique(ish).
    this._token =
      plugin.getPluginName() + Math.random().toString(36)
          .substr(5);
    this.plugin = plugin;
  }

  GrSettingsApi.prototype.title = function(title) {
    this._title = title;
    return this;
  };

  GrSettingsApi.prototype.token = function(token) {
    this._token = token;
    return this;
  };

  GrSettingsApi.prototype.module = function(moduleName) {
    this._moduleName = moduleName;
    return this;
  };

  GrSettingsApi.prototype.build = function() {
    if (!this._moduleName) {
      throw new Error('Settings screen custom element not defined!');
    }
    const token = `x/${this.plugin.getPluginName()}/${this._token}`;
    this.plugin.hook('settings-menu-item').onAttached(el => {
      const menuItem = document.createElement('gr-settings-menu-item');
      menuItem.title = this._title;
      menuItem.href = `#${token}`;
      el.appendChild(menuItem);
    });

    return this.plugin.hook('settings-screen').onAttached(el => {
      const item = document.createElement('gr-settings-item');
      item.title = this._title;
      item.anchor = token;
      item.appendChild(document.createElement(this._moduleName));
      el.appendChild(item);
    });
  };

  window.GrSettingsApi = GrSettingsApi;
})(window);
