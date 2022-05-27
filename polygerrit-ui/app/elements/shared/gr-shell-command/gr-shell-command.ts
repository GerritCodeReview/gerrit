/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-copy-clipboard/gr-copy-clipboard';
import {GrCopyClipboard} from '../gr-copy-clipboard/gr-copy-clipboard';
import {queryAndAssert} from '../../../utils/common-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-shell-command': GrShellCommand;
  }
}

@customElement('gr-shell-command')
export class GrShellCommand extends LitElement {
  @property({type: String})
  command: string | undefined;

  @property({type: String})
  label: string | undefined;

  @property({type: String})
  tooltip = '';

  static override get styles() {
    return [
      sharedStyles,
      css`
        .commandContainer {
          margin-bottom: var(--spacing-m);
        }
        .commandContainer {
          background-color: var(--shell-command-background-color);
          /* Should be spacing-m larger than the :before width. */
          padding: var(--spacing-m) var(--spacing-m) var(--spacing-m)
            calc(3 * var(--spacing-m) + 0.5em);
          position: relative;
          width: 100%;
        }
        .commandContainer:before {
          content: '$';
          position: absolute;
          display: block;
          box-sizing: border-box;
          background: var(--shell-command-decoration-background-color);
          top: 0;
          bottom: 0;
          left: 0;
          /* Should be spacing-m smaller than the .commandContainer padding-left. */
          width: calc(2 * var(--spacing-m) + 0.5em);
          /* Should vertically match the padding of .commandContainer. */
          padding: var(--spacing-m);
          /* Should roughly match the height of .commandContainer without padding. */
          line-height: 26px;
        }
        .commandContainer gr-copy-clipboard::part(text-container-style) {
          border: none;
        }
      `,
    ];
  }

  override render() {
    const label = this.label ?? '';
    return html` <label>${label}</label>
      <div class="commandContainer">
        <gr-copy-clipboard
          .text=${this.command}
          hasTooltip
          buttonTitle=${this.tooltip}
        ></gr-copy-clipboard>
      </div>`;
  }

  async focusOnCopy() {
    await this.updateComplete;
    const copyClipboard = queryAndAssert<GrCopyClipboard>(
      this,
      'gr-copy-clipboard'
    );
    if (copyClipboard) {
      copyClipboard.focusOnCopy();
    }
  }
}
