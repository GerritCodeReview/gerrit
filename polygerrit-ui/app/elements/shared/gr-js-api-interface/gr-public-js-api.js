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

  function Plugin(url) {
    this._url = url;
    if (this._url.pathname.indexOf('/plugins') === -1) {
      console.warn('Plugin not being loaded from /plugins base path:',
          url.href, '— Unable to determine name.');
      return;
    }
    this._name = url.pathname.split('/')[2];
  }

  Plugin.prototype._name = '';

  Plugin.prototype.on = function(eventName, callback) {
    document.createElement('gr-js-api-interface').addEventCallback(eventName,
        callback);
  };

  Plugin.prototype.url = function(opt_path) {
    return this._url.origin + '/plugins/' + this._name + opt_path || '/';
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
    // TODO(andybons): Polyfill currentScript for IE10/11 (edge supports it).
    callback(new Plugin(new URL(document.currentScript.src)));
  };

  window.Gerrit = Gerrit;
})(window);
