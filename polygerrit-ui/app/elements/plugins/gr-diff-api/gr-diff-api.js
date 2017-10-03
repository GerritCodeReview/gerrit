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
  if (window.GrDiffApi) { return; }

  function GrDiffApi(plugin) {
    this.plugin = plugin;
  }

  GrDiffApi.prototype.createCommand = function(callback) {
    console.log('createCommand in gr-diff-api');
    this.plugin.hook('file-diff').onAttached(element => {
      if (!element.content) {return;}
      console.log('--- Logs from gr-diff-api.js ---');
      console.log(element.content);
      console.log(element.content.changeNum);
      console.log(document.getElementsByClassName("style-scope gr-diff right lineNum").length)

      // Call the plugin.
      callback(element.content);
    });
    return this;
  };

  window.GrDiffApi = GrDiffApi;
})(window);
