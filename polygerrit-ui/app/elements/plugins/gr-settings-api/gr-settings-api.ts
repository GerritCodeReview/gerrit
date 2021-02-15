/**
 * @license
 * Copyright (C) 2017 The Android Open Source Settings
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
import '../../settings/gr-settings-view/gr-settings-item';
import '../../settings/gr-settings-view/gr-settings-menu-item';
import {PluginApi} from '../../../api/plugin';
import {SettingsPluginApi} from '../../../api/settings';

export class GrSettingsApi implements SettingsPluginApi {
  private _token: string;

  private _title = '(no title)';

  private _moduleName?: string;

  constructor(readonly plugin: PluginApi) {
    // Generate default screen URL token, specific to plugin, and unique(ish).
    this._token = plugin.getPluginName() + Math.random().toString(36).substr(5);
  }

  title(newTitle: string) {
    this._title = newTitle;
    return this;
  }

  token(newToken: string) {
    this._token = newToken;
    return this;
  }

  module(newModuleName: string) {
    this._moduleName = newModuleName;
    return this;
  }

  build() {
    if (!this._moduleName) {
      throw new Error('Settings screen custom element not defined!');
    }
    const token = `x/${this.plugin.getPluginName()}/${this._token}`;
    this.plugin.hook('settings-menu-item').onAttached(el => {
      const menuItem = document.createElement('gr-settings-menu-item');
      menuItem.title = this._title;
      menuItem.setAttribute('href', `#${token}`);
      el.appendChild(menuItem);
    });
    const moduleName = this._moduleName;
    return this.plugin.hook('settings-screen').onAttached(el => {
      const item = document.createElement('gr-settings-item');
      item.title = this._title;
      item.anchor = token;
      item.appendChild(document.createElement(moduleName));
      el.appendChild(item);
    });
  }
}
