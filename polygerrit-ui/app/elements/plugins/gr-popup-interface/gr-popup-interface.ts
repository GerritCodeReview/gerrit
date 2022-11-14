/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './gr-plugin-popup';
import {GrPluginPopup} from './gr-plugin-popup';
import {PluginApi} from '../../../api/plugin';
import {PopupPluginApi} from '../../../api/popup';
import {getAppContext} from '../../../services/app-context';

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

  private readonly reporting = getAppContext().reportingService;

  constructor(
    readonly plugin: PluginApi,
    private moduleName: string | null = null
  ) {
    this.reporting.trackApi(this.plugin, 'popup', 'constructor');
  }

  // TODO: This method should be removed as soon as plugins stop
  // depending on it.
  _getElement() {
    return this.popup;
  }

  appendContent(el: HTMLElement) {
    if (!this.popup) throw new Error('popup element not (yet) available');
    this.popup.appendChild(el);
  }

  /**
   * Opens the popup, inserts it into DOM over current UI.
   * Creates the popup if not previously created. Creates popup content element,
   * if it was provided with constructor.
   */
  open(): Promise<GrPopupInterface> {
    this.reporting.trackApi(this.plugin, 'popup', 'open');
    if (!this.openingPromise) {
      this.openingPromise = this.plugin
        .hook('plugin-overlay')
        .getLastAttached()
        .then(async hookEl => {
          const popup = document.createElement('gr-plugin-popup');
          if (this.moduleName) {
            const el = popup.appendChild(
              document.createElement(this.moduleName) as CustomPolymerPluginEl
            );
            el.plugin = this.plugin;
          }
          this.popup = hookEl.appendChild(popup);
          await this.popup.updateComplete;
          this.popup.open();
          return this;
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
