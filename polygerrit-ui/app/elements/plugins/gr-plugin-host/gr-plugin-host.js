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

    behaviors: [
      Gerrit.BaseUrlBehavior,
    ],

    _configChanged(config) {
      const jsPlugins = config.js_resource_paths || [];
      const htmlPlugins = config.html_resource_paths || [];
      const defaultTheme = config.default_theme;
      if (defaultTheme) {
        htmlPlugins.unshift(defaultTheme);
      }
      Gerrit._setPluginsCount(jsPlugins.length + htmlPlugins.length);
      this._loadJsPlugins(jsPlugins);
      this._importHtmlPlugins(htmlPlugins);
    },

    _importHtmlPlugins(plugins) {
      for (const url of plugins) {
        this.importHref(
            this._urlFor(url), Gerrit._pluginInstalled, Gerrit._pluginInstalled,
            true);
      }
    },

    _loadJsPlugins(plugins) {
      for (const url of plugins) {
        this._createScriptTag(this._urlFor(url));
      }
    },

    _createScriptTag(url) {
      const el = document.createElement('script');
      el.defer = true;
      el.src = url;
      el.onerror = Gerrit._pluginInstalled;
      return document.body.appendChild(el);
    },

    _urlFor(pathOrUrl) {
      if (pathOrUrl.startsWith('http')) {
        return pathOrUrl;
      }
      if (!pathOrUrl.startsWith('/')) {
        pathOrUrl = '/' + pathOrUrl;
      }
      return this.getBaseUrl() + pathOrUrl;
    },
  });
})();
