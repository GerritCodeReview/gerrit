/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-icons/gr-icons';
import '../gr-tooltip/gr-tooltip';
import {getRootElement} from '../../../scripts/rootElement';
import {GrTooltip} from '../gr-tooltip/gr-tooltip';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators';

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

  // A light tooltip will disappear immediately when the original hovered
  // over content is no longer hovered over.
  @property({type: Boolean, attribute: 'light-tooltip', reflect: true})
  lightTooltip = false;

  @property({type: Boolean, attribute: 'position-below', reflect: true})
  positionBelow = false;

  @property({type: String, attribute: 'max-width', reflect: true})
  maxWidth?: string;

  @property({type: Boolean, attribute: 'show-icon'})
  showIcon = false;

  // Should be private but used in tests.
  @state()
  isTouchDevice = 'ontouchstart' in document.documentElement;

  // Should be private but used in tests.
  tooltip: GrTooltip | null = null;

  @state()
  private originalTitle = '';

  private hasSetupTooltipListeners = false;

  private readonly windowScrollHandler: () => void;

  private readonly showHandler: () => void;

  private readonly hideHandler: (e: Event) => void;

  // eslint-disable-next-line @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any
  constructor() {
    super();
    this.windowScrollHandler = () => this._handleWindowScroll();
    this.showHandler = () => this._handleShowTooltip();
    this.hideHandler = (e: Event | undefined) => this._handleHideTooltip(e);
  }

  override disconnectedCallback() {
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
    return html`
      <slot></slot>
      ${this.renderIcon()}
    `;
  }

  renderIcon() {
    if (!this.showIcon) return;
    return html`<iron-icon icon="gr-icons:info"></iron-icon>`;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('hasTooltip')) {
      this.setupTooltipListeners();
    }
  }

  private setupTooltipListeners() {
    if (!this.hasTooltip) {
      if (this.hasSetupTooltipListeners) {
        // if attribute set to false, remove the listener
        this.removeEventListener('mouseenter', this.showHandler);
        this.hasSetupTooltipListeners = false;
      }
      return;
    }

    if (this.hasSetupTooltipListeners) {
      return;
    }
    this.hasSetupTooltipListeners = true;
    this.addEventListener('mouseenter', this.showHandler);
  }

  async _handleShowTooltip() {
    if (this.isTouchDevice) {
      return;
    }

    if (
      !this.hasAttribute('title') ||
      this.getAttribute('title') === '' ||
      this.tooltip
    ) {
      return;
    }

    // Store the title attribute text then set it to an empty string to
    // prevent it from showing natively.
    this.originalTitle = this.getAttribute('title') || '';
    this.setAttribute('title', '');

    const tooltip = document.createElement('gr-tooltip');
    tooltip.text = this.originalTitle;
    tooltip.maxWidth = this.getAttribute('max-width') || '';
    tooltip.positionBelow = this.hasAttribute('position-below');
    this.tooltip = tooltip;

    // Set visibility to hidden before appending to the DOM so that
    // calculations can be made based on the element’s size.
    tooltip.style.visibility = 'hidden';
    getRootElement().appendChild(tooltip);
    await tooltip.updateComplete;
    this._positionTooltip(tooltip);
    tooltip.style.visibility = 'initial';

    window.addEventListener('scroll', this.windowScrollHandler);
    this.addEventListener('mouseleave', this.hideHandler);
    this.addEventListener('click', this.hideHandler);
    if (!this.lightTooltip) {
      tooltip.addEventListener('mouseleave', this.hideHandler);
    }
  }

  _handleHideTooltip(e?: Event) {
    if (this.isTouchDevice) {
      return;
    }
    if (!this.hasAttribute('title') || !this.originalTitle) {
      return;
    }
    // Do not hide if mouse left this or this.tooltip and came to this or
    // this.tooltip
    if (
      (!this.lightTooltip &&
        (e as MouseEvent)?.relatedTarget === this.tooltip) ||
      (e as MouseEvent)?.relatedTarget === this
    ) {
      return;
    }

    window.removeEventListener('scroll', this.windowScrollHandler);
    this.removeEventListener('mouseleave', this.hideHandler);
    this.removeEventListener('click', this.hideHandler);
    this.setAttribute('title', this.originalTitle);
    if (!this.lightTooltip) {
      this.tooltip?.removeEventListener('mouseleave', this.hideHandler);
    }

    if (this.tooltip?.parentNode) {
      this.tooltip.parentNode.removeChild(this.tooltip);
    }
    this.tooltip = null;
  }

  _handleWindowScroll() {
    if (!this.tooltip) {
      return;
    }
    // This wait is needed for tooltips to be positioned correctly in Firefox
    // and Safari.
    this.updateComplete.then(() => this._positionTooltip(this.tooltip));
  }

  // private but used in tests.
  _positionTooltip(tooltip: GrTooltip | null) {
    if (tooltip === null) return;
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
      tooltip.arrowCenterOffset = `${left}px`;
    } else if (right < 0) {
      tooltip.arrowCenterOffset = `${-0.5 * right}px`;
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
