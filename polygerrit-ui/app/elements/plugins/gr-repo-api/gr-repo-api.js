/**
@license
Copyright (C) 2017 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import "../../../scripts/bundled-polymer.js";

import './gr-plugin-repo-command.js';
const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `<dom-module id="gr-repo-api">
  
</dom-module>`;

document.head.appendChild($_documentContainer.content);
/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

  // Prevent redefinition.
  if (window.GrRepoApi) { return; }

  /** @constructor */
  function GrRepoApi(plugin) {
    this._hook = null;
    this.plugin = plugin;
  }

  GrRepoApi.prototype._createHook = function(title) {
    this._hook = this.plugin.hook('repo-command').onAttached(element => {
      const pluginCommand =
            document.createElement('gr-plugin-repo-command');
      pluginCommand.title = title;
      element.appendChild(pluginCommand);
    });
  };

  GrRepoApi.prototype.createCommand = function(title, callback) {
    if (this._hook) {
      console.warn('Already set up.');
      return this._hook;
    }
    this._createHook(title);
    this._hook.onAttached(element => {
      if (callback(element.repoName, element.config) === false) {
        element.hidden = true;
      }
    });
    return this;
  };

  GrRepoApi.prototype.onTap = function(callback) {
    if (!this._hook) {
      console.warn('Call createCommand first.');
      return this;
    }
    this._hook.onAttached(element => {
      this.plugin.eventHelper(element).on('command-tap', callback);
    });
    return this;
  };

  window.GrRepoApi = GrRepoApi;
})(window);
