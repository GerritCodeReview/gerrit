// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function() {
  'use strict';

  var AWAIT_MAX_ITERS = 10;
  var AWAIT_STEP = 5;

  Polymer({
    is: 'gr-overlay',

    behaviors: [
      Polymer.IronOverlayBehavior,
    ],

    open: function() {
      return new Promise(function(resolve) {
        Polymer.IronOverlayBehaviorImpl.open.apply(this, arguments);
        this._awaitOpen(resolve);
      }.bind(this));
    },

    /**
     * Override the focus stops that iron-overlay-behavior tries to find.
     */
    setFocusStops: function(stops) {
      this.__firstFocusableNode = stops.start;
      this.__lastFocusableNode = stops.end;
    },

    /**
     * NOTE: (wyatta) Slightly hacky way to listen to the overlay actually
     * opening. Eventually replace with a direct way to listen to the overlay.
     */
    _awaitOpen: function(fn) {
      var iters = 0;
      var step = function() {
        this.async(function() {
          if (this.style.display !== 'none') {
            fn.call(this);
          } else if (iters++ < AWAIT_MAX_ITERS) {
            step.call(this);
          }
        }.bind(this), AWAIT_STEP);
      }.bind(this);
      step.call(this);
    },

    _id: function() {
      return this.getAttribute('id') || 'global';
    },
  });
})();
