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

  // Prevent redefinition.
  if (window.GrProjectApi) { return; }

  function GrProjectApi(plugin) {
    this._hook = null;
    this.plugin = plugin;
  }

  GrProjectApi.prototype._createHook = function(title) {
    this._hook = this.plugin.hook('project-command').onAttached(element => {
      const pluginCommand =
            document.createElement('gr-plugin-project-command');
      pluginCommand.title = title;
      element.appendChild(pluginCommand);
    });
  };

  GrProjectApi.prototype.createCommand = function(title, callback) {
    if (this._hook) {
      console.warn('Already set up.');
      return this._hook;
    }
    this._createHook(title);
    this._hook.onAttached(element => {
      if (callback(element.projectName, element.config) === false) {
        element.hidden = true;
      }
    });
    return this;
  };

  GrProjectApi.prototype.onTap = function(callback) {
    if (!this._hook) {
      console.warn('Call createCommand first.');
      return this;
    }
    this._hook.onAttached(element => {
      this.plugin.eventHelper(element).on('command-tap', callback);
    });
    return this;
  };

  window.GrProjectApi = GrProjectApi;
})(window);
