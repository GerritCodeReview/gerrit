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
import '../../../styles/shared-styles';
import '../../shared/gr-tooltip/gr-tooltip';
import {GrTooltip} from '../../shared/gr-tooltip/gr-tooltip';
import {customElement, property} from '@polymer/decorators';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-selection-action-box_html';
import {fireEvent} from '../../../utils/event-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-selection-action-box': GrSelectionActionBox;
  }
}

export interface GrSelectionActionBox {
  $: {
    tooltip: GrTooltip;
  };
}

@customElement('gr-selection-action-box')
export class GrSelectionActionBox extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the comment creation action was taken (click).
   *
   * @event create-comment-requested
   */

  @property({type: Boolean})
  positionBelow = false;

  constructor() {
    super();
    // See https://crbug.com/gerrit/4767
    this.addEventListener('mousedown', e => this._handleMouseDown(e));
  }

  async placeAbove(el: Text | Element | Range) {
    await this.$.tooltip.updateComplete;
    const rect = this._getTargetBoundingRect(el);
    const boxRect = this.$.tooltip.getBoundingClientRect();
    const parentRect = this._getParentBoundingClientRect();
    if (parentRect === null) {
      return;
    }
    this.style.top = `${rect.top - parentRect.top - boxRect.height - 6}px`;
    this.style.left = `${
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2
    }px`;
  }

  async placeBelow(el: Text | Element | Range) {
    await this.$.tooltip.updateComplete;
    const rect = this._getTargetBoundingRect(el);
    const boxRect = this.$.tooltip.getBoundingClientRect();
    const parentRect = this._getParentBoundingClientRect();
    if (parentRect === null) {
      return;
    }
    this.style.top = `${rect.top - parentRect.top + boxRect.height - 6}px`;
    this.style.left = `${
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2
    }px`;
  }

  private _getParentBoundingClientRect() {
    // With native shadow DOM, the parent is the shadow root, not the gr-diff
    // element
    if (this.parentElement) {
      return this.parentElement.getBoundingClientRect();
    }
    if (this.parentNode !== null) {
      return (this.parentNode as ShadowRoot).host.getBoundingClientRect();
    }
    return null;
  }

  private _getTargetBoundingRect(el: Text | Element | Range) {
    let rect;
    if (el instanceof Text) {
      const range = document.createRange();
      range.selectNode(el);
      rect = range.getBoundingClientRect();
      range.detach();
    } else {
      rect = el.getBoundingClientRect();
    }
    return rect;
  }

  private _handleMouseDown(e: MouseEvent) {
    if (e.button !== 0) {
      return;
    } // 0 = main button
    e.preventDefault();
    e.stopPropagation();
    fireEvent(this, 'create-comment-requested');
  }
}
