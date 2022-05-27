/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  EventHelperPluginApi,
  UnsubscribeCallback,
} from '../../../api/event-helper';
import {PluginApi} from '../../../api/plugin';
import {getAppContext} from '../../../services/app-context';

export class GrEventHelper implements EventHelperPluginApi {
  private readonly reporting = getAppContext().reportingService;

  constructor(readonly plugin: PluginApi, readonly element: HTMLElement) {
    this.reporting.trackApi(this.plugin, 'event', 'constructor');
  }

  /**
   * Alias for @see onClick
   */
  onTap(callback: (event: Event) => boolean) {
    this.reporting.trackApi(this.plugin, 'event', 'onTap');
    return this.onClick(callback);
  }

  /**
   * Add a callback to element click or touch.
   * The callback may return false to prevent event bubbling.
   */
  onClick(callback: (event: Event) => boolean) {
    this.reporting.trackApi(this.plugin, 'event', 'onClick');
    return this._listen(this.element, callback);
  }

  _listen(
    container: HTMLElement,
    callback: (event: Event) => boolean
  ): UnsubscribeCallback {
    const handler = (e: Event) => {
      const path = e.composedPath();
      if (!path) return;
      if (path.indexOf(this.element) !== -1) {
        let mayContinue = true;
        try {
          mayContinue = callback(e);
        } catch (exception: unknown) {
          this.reporting.error(
            new Error('event listener callback error'),
            undefined,
            exception
          );
        }
        if (mayContinue === false) {
          e.stopImmediatePropagation();
          e.stopPropagation();
          e.preventDefault();
        }
      }
    };
    container.addEventListener('click', handler);
    const unsubscribe = () => container.removeEventListener('click', handler);
    return unsubscribe;
  }
}
