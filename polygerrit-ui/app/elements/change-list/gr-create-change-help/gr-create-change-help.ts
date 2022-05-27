/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../shared/gr-button/gr-button';
import '../../shared/gr-icons/gr-icons';
import {fireEvent} from '../../../utils/event-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement} from 'lit/decorators';
import {fontStyles} from '../../../styles/gr-font-styles';

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-change-help': GrCreateChangeHelp;
  }
}

@customElement('gr-create-change-help')
export class GrCreateChangeHelp extends LitElement {
  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        :host {
          display: block;
        }
        #graphic {
          display: inline-block;
          margin: var(--spacing-m);
          margin-left: 0;
        }
        #graphic #circle {
          align-items: center;
          background-color: var(--chip-background-color);
          border-radius: 50%;
          display: flex;
          height: 10em;
          justify-content: center;
          width: 10em;
        }
        #graphic iron-icon {
          color: var(--gray-foreground);
          height: 5em;
          width: 5em;
        }
        #graphic p {
          color: var(--deemphasized-text-color);
          text-align: center;
        }
        #help {
          display: inline-block;
          margin: var(--spacing-m);
          padding-top: var(--spacing-xl);
          vertical-align: top;
        }
        #help p {
          margin-bottom: var(--spacing-m);
          max-width: 35em;
        }
        @media only screen and (max-width: 50em) {
          #graphic {
            display: none;
          }
        }
      `,
    ];
  }

  override render() {
    return html` <div id="graphic">
        <div id="circle">
          <iron-icon id="icon" icon="gr-icons:zeroState"></iron-icon>
        </div>
        <p>No outgoing changes yet</p>
      </div>
      <div id="help">
        <h2 class="heading-3">Push your first change for code review</h2>
        <p>
          Pushing a change for review is easy, but a little different from other
          git code review tools. Click on the \`Create Change' button and follow
          the step by step instructions.
        </p>
        <gr-button @click=${this._handleCreateTap}>Create Change</gr-button>
      </div>`;
  }

  /**
   * Fired when the "Create change" button is tapped.
   */
  _handleCreateTap(e: Event) {
    e.preventDefault();
    fireEvent(this, 'create-tap');
  }
}
