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
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';

export class GrEventHelper implements EventHelperPluginApi {
  constructor(
    private readonly reporting: ReportingService,
    readonly plugin: PluginApi,
    readonly element: HTMLElement
  ) {
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
    return this.listen(this.element, callback);
  }

  private listen(
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
            'GrEventHelper',
            new Error('event listener callback error'),
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
