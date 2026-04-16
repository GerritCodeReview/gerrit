/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {fire} from '../../../utils/event-util';
import {html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';

declare global {
  interface HTMLElementTagNameMap {
    'gr-selection-action-box': GrSelectionActionBox;
  }
  interface HTMLElementEventMap {
    /** Fired when the comment creation action was taken (click). */
    'create-comment-requested': CustomEvent<{}>;

    /** Fired when the selection action box is visible. */
    'selection-action-box-visible': CustomEvent<{}>;
  }
}

@customElement('gr-selection-action-box')
export class GrSelectionActionBox extends LitElement {
  @query('.menu')
  menuElement?: HTMLElement;

  @property({type: Boolean})
  positionBelow = false;

  @property({type: String})
  hoverCardText = 'Press c to comment';

  /**
   * We need to absolutely position the element before we can show it. So
   * initially the tooltip must be invisible.
   */
  @state() private invisible = true;


  override render() {
    return html`
      <div
        class="menu"
        ?invisible=${this.invisible}
        @mousedown=${this.handleMenuMouseDown}
      >
        <div class="menu-item">${this.hoverCardText}</div>
      </div>
    `;
  }

  /**
   * The browser API for handling selection does not (yet) work for selection
   * across multiple shadow DOM elements. So we are rendering gr-diff components
   * into the light DOM instead of the shadow DOM by overriding this method,
   * which was the recommended workaround by the lit team.
   * See also https://github.com/WICG/webcomponents/issues/79.
   */
  override createRenderRoot() {
    return this;
  }

  async placeAbove(el: Text | Element | Range) {
    if (!this.menuElement) return;
    await this.updateComplete;
    const rect = this.getTargetBoundingRect(el);
    const boxRect = this.menuElement.getBoundingClientRect();
    const parentRect = this.getParentBoundingClientRect();
    if (parentRect === null) {
      return;
    }
    this.style.top = `${rect.top - parentRect.top - boxRect.height - 6}px`;
    this.style.left = `${
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2
    }px`;
    this.invisible = false;
    fire(this, 'selection-action-box-visible', {});
  }

  async placeBelow(el: Text | Element | Range) {
    if (!this.menuElement) return;
    await this.updateComplete;
    const rect = this.getTargetBoundingRect(el);
    const boxRect = this.menuElement.getBoundingClientRect();
    const parentRect = this.getParentBoundingClientRect();
    if (parentRect === null) {
      return;
    }
    this.style.top = `${rect.top + rect.height - parentRect.top + 6}px`;
    this.style.left = `${
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2
    }px`;
    this.invisible = false;
    fire(this, 'selection-action-box-visible', {});
  }

  private getParentBoundingClientRect() {
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

  // See https://crbug.com/gerrit/4767
  private handleMenuMouseDown(e: MouseEvent) {
    if (e.button !== 0) return; // 0 = main button
    e.preventDefault();
    e.stopPropagation();

    const target = e.target as HTMLElement;
    if (
      target.classList.contains('menu-item') ||
      target.closest('.menu-item')
    ) {
      fire(this, 'create-comment-requested', {});
    }
  }
}
