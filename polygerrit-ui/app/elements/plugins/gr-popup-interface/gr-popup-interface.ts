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
import './gr-plugin-popup';
import {dom, flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GrPluginPopup} from './gr-plugin-popup';
import {PluginApi} from '../../../api/plugin';
import {PopupPluginApi} from '../../../api/popup';

interface CustomPolymerPluginEl extends HTMLElement {
  plugin: PluginApi;
}

/**
 * Plugin popup API.
 * Provides method for opening and closing popups from plugin.
 * opt_moduleName is a name of custom element that will be automatically
 * inserted on popup opening.
 */
export class GrPopupInterface implements PopupPluginApi {
  private _openingPromise: Promise<GrPopupInterface> | null = null;

  private _popup: GrPluginPopup | null = null;

  constructor(
    readonly plugin: PluginApi,
    private _moduleName: string | null = null
  ) {}

  _getElement() {
    // TODO(TS): maybe consider removing this if no one is using
    // anything other than native methods on the return
    return (dom(this._popup) as unknown) as HTMLElement;
  }

  /**
   * Opens the popup, inserts it into DOM over current UI.
   * Creates the popup if not previously created. Creates popup content element,
   * if it was provided with constructor.
   */
  open(): Promise<PopupPluginApi> {
    if (!this._openingPromise) {
      this._openingPromise = this.plugin
        .hook('plugin-overlay')
        .getLastAttached()
        .then(hookEl => {
          const popup = document.createElement('gr-plugin-popup');
          if (this._moduleName) {
            const el = popup.appendChild(
              document.createElement(this._moduleName) as CustomPolymerPluginEl
            );
            el.plugin = this.plugin;
          }
          this._popup = hookEl.appendChild(popup);
          flush();
          return this._popup.open().then(() => this);
        });
    }
    return this._openingPromise;
  }

  /**
   * Hides the popup.
   */
  close() {
    if (!this._popup) {
      return;
    }
    this._popup.close();
    this._openingPromise = null;
  }
}
