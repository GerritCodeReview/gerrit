/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, query, state} from 'lit/decorators';
import {assertIsDefined} from '../../../utils/common-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-page-nav': GrPageNav;
  }
}

@customElement('gr-page-nav')
export class GrPageNav extends LitElement {
  @query('nav') private nav?: HTMLElement;

  // private but used in test
  @state() headerHeight?: number;

  private readonly bodyScrollHandler: () => void;

  constructor() {
    super();
    this.bodyScrollHandler = () => this.handleBodyScroll();
  }

  override connectedCallback() {
    super.connectedCallback();
    window.addEventListener('scroll', this.bodyScrollHandler);
  }

  override disconnectedCallback() {
    window.removeEventListener('scroll', this.bodyScrollHandler);
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        nav {
          background-color: var(--table-header-background-color);
          border: 1px solid var(--border-color);
          border-top: none;
          height: 100%;
          position: absolute;
          top: 0;
          width: 14em;
        }
        nav.pinned {
          position: fixed;
        }
        @media only screen and (max-width: 53em) {
          nav {
            display: none;
          }
        }
      `,
    ];
  }

  override render() {
    return html`
      <nav aria-label="Sidebar">
        <slot></slot>
      </nav>
    `;
  }

  // private but used in test
  handleBodyScroll() {
    assertIsDefined(this.nav, 'nav');
    if (this.headerHeight === undefined) {
      let top = this.getOffsetTop(this);
      // TODO(TS): Element doesn't have offsetParent,
      // while `offsetParent` are returning Element not HTMLElement
      for (
        let offsetParent = this.offsetParent as HTMLElement | undefined;
        offsetParent;
        offsetParent = this.getOffsetParent(offsetParent)
      ) {
        top += this.getOffsetTop(offsetParent);
      }
      this.headerHeight = top;
    }

    this.nav.classList.toggle(
      'pinned',
      this.getScrollY() >= (this.headerHeight || 0)
    );
  }

  /* Functions used for test purposes */
  private getOffsetParent(element?: HTMLElement) {
    if (!element || !('offsetParent' in element)) {
      return undefined;
    }
    return element.offsetParent as HTMLElement;
  }

  // private but used in test
  getOffsetTop(element: HTMLElement) {
    return element.offsetTop;
  }

  // private but used in test
  getScrollY() {
    return window.scrollY;
  }
}
