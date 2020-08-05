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
import {htmlTemplate} from './gr-page-nav_html';
import {customElement, property} from '@polymer/decorators';

@customElement('gr-page-nav')
export class GrPageNav extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Number})
  _headerHeight?: number;

  private readonly bodyScrollHandler: () => void;

  constructor() {
    super();
    this.bodyScrollHandler = () => this._handleBodyScroll();
  }

  attached() {
    super.attached();
    window.addEventListener('scroll', this.bodyScrollHandler);
  }

  detached() {
    super.detached();
    window.removeEventListener('scroll', this.bodyScrollHandler);
  }

  _handleBodyScroll() {
    if (this._headerHeight === undefined) {
      let top = this._getOffsetTop(this);
      // TODO(TS): Element doesn't have offsetParent,
      // while `offsetParent` are returning Element not HTMLElement
      for (
        let offsetParent = this.offsetParent as HTMLElement | undefined;
        offsetParent;
        offsetParent = this._getOffsetParent(offsetParent)
      ) {
        top += this._getOffsetTop(offsetParent);
      }
      this._headerHeight = top;
    }

    this.$.nav.classList.toggle(
      'pinned',
      this._getScrollY() >= (this._headerHeight || 0)
    );
  }

  /* Functions used for test purposes */
  _getOffsetParent(element?: HTMLElement) {
    if (!element || !('offsetParent' in element)) {
      return undefined;
    }
    return element.offsetParent as HTMLElement;
  }

  _getOffsetTop(element: HTMLElement) {
    return element.offsetTop;
  }

  _getScrollY() {
    return window.scrollY;
  }
}
