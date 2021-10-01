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
import '../gr-icons/gr-icons';
import '../gr-tooltip/gr-tooltip';
import {getRootElement} from '../../../scripts/rootElement';
import {GrTooltip} from '../gr-tooltip/gr-tooltip';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators';

const BOTTOM_OFFSET = 7.2; // Height of the arrow in tooltip.

declare global {
  interface HTMLElementTagNameMap {
    'gr-tooltip-content': GrTooltipContent;
  }
}

@customElement('gr-tooltip-content')
export class GrTooltipContent extends LitElement {
  @property({type: Boolean, attribute: 'has-tooltip', reflect: true})
  hasTooltip = false;

  @property({type: Boolean, attribute: 'position-below', reflect: true})
  positionBelow = false;

  @property({type: String, attribute: 'max-width', reflect: true})
  maxWidth?: string;

  @property({type: Boolean})
  showIcon = false;

  @property({type: Boolean})
  _isTouchDevice = 'ontouchstart' in document.documentElement;

  @property({type: Object})
  _tooltip: GrTooltip | null = null;

  @property({type: String})
  private _titleText = '';

  @property({type: Boolean})
  _hasSetupTooltipListeners = false;

  // Handler for scrolling on window
  private readonly windowScrollHandler: () => void;

  // Handler for showing the tooltip, will be attached to certain events
  private readonly showHandler: () => void;

  // Handler for hiding the tooltip, will be attached to certain events
  private readonly hideHandler: (e: Event) => void;

  // eslint-disable-next-line @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any
  constructor(..._: any[]) {
    super();
    this.windowScrollHandler = () => this._handleWindowScroll();
    this.showHandler = () => this._handleShowTooltip();
    this.hideHandler = (e: Event | undefined) => this._handleHideTooltip(e);
  }

  override disconnectedCallback() {
    // NOTE: if you define your own `detached` in your component
    // then this won't take affect (as its not a class yet)
    this._handleHideTooltip(undefined);
    this.removeEventListener('mouseenter', this.showHandler);
    window.removeEventListener('scroll', this.windowScrollHandler);
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      css`
        iron-icon {
          width: var(--line-height-normal);
          height: var(--line-height-normal);
          vertical-align: top;
        }
      `,
    ];
  }

  override render() {
    if (!this.showIcon) return html`<slot></slot>`;
    return html`
      <slot></slot>
      <iron-icon icon="gr-icons:info"></iron-icon>
    `;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('hasTooltip')) {
      this.setupTooltipListeners();
    }
  }

  private setupTooltipListeners() {
    if (!this.hasTooltip) {
      if (this._hasSetupTooltipListeners) {
        // if attribute set to false, remove the listener
        this.removeEventListener('mouseenter', this.showHandler);
        this._hasSetupTooltipListeners = false;
      }
      return;
    }

    if (this._hasSetupTooltipListeners) {
      return;
    }
    this._hasSetupTooltipListeners = true;
    this.addEventListener('mouseenter', this.showHandler);
  }

  _handleShowTooltip() {
    if (this._isTouchDevice) {
      return;
    }

    if (
      !this.hasAttribute('title') ||
      this.getAttribute('title') === '' ||
      this._tooltip
    ) {
      return;
    }

    // Store the title attribute text then set it to an empty string to
    // prevent it from showing natively.
    this._titleText = this.getAttribute('title') || '';
    this.setAttribute('title', '');

    const tooltip = document.createElement('gr-tooltip');
    tooltip.text = this._titleText;
    tooltip.maxWidth = this.getAttribute('max-width') || '';
    tooltip.positionBelow = this.hasAttribute('position-below');

    // Set visibility to hidden before appending to the DOM so that
    // calculations can be made based on the element’s size.
    tooltip.style.visibility = 'hidden';
    getRootElement().appendChild(tooltip);
    this._positionTooltip(tooltip);
    tooltip.style.visibility = 'initial';

    this._tooltip = tooltip;
    window.addEventListener('scroll', this.windowScrollHandler);
    this.addEventListener('mouseleave', this.hideHandler);
    this.addEventListener('click', this.hideHandler);
    tooltip.addEventListener('mouseleave', this.hideHandler);
  }

  _handleHideTooltip(e: Event | undefined) {
    if (this._isTouchDevice) {
      return;
    }
    if (!this.hasAttribute('title') || !this._titleText) {
      return;
    }
    // Do not hide if mouse left this or this._tooltip and came to this or
    // this._tooltip
    if (
      (e as MouseEvent)?.relatedTarget === this._tooltip ||
      (e as MouseEvent)?.relatedTarget === this
    ) {
      return;
    }

    window.removeEventListener('scroll', this.windowScrollHandler);
    this.removeEventListener('mouseleave', this.hideHandler);
    this.removeEventListener('click', this.hideHandler);
    this.setAttribute('title', this._titleText);
    this._tooltip?.removeEventListener('mouseleave', this.hideHandler);

    if (this._tooltip?.parentNode) {
      this._tooltip.parentNode.removeChild(this._tooltip);
    }
    this._tooltip = null;
  }

  _handleWindowScroll() {
    if (!this._tooltip) {
      return;
    }

    this._positionTooltip(this._tooltip);
  }

  // private but used in tests.
  async _positionTooltip(tooltip: GrTooltip) {
    // This wait is needed for tooltips to be positioned correctly in Firefox
    // and Safari.
    await this.updateComplete;
    const rect = this.getBoundingClientRect();
    const boxRect = tooltip.getBoundingClientRect();
    if (!tooltip.parentElement) {
      return;
    }
    const parentRect = tooltip.parentElement.getBoundingClientRect();
    const top = rect.top - parentRect.top;
    const left = rect.left - parentRect.left + (rect.width - boxRect.width) / 2;
    const right = parentRect.width - left - boxRect.width;
    if (left < 0) {
      tooltip.updateStyles({
        '--gr-tooltip-arrow-center-offset': `${left}px`,
      });
    } else if (right < 0) {
      tooltip.updateStyles({
        '--gr-tooltip-arrow-center-offset': `${-0.5 * right}px`,
      });
    }
    tooltip.style.left = `${Math.max(0, left)}px`;

    if (!this.positionBelow) {
      tooltip.style.top = `${Math.max(0, top)}px`;
      tooltip.style.transform = `translateY(calc(-100% - ${BOTTOM_OFFSET}px))`;
    } else {
      tooltip.style.top = `${top + rect.height + BOTTOM_OFFSET}px`;
    }
  }
}
