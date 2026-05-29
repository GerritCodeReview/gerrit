/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-icon/gr-icon';
import '../gr-tooltip/gr-tooltip';
import {GrTooltip} from '../gr-tooltip/gr-tooltip';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';

const ARROW_HEIGHT = 7.2; // Height of the arrow in tooltip.

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
        gr-icon {
          font-size: var(--line-height-normal);
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
    return html`<gr-icon icon="info" filled></gr-icon>`;
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

    // We need to be able to use the size of the tooltip to calculate it's
    // position. For that before attaching to the DOM
    //  - We set "visibility" to hidden, but don't touch "display" as we need
    //    browser to calculate the size for us.
    //  - Set location to the top left corner, so that we don't increase the
    //    size of the page and cause scrollbars appear if they were not
    //    previously.
    tooltip.style.visibility = 'hidden';
    tooltip.style.top = '0';
    tooltip.style.left = '0';
    const parent = this.getTooltipParent(this);
    parent.appendChild(tooltip);
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

  getTooltipParent(el: Node): Node {
    if (el === document.body) {
      return el;
    }
    if (el instanceof HTMLDialogElement) {
      return el;
    }
    if (el instanceof ShadowRoot) {
      return this.getTooltipParent(el.host);
    }
    if (el.parentNode) {
      return this.getTooltipParent(el.parentNode);
    }
    return document.body;
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
    const hoveredRect = this.getBoundingClientRect();
    const tooltipRect = tooltip.getBoundingClientRect();
    if (!tooltip.parentElement) {
      return;
    }
    const parentRect = tooltip.parentElement.getBoundingClientRect();
    // Use clientWidth to not include the scrollbars
    const parentWidth = tooltip.parentElement.clientWidth;

    const hoveredCenter =
      0.5 * (hoveredRect.left + hoveredRect.right) - parentRect.left;
    const left = this.computeLeft(tooltipRect, hoveredCenter, parentWidth);
    const {isBelow, top} = this.computeTop(
      tooltipRect,
      hoveredRect,
      parentRect
    );
    const tooltipCenter = left + 0.5 * tooltipRect.width;

    tooltip.arrowCenterOffset = `${hoveredCenter - tooltipCenter}px`;
    tooltip.positionBelow = isBelow;
    tooltip.style.top = `${top}px`;
    tooltip.style.left = `${left}px`;
  }

  private computeLeft(
    tooltipRect: DOMRect,
    hoveredCenter: number,
    parentWidth: number
  ) {
    let left = hoveredCenter - 0.5 * tooltipRect.width;
    if (left + tooltipRect.width > parentWidth - 1) {
      // Add 1px of extra padding. Without it on some browser zoom levels
      // the hovercard is still considered going out of bounds and gets
      // reshaped.
      left = parentWidth - tooltipRect.width - 1;
    }
    return Math.max(0, left);
  }

  private computeTop(
    tooltipRect: DOMRect,
    hoveredRect: DOMRect,
    parentRect: DOMRect
  ): {
    isBelow: boolean;
    top: number;
  } {
    const top =
      hoveredRect.top - parentRect.top - tooltipRect.height - ARROW_HEIGHT;
    if (this.positionBelow || top < 0) {
      return {
        isBelow: true,
        top: hoveredRect.bottom - parentRect.top + ARROW_HEIGHT,
      };
    }
    return {isBelow: false, top};
  }
}
