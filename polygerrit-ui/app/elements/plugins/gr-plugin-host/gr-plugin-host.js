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
(function() {
  'use strict';

  Polymer({
    is: 'gr-plugin-host',
    _legacyUndefinedCheck: true,

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
      const plugins = config.plugin;
      const htmlPlugins = (plugins.html_resource_paths || [])
          .map(p => this._urlFor(p))
          .filter(p => !Gerrit._isPluginPreloaded(p));
      const jsPlugins =
          this._handleMigrations(plugins.js_resource_paths || [], htmlPlugins)
          .map(p => this._urlFor(p))
          .filter(p => !Gerrit._isPluginPreloaded(p));
      const shouldLoadTheme = config.default_theme &&
            !Gerrit._isPluginPreloaded('preloaded:gerrit-theme');
      const defaultTheme =
            shouldLoadTheme ? this._urlFor(config.default_theme) : null;
      
      // If theme exists, load it first.
      const pluginsPending =
          (defaultTheme ? [defaultTheme] : []).concat(jsPlugins, htmlPlugins);

      const pluginOpts = {};
      if (defaultTheme) {
        // Theme needs to be loaded synchronous.
        pluginOpts[defaultTheme] = {sync: true};
      }

      Gerrit.loadPlugins(pluginsPending, pluginOpts);
    },

    /**
     * Omit .js plugins that have .html counterparts.
     * For example, if plugin provides foo.js and foo.html, skip foo.js.
     */
    _handleMigrations(jsPlugins, htmlPlugins) {
      return jsPlugins.filter(url => {
        const counterpart = url.replace(/\.js$/, '.html');
        return !htmlPlugins.includes(counterpart);
      });
    },

    _urlFor(pathOrUrl) {
      if (!pathOrUrl) {
        return pathOrUrl;
      }
      if (pathOrUrl.startsWith('preloaded:') ||
          pathOrUrl.startsWith('http')) {
        // Plugins are loaded from another domain or preloaded.
        return pathOrUrl;
      }
      if (!pathOrUrl.startsWith('/')) {
        pathOrUrl = '/' + pathOrUrl;
      }
      return window.location.origin + this.getBaseUrl() + pathOrUrl;
    },
  });
})();
