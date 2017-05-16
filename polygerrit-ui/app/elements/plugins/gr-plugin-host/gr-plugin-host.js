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
    is: 'gr-plugin-host',

    properties: {
      config: {
        type: Object,
        observer: '_configChanged',
      },
    },

    _configChanged(config) {
      const jsPlugins = config.js_resource_paths || [];
      const htmlPlugins = config.html_resource_paths || [];
      Gerrit._setPluginsCount(jsPlugins.length + htmlPlugins.length);
      this._loadJsPlugins(jsPlugins);
      this._importHtmlPlugins(htmlPlugins);
    },

    _importHtmlPlugins(plugins) {
      for (let url of plugins) {
        if (!url.startsWith('http')) {
          url = '/' + url;
        }
        this.importHref(
            url, Gerrit._pluginInstalled, Gerrit._pluginInstalled, true);
      }
    },

    _loadJsPlugins(plugins) {
      for (let i = 0; i < plugins.length; i++) {
        const scriptEl = document.createElement('script');
        scriptEl.defer = true;
        scriptEl.src = '/' + plugins[i];
        scriptEl.onerror = Gerrit._pluginInstalled;
        document.body.appendChild(scriptEl);
      }
    },
  });
})();
