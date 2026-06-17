/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../elements/shared/gr-icon/gr-icon';
import {fire} from '../../../utils/event-util';
import {html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {SelectionContext} from '../../../types/events';

export interface SelectionActionBoxVisibleEventDetail {
  getSelectionContext?: () => Promise<SelectionContext>;
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-selection-action-box': GrSelectionActionBox;
  }
  interface HTMLElementEventMap {
    /** Fired when the comment creation action was taken (click). */
    'create-comment-requested': CustomEvent<{}>;

    /** Fired when the selection action box is visible. */
    'selection-action-box-visible': CustomEvent<SelectionActionBoxVisibleEventDetail>;

    /** Fired when the add to chat action was taken (click). */
    'add-to-chat-requested': CustomEvent<{}>;
  }
}

@customElement('gr-selection-action-box')
export class GrSelectionActionBox extends LitElement {
  @query('#container')
  container?: HTMLElement;


  @property({type: Boolean})
  positionBelow = false;

  @property({type: String})
  hoverCardText = 'Press c to comment';

  @property({type: Object})
  getSelectionContext?: () => Promise<SelectionContext>;

  /**
   * We need to absolutely position the element before we can show it. So
   * initially the tooltip must be invisible.
   */
  @state() private invisible = true;

  constructor() {
    super();
  }

  override render() {
    return html`
      <slot
        name="selectionActionBox"
        ?invisible=${this.invisible}
      >
        <div
          id="container"
          class=${this.invisible ? 'invisible' : ''}
        >
          <button class="action-btn" @mousedown=${this.handleCommentClick}>
            <gr-icon icon="chat_bubble"></gr-icon>
            Comment (c)
          </button>
          <button class="action-btn" @mousedown=${this.handleChatClick}>
            <gr-icon icon="smart_toy"></gr-icon>
            Add to Chat
          </button>
        </div>
      </slot>
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

  // TODO(b/315277651): This is very similar in purpose to gr-tooltip-content.
  //   We should figure out a way to reuse as much of the logic as possible.
  async placeAbove(el: Text | Element | Range) {
    await this.updateComplete;
    if (!this.container) return;
    const rect = this.getTargetBoundingRect(el);
    const boxRect = this.container.getBoundingClientRect();
    const parentRect = this.getParentBoundingClientRect();
    if (parentRect === null) {
      return;
    }
    this.style.top = `${rect.top - parentRect.top - boxRect.height - 6}px`;
    this.style.left = `${
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2
    }px`;
    this.invisible = false;
    fire(this, 'selection-action-box-visible', {
      getSelectionContext: this.getSelectionContext,
    });
  }

  async placeBelow(el: Text | Element | Range) {
    await this.updateComplete;
    if (!this.container) return;
    const rect = this.getTargetBoundingRect(el);
    const boxRect = this.container.getBoundingClientRect();
    const parentRect = this.getParentBoundingClientRect();
    if (parentRect === null) {
      return;
    }
    this.style.top = `${rect.top - parentRect.top + boxRect.height - 6}px`;
    this.style.left = `${
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2
    }px`;
    this.invisible = false;
    fire(this, 'selection-action-box-visible', {
      getSelectionContext: this.getSelectionContext,
    });
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

  private handleCommentClick(e: MouseEvent) {
    if (e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    fire(this, 'create-comment-requested', {});
  }

  private handleChatClick(e: MouseEvent) {
    if (e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    fire(this, 'add-to-chat-requested', {});
  }
}
