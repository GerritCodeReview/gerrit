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

  var warnNotSupported = function(opt_name) {
    console.warn('Plugin API method ' + (opt_name || '') + ' is not supported');
  };

  var stubbedMethods = ['_loadedGwt', 'screen', 'settingsScreen', 'panel'];
  var GWT_PLUGIN_STUB = {};
  stubbedMethods.forEach(function(name) {
    GWT_PLUGIN_STUB[name] = warnNotSupported.bind(null, name);
  });

  var API_VERSION = '0.1';

  // GWT JSNI uses $wnd to refer to window.
  // http://www.gwtproject.org/doc/latest/DevGuideCodingBasicsJSNI.html
  window.$wnd = window;

  function Plugin(opt_url) {
    if (!opt_url) {
      console.warn('Plugin not being loaded from /plugins base path.',
          'Unable to determine name.');
      return;
    }

    this._url = new URL(opt_url);
    if (this._url.pathname.indexOf('/plugins') !== 0) {
      console.warn('Plugin not being loaded from /plugins base path:',
          this._url.href, '— Unable to determine name.');
      return;
    }
    this._name = this._url.pathname.split('/')[2];
  }

  Plugin._sharedAPIElement = document.createElement('gr-js-api-interface');

  Plugin.prototype._name = '';

  Plugin.prototype.getPluginName = function() {
    return this._name;
  };

  Plugin.prototype.getServerInfo = function() {
    return document.createElement('gr-rest-api-interface').getConfig();
  };

  Plugin.prototype.on = function(eventName, callback) {
    Plugin._sharedAPIElement.addEventCallback(eventName, callback);
  };

  Plugin.prototype.url = function(opt_path) {
    return this._url.origin + '/plugins/' + this._name + (opt_path || '/');
  };

  Plugin.prototype.changeActions = function() {
    return new GrChangeActionsInterface(Plugin._sharedAPIElement.getElement(
        Plugin._sharedAPIElement.Element.CHANGE_ACTIONS));
  };

  Plugin.prototype.changeReply = function() {
    return new GrChangeReplyInterface(Plugin._sharedAPIElement.getElement(
        Plugin._sharedAPIElement.Element.REPLY_DIALOG));
  };

  var Gerrit = window.Gerrit || {};

  // Number of plugins to initialize, -1 means 'not yet known'.
  Gerrit._pluginsPending = -1;

  Gerrit.getPluginName = function() {
    console.warn('Gerrit.getPluginName is not supported in PolyGerrit.',
        'Please use self.getPluginName() instead.');
  };

  Gerrit.css = function(rulesStr) {
    if (!Gerrit._customStyleSheet) {
      var styleEl = document.createElement('style');
      document.head.appendChild(styleEl);
      Gerrit._customStyleSheet = styleEl.sheet;
    }

    var name = '__pg_js_api_class_' + Gerrit._customStyleSheet.cssRules.length;
    Gerrit._customStyleSheet.insertRule('.' + name + '{' + rulesStr + '}', 0);
    return name;
  };

  Gerrit.install = function(callback, opt_version, opt_src) {
    if (opt_version && opt_version !== API_VERSION) {
      console.warn('Only version ' + API_VERSION +
          ' is supported in PolyGerrit. ' + opt_version + ' was given.');
      Gerrit._pluginInstalled();
      return;
    }

    // TODO(andybons): Polyfill currentScript for IE10/11 (edge supports it).
    var src = opt_src || (document.currentScript && document.currentScript.src);
    var plugin = new Plugin(src);
    try {
      callback(plugin);
    } catch (e) {
      console.warn(plugin.getPluginName() + ' install failed: ' +
          e.name + ': ' + e.message);
    }
    Gerrit._pluginInstalled();
  };

  Gerrit.getLoggedIn = function() {
    return document.createElement('gr-rest-api-interface').getLoggedIn();
  };

  /**
   * Polyfill GWT API dependencies to avoid runtime exceptions when loading
   * GWT-compiled plugins.
   * @deprecated Not supported in PolyGerrit.
   */
  Gerrit.installGwt = function() {
    Gerrit._pluginInstalled();
    return GWT_PLUGIN_STUB;
  };

  Gerrit._allPluginsPromise = null;
  Gerrit._resolveAllPluginsLoaded = null;

  Gerrit.awaitPluginsLoaded = function() {
    if (!Gerrit._allPluginsPromise) {
      if (Gerrit._arePluginsLoaded()) {
        Gerrit._allPluginsPromise = Promise.resolve();
      } else {
        Gerrit._allPluginsPromise = new Promise(function(resolve) {
          Gerrit._resolveAllPluginsLoaded = resolve;
        });
      }
    }
    return Gerrit._allPluginsPromise;
  };

  Gerrit._setPluginsCount = function(count) {
    Gerrit._pluginsPending = count;
    if (Gerrit._arePluginsLoaded()) {
      document.createElement('gr-reporting').pluginsLoaded();
      if (Gerrit._resolveAllPluginsLoaded) {
        Gerrit._resolveAllPluginsLoaded();
      }
    }
  };

  Gerrit._pluginInstalled = function() {
    Gerrit._setPluginsCount(Gerrit._pluginsPending - 1);
  };

  Gerrit._arePluginsLoaded = function() {
    return Gerrit._pluginsPending === 0;
  };

  window.Gerrit = Gerrit;
})(window);
