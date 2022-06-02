/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../elements/shared/gr-tooltip/gr-tooltip';
import {GrTooltip} from '../../../elements/shared/gr-tooltip/gr-tooltip';
import {fireEvent} from '../../../utils/event-util';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-selection-action-box': GrSelectionActionBox;
  }
}

@customElement('gr-selection-action-box')
export class GrSelectionActionBox extends LitElement {
  /**
   * Fired when the comment creation action was taken (click).
   *
   * @event create-comment-requested
   */

  @query('#tooltip')
  tooltip?: GrTooltip;

  @property({type: Boolean})
  positionBelow = false;

  /**
   * We need to absolutely position the element before we can show it. So
   * initially the tooltip must be invisible.
   */
  @state() private invisible = true;

  constructor() {
    super();
    // See https://crbug.com/gerrit/4767
    this.addEventListener('mousedown', e => this.handleMouseDown(e));
  }

  static override styles = [
    sharedStyles,
    css`
      :host {
        cursor: pointer;
        font-family: var(--font-family);
        position: absolute;
        white-space: nowrap;
      }
      gr-tooltip[invisible] {
        visibility: hidden;
      }
    `,
  ];

  override render() {
    return html`
      <gr-tooltip
        id="tooltip"
        ?invisible=${this.invisible}
        text="Press c to comment"
        ?position-below=${this.positionBelow}
      ></gr-tooltip>
    `;
  }

  async placeAbove(el: Text | Element | Range) {
    if (!this.tooltip) return;
    await this.tooltip.updateComplete;
    const rect = this.getTargetBoundingRect(el);
    const boxRect = this.tooltip.getBoundingClientRect();
    const parentRect = this.getParentBoundingClientRect();
    if (parentRect === null) {
      return;
    }
    this.style.top = `${rect.top - parentRect.top - boxRect.height - 6}px`;
    this.style.left = `${
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2
    }px`;
    this.invisible = false;
  }

  async placeBelow(el: Text | Element | Range) {
    if (!this.tooltip) return;
    await this.tooltip.updateComplete;
    const rect = this.getTargetBoundingRect(el);
    const boxRect = this.tooltip.getBoundingClientRect();
    const parentRect = this.getParentBoundingClientRect();
    if (parentRect === null) {
      return;
    }
    this.style.top = `${rect.top - parentRect.top + boxRect.height - 6}px`;
    this.style.left = `${
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2
    }px`;
    this.invisible = false;
  }

  private getParentBoundingClientRect() {
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

  // visible for testing
  getTargetBoundingRect(el: Text | Element | Range) {
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

  // visible for testing
  handleMouseDown(e: MouseEvent) {
    if (e.button !== 0) {
      return;
    } // 0 = main button
    e.preventDefault();
    e.stopPropagation();
    fireEvent(this, 'create-comment-requested');
  }
}
