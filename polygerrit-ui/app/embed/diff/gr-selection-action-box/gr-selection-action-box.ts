/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../elements/shared/gr-tooltip/gr-tooltip';
import {GrTooltip} from '../../../elements/shared/gr-tooltip/gr-tooltip';
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

interface SelectionAction {
  label: string;
  onClick: (detail: GrSelectionActionBox) => void;
}

const registeredActions: SelectionAction[] = [];

document.addEventListener('register-selection-action', (e: Event) => {
  const customEvent = e as CustomEvent<SelectionAction>;
  registeredActions.push(customEvent.detail);
});

@customElement('gr-selection-action-box')
export class GrSelectionActionBox extends LitElement {
  @query('#tooltip')
  tooltip?: GrTooltip;

  @state() private isSlotAssigned = false;

  @query('slot') slotElement!: HTMLSlotElement;

  @property({type: Boolean})
  positionBelow = false;

  @property({type: String})
  hoverCardText = 'Press c to comment';

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

  override render() {
    return html`
      <slot
        name="selectionActionBox"
        ?invisible=${this.invisible}
        @slotchange=${this.handleSlotChange}
      >
        <div class="selection-action-menu">
          <button class="action-button" @click=${this.handleDefaultAction}>
            Press c to comment
          </button>
          ${registeredActions.map(
            action => html`
              <button
                class="action-button"
                @click=${() => action.onClick(this)}
              >
                ${action.label}
              </button>
            `
          )}
        </div>
      </slot>
    `;
  }

  private handleDefaultAction() {
    fire(this, 'create-comment-requested', {});
  }

  private handleSlotChange() {
    const assignedNodes = this.slotElement.assignedNodes({flatten: true});
    this.isSlotAssigned = assignedNodes.length > 0;
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
    const rect = this.getTargetBoundingRect(el);
    const boxRect = this.getBoundingClientRect();
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
    await this.updateComplete;
    const rect = this.getTargetBoundingRect(el);
    const boxRect = this.getBoundingClientRect();
    const parentRect = this.getParentBoundingClientRect();
    if (parentRect === null) {
      return;
    }
    this.style.top = `${rect.top - parentRect.top + boxRect.height - 6}px`;
    this.style.left = `${
      rect.left - parentRect.left + (rect.width - boxRect.width) / 2
    }px`;
    this.invisible = false;
    fire(this, 'selection-action-box-visible', {});
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
    if (this.isSlotAssigned) {
      return;
    }
    if (e.button !== 0) {
      return;
    } // 0 = main button
    e.preventDefault();
    e.stopPropagation();
    fire(this, 'create-comment-requested', {});
  }
}
