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

  Polymer({
    is: 'gr-header',

    properties: {
      keepOnScroll: {
        type: Boolean,
      },
      _topInitial: Number,
      _topLast: Number,
      _headerHeight: Number,
    },

    attached: function() {
      var rect = this.$.header.getBoundingClientRect();
      this._topInitial = rect.top;
      this._topLast = rect.top;
      this._headerHeight = rect.height;
      this.customStyle['--header-height'] = rect.height + 'px';
      this.updateStyles();
      this.listen(window, 'scroll', '_handleScroll');
    },

    detached: function() {
      this.unlisten(window, 'scroll', '_handleScroll');
    },

    _getScrollY: function() {
      return window.scrollY;
    },

    _handleScrollDebounced: function() {
      var header = this.$.header;
      var scrollY = this._topInitial - this._getScrollY();
      var newTop;
      if (this.keepOnScroll) {
        if (scrollY > 0) {
          // Reposition to imitate natural scrolling.
          newTop = scrollY;
        } else {
          newTop = 0;
        }
      } else if (scrollY > -this._headerHeight ||
          this._topLast < -this._headerHeight) {
        // Allow to scroll away, but ignore when far behind the edge.
        newTop = scrollY;
      } else {
        newTop = -this._headerHeight;
      }
      if (this._topLast !== newTop) {
        if (newTop === undefined) {
          header.style.top = '';
        } else {
          header.style.top = newTop + 'px';
        }
        this._topLast = newTop;
      }
    },

    _handleScroll: function() {
      this.debounce('scroll', this._handleScrollDebounced);
    },
  });
})();
