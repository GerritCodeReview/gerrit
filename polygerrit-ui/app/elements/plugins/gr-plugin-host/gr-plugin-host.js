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
      const htmlPlugins = plugins.html_resource_paths || [];
      const jsPlugins =
          this._handleMigrations(plugins.js_resource_paths || [], htmlPlugins);
      const defaultTheme = config.default_theme;
      const pluginsPending =
            [].concat(jsPlugins, htmlPlugins, defaultTheme || []).map(
                p => this._urlFor(p));
      Gerrit._setPluginsPending(pluginsPending);
      if (defaultTheme) {
        // Make theme first to be first to load.
        // Load sync to work around rare theme loading race condition.
        this._importHtmlPlugins([defaultTheme], true);
      }
      this._loadJsPlugins(jsPlugins);
      this._importHtmlPlugins(htmlPlugins);
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

    /**
     * @suppress {checkTypes}
     * States that it expects no more than 3 parameters, but that's not true.
     * @todo (beckysiegel) check Polymer annotations and submit change.
     * @param {Array} plugins
     * @param {boolean=} opt_sync
     */
    _importHtmlPlugins(plugins, opt_sync) {
      const async = !opt_sync;
      for (const url of plugins) {
        // onload (second param) needs to be a function. When null or undefined
        // were passed, plugins were not loaded correctly.
        this.importHref(
            this._urlFor(url), () => {},
            Gerrit._pluginInstallError.bind(null, `${url} import error`),
            async);
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
      el.onerror = Gerrit._pluginInstallError.bind(null, `${url} load error`);
      return document.body.appendChild(el);
    },

    _urlFor(pathOrUrl) {
      if (pathOrUrl.startsWith('http')) {
        // Plugins are loaded from another domain.
        return pathOrUrl;
      }
      if (!pathOrUrl.startsWith('/')) {
        pathOrUrl = '/' + pathOrUrl;
      }
      const {href, pathname} = window.location;
      return href.split(pathname)[0] + this.getBaseUrl() + pathOrUrl;
    },
  });
})();
