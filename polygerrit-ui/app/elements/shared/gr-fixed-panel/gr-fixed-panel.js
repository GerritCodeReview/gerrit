// Copyright (C) 2017 The Android Open Source Project
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
    is: 'gr-fixed-panel',

    properties: {
      floatingDisabled: Boolean,
      readyForMeasure: {
        type: Boolean,
        observer: '_readyForMeasureObserver',
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
      _observer: {
        type: Object,
        value: null,
      },
      _webComponentsReady: Boolean,
    },

    attached() {
      if (this.floatingDisabled) {
        return;
      }
      // Enable content measure unless blocked by param.
      if (this.readyForMeasure !== false) {
        this.readyForMeasure = true;
      }
      this.listen(window, 'resize', 'update');
      this.listen(window, 'scroll', '_updateOnScroll');
      this._observer = new MutationObserver(this.update.bind(this));
      this._observer.observe(this.$.header, {childList: true, subtree: true});
    },

    detached() {
      this.unlisten(window, 'scroll', '_updateOnScroll');
      this.unlisten(window, 'resize', 'update');
      if (this._observer) {
        this._observer.disconnect();
      }
    },

    _readyForMeasureObserver(readyForMeasure) {
      if (readyForMeasure) {
        this.update();
      }
    },

    _computeHeaderClass(headerFloating) {
      return headerFloating ? 'floating' : '';
    },

    _getScrollY() {
      return window.scrollY;
    },

    unfloat() {
      if (this.floatingDisabled) {
        return;
      }
      this.$.header.style.top = '';
      this._headerFloating = false;
      this.customStyle['--header-height'] = '';
      this.updateStyles();
    },

    update() {
      this.debounce('update', () => {
        this._updateDebounced();
      }, 100);
    },

    _updateOnScroll() {
      this.debounce('update', () => {
        this._updateDebounced();
      });
    },

    _updateDebounced() {
      if (this.floatingDisabled) {
        return;
      }
      this._isMeasured = false;
      this._maybeFloatHeader();
      this._reposition();
    },

    _reposition() {
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
      this._topLast = top;
      this._headerHeight = rect.height;
      this._topInitial =
        this.getBoundingClientRect().top + document.body.scrollTop;
      this._isMeasured = true;
    },

    _isFloatingNeeded() {
      return this.keepOnScroll ||
        document.body.scrollWidth > document.body.clientWidth;
    },

    _maybeFloatHeader() {
      if (!this._isFloatingNeeded()) {
        return;
      }
      this._measure();
      if (this._isMeasured) {
        this._floatHeader();
      }
    },

    _floatHeader() {
      this.customStyle['--header-height'] = this._headerHeight + 'px';
      this.updateStyles();
      this._headerFloating = true;
    },
  });
})();
