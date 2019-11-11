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
(function() {
  'use strict';

  class GrFixedPanel extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-fixed-panel'; }

    static get properties() {
      return {
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

        /**
       * Initial offset from the top of the document, in pixels.
       */
        _topInitial: Number,

        /**
       * Current offset from the top of the window, in pixels.
       */
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
      };
    }

    attached() {
      super.attached();
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
    }

    detached() {
      super.detached();
      this.unlisten(window, 'scroll', '_updateOnScroll');
      this.unlisten(window, 'resize', 'update');
      if (this._observer) {
        this._observer.disconnect();
      }
    }

    _readyForMeasureObserver(readyForMeasure) {
      if (readyForMeasure) {
        this.update();
      }
    }

    _computeHeaderClass(headerFloating, topLast) {
      const fixedAtTop = this.keepOnScroll && topLast === 0;
      return [
        headerFloating ? 'floating' : '',
        fixedAtTop ? 'fixedAtTop' : '',
      ].join(' ');
    }

    unfloat() {
      if (this.floatingDisabled) {
        return;
      }
      this.$.header.style.top = '';
      this._headerFloating = false;
      this.updateStyles({'--header-height': ''});
    }

    update() {
      this.debounce('update', () => {
        this._updateDebounced();
      }, 100);
    }

    _updateOnScroll() {
      this.debounce('update', () => {
        this._updateDebounced();
      });
    }

    _updateDebounced() {
      if (this.floatingDisabled) {
        return;
      }
      this._isMeasured = false;
      this._maybeFloatHeader();
      this._reposition();
    }

    _getElementTop() {
      return this.getBoundingClientRect().top;
    }

    _reposition() {
      if (!this._headerFloating) {
        return;
      }
      const header = this.$.header;
      // Since the outer element is relative positioned, can  use its top
      // to determine how to position the inner header element.
      const elemTop = this._getElementTop();
      let newTop;
      if (this.keepOnScroll && elemTop < 0) {
        // Should stick to the top.
        newTop = 0;
      } else {
        // Keep in line with the outer element.
        newTop = elemTop;
      }
      // Initialize top style if it doesn't exist yet.
      if (!header.style.top && this._topLast === newTop) {
        header.style.top = newTop;
      }
      if (this._topLast !== newTop) {
        if (newTop === undefined) {
          header.style.top = '';
        } else {
          header.style.top = newTop + 'px';
        }
        this._topLast = newTop;
      }
    }

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
    }

    _isFloatingNeeded() {
      return this.keepOnScroll ||
        document.body.scrollWidth > document.body.clientWidth;
    }

    _maybeFloatHeader() {
      if (!this._isFloatingNeeded()) {
        return;
      }
      this._measure();
      if (this._isMeasured) {
        this._floatHeader();
      }
    }

    _floatHeader() {
      this.updateStyles({'--header-height': this._headerHeight + 'px'});
      this._headerFloating = true;
    }
  }

  customElements.define(GrFixedPanel.is, GrFixedPanel);
})();
