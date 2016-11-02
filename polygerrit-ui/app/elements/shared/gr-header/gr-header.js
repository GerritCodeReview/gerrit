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
      readyForMeasure: {
        type: Boolean,
        observer: '_maybeFloatHeader',
      },
      keepOnScroll: {
        type: Boolean,
        value: false,
      },
      _topInitial: Number,
      _topLast: Number,
      _headerHeight: Number,
      _headerFloating: {
        type: Boolean,
        value: false,
      },
    },

    attached: function() {
      // Wrap in async to wait for wrapped content to be attached as well.
      this.async(function() {
        var rect = this.$.header.getBoundingClientRect();
        this._topInitial = rect.top;
        this._topLast = rect.top;
        // Enable content measure unless blocked by param.
        if (this.readyForMeasure !== false) {
          this.readyForMeasure = true;
        }
      });
      this.listen(window, 'scroll', '_handleScroll');
      this.listen(window, 'resize', '_handleResize');
    },

    detached: function() {
      this.unlisten(window, 'scroll', '_handleScroll');
      this.unlisten(window, 'resize', '_handleResize');
    },

    _computeHeaderClass: function(headerFloating) {
      return headerFloating ? 'floating' : '';
    },

    _getScrollY: function() {
      return window.scrollY;
    },

    _handleResize: function() {
      if (this._headerFloating) {
        this._unfloatHeader();
      }
      this.debounce('resize', function() {
        this._maybeFloatHeader();
        this._handleScrollDebounced();
      }, 10);
    },

    _handleScroll: function() {
      this._maybeFloatHeader();
      this.debounce('scroll', this._handleScrollDebounced);
    },

    _handleScrollDebounced: function() {
      if (!this._headerFloating) {
        return;
      }
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

    _maybeFloatHeader: function() {
      if (this._headerFloating || !this.readyForMeasure) {
        return;
      }
      this._floatHeader();
    },

    _unfloatHeader: function() {
      this.customStyle['--header-height'] = '';
      this._headerFloating = false;
      this.updateStyles();
    },

    _floatHeader: function() {
      var rect = this.$.header.getBoundingClientRect();
      this._headerHeight = rect.height;
      this.customStyle['--header-height'] = rect.height + 'px';
      this._headerFloating = true;
      this.updateStyles();
    },
  });
})();
