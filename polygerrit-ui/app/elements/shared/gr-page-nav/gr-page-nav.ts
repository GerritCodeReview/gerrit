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
  @query('#nav') private nav?: HTMLElement;

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
        #nav {
          background-color: var(--table-header-background-color);
          border: 1px solid var(--border-color);
          border-top: none;
          height: 100%;
          position: absolute;
          top: 0;
          width: 14em;
        }
        #nav.pinned {
          position: fixed;
        }
        @media only screen and (max-width: 53em) {
          #nav {
            display: none;
          }
        }
      `,
    ];
  }

  override render() {
    return html`
      <nav id="nav" aria-label="Sidebar">
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
  // private but used in test
  getOffsetParent(element?: HTMLElement) {
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
