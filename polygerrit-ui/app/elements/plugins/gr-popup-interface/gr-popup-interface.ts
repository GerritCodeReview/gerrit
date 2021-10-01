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
import {appContext} from '../../../services/app-context';

interface CustomPolymerPluginEl extends HTMLElement {
  plugin: PluginApi;
}

/**
 * Plugin popup API.
 * Provides method for opening and closing popups from plugin.
 * optmoduleName is a name of custom element that will be automatically
 * inserted on popup opening.
 */
export class GrPopupInterface implements PopupPluginApi {
  private openingPromise: Promise<GrPopupInterface> | null = null;

  private popup: GrPluginPopup | null = null;

  private readonly reporting = appContext.reportingService;

  constructor(
    readonly plugin: PluginApi,
    private moduleName: string | null = null
  ) {
    this.reporting.trackApi(this.plugin, 'popup', 'constructor');
  }

  _getElement() {
    // TODO(TS): maybe consider removing this if no one is using
    // anything other than native methods on the return
    return dom(this.popup) as unknown as HTMLElement;
  }

  setContent(el: HTMLElement) {
    if (!this.popup) throw new Error('popup element not (yet) available');
    this.popup.appendChild(el);
  }

  /**
   * Opens the popup, inserts it into DOM over current UI.
   * Creates the popup if not previously created. Creates popup content element,
   * if it was provided with constructor.
   */
  open(): Promise<PopupPluginApi> {
    this.reporting.trackApi(this.plugin, 'popup', 'open');
    if (!this.openingPromise) {
      this.openingPromise = this.plugin
        .hook('plugin-overlay')
        .getLastAttached()
        .then(hookEl => {
          const popup = document.createElement('gr-plugin-popup');
          if (this.moduleName) {
            const el = popup.appendChild(
              document.createElement(this.moduleName) as CustomPolymerPluginEl
            );
            el.plugin = this.plugin;
          }
          this.popup = hookEl.appendChild(popup);
          flush();
          return this.popup.open().then(() => this);
        });
    }
    return this.openingPromise;
  }

  /**
   * Hides the popup.
   */
  close() {
    this.reporting.trackApi(this.plugin, 'popup', 'close');
    if (!this.popup) {
      return;
    }
    this.popup.close();
    this.openingPromise = null;
  }
}
