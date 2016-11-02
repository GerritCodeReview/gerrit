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
      _isMeasured: {
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

    attached() {
      // Enable content measure unless blocked by param.
      this.async(() => {
        if (this.readyForMeasure !== false) {
          this.readyForMeasure = true;
        }
      }, 1);
      this.listen(window, 'scroll', '_handleScroll');
      this.listen(window, 'resize', '_handleResize');
    },

    detached() {
      this.unlisten(window, 'scroll', '_handleScroll');
      this.unlisten(window, 'resize', '_handleResize');
    },

    _computeHeaderClass(headerFloating) {
      return headerFloating ? 'floating' : '';
    },

    _getScrollY() {
      return window.scrollY;
    },

    _handleResize() {
      if (this._headerFloating) {
        this._unfloatHeader();
      }
      this.debounce('resize', () => {
        this._maybeFloatHeader();
        this._handleScrollDebounced();
      }, 100);
    },

    _handleScroll() {
      this._maybeFloatHeader();
      this.debounce('scroll', this._handleScrollDebounced);
    },

    _handleScrollDebounced() {
      if (!this._headerFloating) {
        return;
      }
      const header = this.$.header;
      const scrollY = this._topInitial - this._getScrollY();
      let newTop;
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

    _measure() {
      if (this._isMeasured) {
        return; // Already measured.
      }
      const rect = this.$.header.getBoundingClientRect();
      if (rect.height === 0 && rect.width === 0) {
        return; // Not ready for measurement yet.
      }
      const top = document.body.scrollTop + rect.top;
      this._topInitial = top;
      this._topLast = top;
      this._isMeasured = true;
    },

    _maybeFloatHeader() {
      if (!this.readyForMeasure) {
        return;
      }
      this._measure();
      if (!this._headerFloating && this._isMeasured) {
        this._floatHeader();
      }
    },

    _unfloatHeader() {
      this.customStyle['--header-height'] = '';
      this._headerFloating = false;
      this.updateStyles();
    },

    _floatHeader() {
      const rect = this.$.header.getBoundingClientRect();
      this._headerHeight = rect.height;
      this.customStyle['--header-height'] = rect.height + 'px';
      this._headerFloating = true;
      this.updateStyles();
    },
  });
})();
