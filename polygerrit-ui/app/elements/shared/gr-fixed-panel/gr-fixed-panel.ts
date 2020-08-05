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
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-fixed-panel_html';
import {customElement, property, observe} from '@polymer/decorators';

export interface GrFixedPanel {
  $: {
    header: Element;
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-fixed-panel': GrFixedPanel;
  }
}

@customElement('gr-fixed-panel')

/** @extends PolymerElement */
export class GrFixedPanel extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean})
  floatingDisabled = false;

  @property({type: Boolean})
  readyForMeasure = false;

  @property({type: Boolean})
  keepOnScroll = false;

  @property({type: Boolean})
  _isMeasured = false;

  /**
   * Initial offset from the top of the document, in pixels.
   */
  @property({type: Number})
  _topInitial: number | null = null;

  /**
   * Current offset from the top of the window, in pixels.
   */
  @property({type: Number})
  _topLast: number | null = null;

  @property({type: Number})
  _headerHeight: number | null = null;

  @property({type: Boolean})
  _headerFloating = false;

  @property({type: Object})
  _observer: any = null;

  /**
   * If place before any other content defines how much
   * of the content below it is covered by this panel
   */
  @property({type: Number, notify: true})
  floatingHeight = 0;

  @property({type: Boolean})
  _webComponentsReady = false;

  static get observers() {
    return [
      '_updateFloatingHeight(floatingDisabled, _isMeasured, _headerHeight)',
    ];
  }

  _updateFloatingHeight(floatingDisabled: boolean, isMeasured: boolean, headerHeight: number) {
    if (
      [floatingDisabled, isMeasured, headerHeight].some(
        arg => arg === undefined
      )
    ) {
      return;
    }
    this.floatingHeight = !floatingDisabled && isMeasured ? headerHeight : 0;
  }

  /** @override */
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

  /** @override */
  detached() {
    super.detached();
    this.unlisten(window, 'scroll', '_updateOnScroll');
    this.unlisten(window, 'resize', 'update');
    if (this._observer) {
      this._observer.disconnect();
    }
  }

  @observe('readyForMeasure')
  _readyForMeasureObserver(readyForMeasure: boolean) {
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
    this.debounce(
      'update',
      () => {
        this._updateDebounced();
      },
      100
    );
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
    return (
      this.keepOnScroll || document.body.scrollWidth > document.body.clientWidth
    );
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
