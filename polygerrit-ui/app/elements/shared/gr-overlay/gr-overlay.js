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
(function() {
  'use strict';

  const AWAIT_MAX_ITERS = 10;
  const AWAIT_STEP = 5;
  const BREAKPOINT_FULLSCREEN_OVERLAY = '50em';

  Polymer({
    is: 'gr-overlay',

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

    properties: {
      _fullScreenOpen: {
        type: Boolean,
        value: false,
      },
    },

    behaviors: [
      Gerrit.FireBehavior,
      Polymer.IronOverlayBehavior,
    ],

    listeners: {
      'iron-overlay-closed': '_close',
      'iron-overlay-cancelled': '_close',
    },

    open(...args) {
      return new Promise(resolve => {
        Polymer.IronOverlayBehaviorImpl.open.apply(this, args);
        if (this._isMobile()) {
          this.fire('fullscreen-overlay-opened');
          this._fullScreenOpen = true;
        }
        this._awaitOpen(resolve);
      });
    },

    _isMobile() {
      return window.matchMedia(`(max-width: ${BREAKPOINT_FULLSCREEN_OVERLAY})`);
    },

    _close() {
      if (this._fullScreenOpen) {
        this.fire('fullscreen-overlay-closed');
        this._fullScreenOpen = false;
      }
    },

    /**
     * Override the focus stops that iron-overlay-behavior tries to find.
     */
    setFocusStops(stops) {
      this.__firstFocusableNode = stops.start;
      this.__lastFocusableNode = stops.end;
    },

    /**
     * NOTE: (wyatta) Slightly hacky way to listen to the overlay actually
     * opening. Eventually replace with a direct way to listen to the overlay.
     */
    _awaitOpen(fn) {
      let iters = 0;
      const step = () => {
        this.async(() => {
          if (this.style.display !== 'none') {
            fn.call(this);
          } else if (iters++ < AWAIT_MAX_ITERS) {
            step.call(this);
          }
        }, AWAIT_STEP);
      };
      step.call(this);
    },

    _id() {
      return this.getAttribute('id') || 'global';
    },
  });
})();
