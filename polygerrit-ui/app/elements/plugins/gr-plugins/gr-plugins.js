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
(function() {
  'use strict';

  Polymer({
    is: 'gr-plugins',

    properties: {
      config: {
        type: Object,
        observer: "_configChanged",
      },
    },

    _configChanged: function(config) {
      var jsPlugins = config.js_resource_paths || [];
      var htmlPlugins = config.html_resource_paths || [];
      Gerrit._setPluginsCount(jsPlugins.length + htmlPlugins.length);
      this._loadJsPlugins(jsPlugins);
      this._importHtmlPlugins(htmlPlugins);
    },

    _importHtmlPlugins: function(plugins) {
      plugins.forEach(function(url) {
        this.importHref('/' + url, null, Gerrit._pluginInstalled, true);
      }.bind(this));
    },

    _loadJsPlugins: function(plugins) {
      for (var i = 0; i < plugins.length; i++) {
        var url = plugins[i];
        var scriptEl = document.createElement('script');
        scriptEl.defer = true;
        scriptEl.src = '/' + plugins[i];
        scriptEl.onerror = Gerrit._pluginInstalled;
        document.body.appendChild(scriptEl);
      }
    },
  });
})();
