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
    // private but used in tests
    readonly moduleName: string | null = null
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
          // Pressing esc closes the dialog, but keeps the element in the
          // tree. This will never be used again because plugin.popup() will
          // just create a new element. We need to remove the element as not
          // to waste resources.
          popup.addEventListener('keydown', (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
              this.close();
            }
          });
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
    // We have to call .remove() otherwise a new element is created
    // each time this class is called with open(). Rather than openening
    // the existing element. Leading to wasted resources.
    this.popup.remove();
    this.popup = null;
    this.openingPromise = null;
  }
}
