/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import {IronOverlayBehaviorImpl} from '@polymer/iron-overlay-behavior/iron-overlay-behavior.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-overlay_html.js';
import {IronOverlayMixin} from '../../../mixins/iron-overlay-mixin/iron-overlay-mixin.js';

const AWAIT_MAX_ITERS = 10;
const AWAIT_STEP = 5;
const BREAKPOINT_FULLSCREEN_OVERLAY = '50em';

/**
 * @extends PolymerElement
 */
class GrOverlay extends IronOverlayMixin(GestureEventListeners(
    LegacyElementMixin(PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-overlay'; }
  /**
   * Fired when a fullscreen overlay is closed
   *
   * @event fullscreen-overlay-closed
   */

  /**
   * Fired when an overlay is opened in full screen mode
   *
   * @event fullscreen-overlay-opened
   */

  static get properties() {
    return {
      _fullScreenOpen: {
        type: Boolean,
        value: false,
      },
    };
  }

  /** @override */
  created() {
    this._boundHandleClose = () => this._close();
    super.created();
    this.addEventListener('iron-overlay-closed',
        () => this._close());
    this.addEventListener('iron-overlay-cancelled',
        () => this._close());
  }

  open(...args) {
    window.addEventListener('popstate', this._boundHandleClose);
    return new Promise((resolve, reject) => {
      IronOverlayBehaviorImpl.open.apply(this, args);
      if (this._isMobile()) {
        this.dispatchEvent(new CustomEvent('fullscreen-overlay-opened', {
          composed: true, bubbles: true,
        }));
        this._fullScreenOpen = true;
      }
      this._awaitOpen(resolve, reject);
    });
  }

  _isMobile() {
    return window.matchMedia(`(max-width: ${BREAKPOINT_FULLSCREEN_OVERLAY})`);
  }

  _close() {
    window.removeEventListener('popstate', this._boundHandleClose);
    if (this._fullScreenOpen) {
      this.dispatchEvent(new CustomEvent('fullscreen-overlay-closed', {
        composed: true, bubbles: true,
      }));
      this._fullScreenOpen = false;
    }
  }

  /**
   * Override the focus stops that iron-overlay-behavior tries to find.
   */
  setFocusStops(stops) {
    this.__firstFocusableNode = stops.start;
    this.__lastFocusableNode = stops.end;
  }

  /**
   * NOTE: (wyatta) Slightly hacky way to listen to the overlay actually
   * opening. Eventually replace with a direct way to listen to the overlay.
   */
  _awaitOpen(fn, reject) {
    let iters = 0;
    const step = () => {
      this.async(() => {
        if (this.style.display !== 'none') {
          fn.call(this);
        } else if (iters++ < AWAIT_MAX_ITERS) {
          step.call(this);
        } else {
          reject(new Error('gr-overlay _awaitOpen failed to resolve'));
        }
      }, AWAIT_STEP);
    };
    step.call(this);
  }

  _id() {
    return this.getAttribute('id') || 'global';
  }
}

customElements.define(GrOverlay.is, GrOverlay);
