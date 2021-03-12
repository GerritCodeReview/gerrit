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
import {
  EventHelperPluginApi,
  UnsubscribeCallback,
} from '../../../api/event-helper';
import {PluginApi} from '../../../api/plugin';
import {appContext} from '../../../services/app-context';

export class GrEventHelper implements EventHelperPluginApi {
  private readonly reporting = appContext.reportingService;

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
        } catch (exception) {
          console.warn(`Plugin error handing event: ${exception}`);
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
