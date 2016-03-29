// Copyright (C) 2016 The Android Open Source Project
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

  function Plugin() {}

  Plugin.prototype.on = function(eventName, callback) {
    document.createElement('gr-js-api-interface').addEventCallback(eventName,
        callback);
  };

  var Gerrit = window.Gerrit || {};

  Gerrit.css = function(rulesStr) {
    if (!Gerrit._customStyleSheet) {
      var styleEl = document.createElement('style');
      document.head.appendChild(styleEl);
      Gerrit._customStyleSheet = styleEl.sheet;
    }

    var name = '__pg_js_api_class_' + Gerrit._customStyleSheet.cssRules.length;
    Gerrit._customStyleSheet.insertRule('.' + name + '{' + rulesStr + '}', 0);
    return name;
  },

  Gerrit.install = function(callback) {
    callback(new Plugin());
  };

  window.Gerrit = Gerrit;
})(window);
