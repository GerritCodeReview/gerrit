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

  class GrPluginHost extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-plugin-host'; }

    static get properties() {
      return {
        config: {
          type: Object,
          observer: '_configChanged',
        },
      };
    }

    _configChanged(config) {
      const plugins = config.plugin;
      const htmlPlugins = (plugins.html_resource_paths || []);
      const jsPlugins =
          this._handleMigrations(plugins.js_resource_paths || [], htmlPlugins);

      const shouldLoadTheme = config.default_theme &&
            !Gerrit._isPluginPreloaded('preloaded:gerrit-theme');
      const themeToLoad =
            shouldLoadTheme ? [config.default_theme] : [];

      // Theme should be loaded first if has one to have better UX
      const pluginsPending =
          themeToLoad.concat(jsPlugins, htmlPlugins);

      const pluginOpts = {};

      if (shouldLoadTheme) {
        // Theme needs to be loaded synchronous.
        pluginOpts[config.default_theme] = {sync: true};
      }

      Gerrit._loadPlugins(pluginsPending, pluginOpts);
    }

    /**
     * Omit .js plugins that have .html counterparts.
     * For example, if plugin provides foo.js and foo.html, skip foo.js.
     */
    _handleMigrations(jsPlugins, htmlPlugins) {
      return jsPlugins.filter(url => {
        const counterpart = url.replace(/\.js$/, '.html');
        return !htmlPlugins.includes(counterpart);
      });
    }
  }

  customElements.define(GrPluginHost.is, GrPluginHost);
})();
