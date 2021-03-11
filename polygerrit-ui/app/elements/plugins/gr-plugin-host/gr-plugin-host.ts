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
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {customElement, property} from '@polymer/decorators';
import {ServerInfo} from '../../../types/common';

@customElement('gr-plugin-host')
export class GrPluginHost extends LegacyElementMixin(PolymerElement) {
  @property({type: Object, observer: '_configChanged'})
  config?: ServerInfo;

  _configChanged(config: ServerInfo) {
    const plugins = config.plugin;
    const htmlPlugins = (plugins && plugins.html_resource_paths) || [];
    const jsPlugins = this._handleMigrations(
      (plugins && plugins.js_resource_paths) || [],
      htmlPlugins
    );
    const shouldLoadTheme =
      !!config.default_theme &&
      !getPluginLoader().isPluginPreloaded('preloaded:gerrit-theme');
    // config.default_theme is defined when shouldLoadTheme is true
    const themeToLoad: string[] = shouldLoadTheme
      ? [config.default_theme!]
      : [];

    // Theme should be loaded first if has one to have better UX
    const pluginsPending = themeToLoad.concat(jsPlugins, htmlPlugins);

    const pluginOpts: {[key: string]: {sync: boolean}} = {};

    if (shouldLoadTheme) {
      // config.default_theme is defined when shouldLoadTheme is true
      // Theme needs to be loaded synchronous.
      pluginOpts[config.default_theme!] = {sync: true};
    }

    getPluginLoader().loadPlugins(pluginsPending, pluginOpts);
  }

  /**
   * Omit .js plugins that have .html counterparts.
   * For example, if plugin provides foo.js and foo.html, skip foo.js.
   */
  _handleMigrations(jsPlugins: string[], htmlPlugins: string[]) {
    return jsPlugins.filter(url => {
      const counterpart = url.replace(/\.js$/, '.html');
      return !htmlPlugins.includes(counterpart);
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-host': GrPluginHost;
  }
}
