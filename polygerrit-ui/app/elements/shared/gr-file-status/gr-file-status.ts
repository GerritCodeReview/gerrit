/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {FileInfoStatus} from '../../../constants/constants';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {assertNever} from '../../../utils/common-util';
import '../gr-tooltip-content/gr-tooltip-content';
import '../gr-icon/gr-icon';

function statusString(status?: FileInfoStatus) {
  if (!status) return '';
  switch (status) {
    case FileInfoStatus.ADDED:
      return 'Added';
    case FileInfoStatus.COPIED:
      return 'Copied';
    case FileInfoStatus.DELETED:
      return 'Deleted';
    case FileInfoStatus.MODIFIED:
      return 'Modified';
    case FileInfoStatus.RENAMED:
      return 'Renamed';
    case FileInfoStatus.REWRITTEN:
      return 'Rewritten';
    case FileInfoStatus.UNMODIFIED:
      return 'Unchanged';
    case FileInfoStatus.REVERTED:
      return 'Reverted';
    default:
      assertNever(status, `Unsupported status: ${status}`);
  }
}

/**
 * This is a square colored box with a single letter, which can be used as a
 * prefix column before file names.
 *
 * It can also show an additional "new" icon for indicating that a file was
 * newly changed in a patchset.
 */
@customElement('gr-file-status')
export class GrFileStatus extends LitElement {
  @property({type: String})
  status?: FileInfoStatus;

  /**
   * Show an additional "new" icon for indicating that a file was newly changed
   * in a patchset.
   */
  @property({type: Boolean})
  newlyChanged = false;

  /**
   * What postfix should the tooltip have? For example you can set
   * ' in ps 5', such that the 'Added' tooltip becomes 'Added in ps 5'.
   */
  @property({type: String})
  labelPostfix = '';

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
        }
        div.status {
          display: inline-block;
          line-height: var(--line-height-normal);
          width: var(--line-height-normal);
          text-align: center;
          border-radius: var(--border-radius);
          background-color: transparent;
          color: var(--file-status-font-color);
        }
        div.status gr-icon {
          color: var(--file-status-font-color);
        }
        div.status.M {
          border: 1px solid var(--border-color);
          line-height: calc(var(--line-height-normal) - 2px);
          width: calc(var(--line-height-normal) - 2px);
          color: var(--deemphasized-text-color);
        }
        div.status.A {
          background-color: var(--file-status-added);
        }
        div.status.D {
          background-color: var(--file-status-deleted);
        }
        div.status.R,
        div.status.W {
          background-color: var(--file-status-renamed);
        }
        div.status.U {
          background-color: var(--file-status-unchanged);
        }
        div.status.X {
          background-color: var(--file-status-reverted);
        }
        .size-16 {
          font-size: 16px;
          position: relative;
          top: 2px;
        }
      `,
    ];
  }

  override render() {
    return html`${this.renderNewlyChanged()}${this.renderStatus()}`;
  }

  private renderStatus() {
    const classes = ['status', this.status];
    return html`
      <gr-tooltip-content
        title=${this.computeLabel()}
        has-tooltip
        aria-label=${statusString(this.status)}
      >
        <div class=${classes.join(' ')} aria-hidden="true">
          ${this.renderIconOrLetter()}
        </div>
      </gr-tooltip-content>
    `;
  }

  private renderIconOrLetter() {
    if (this.status === FileInfoStatus.REVERTED) {
      return html`<gr-icon small icon="undo"></gr-icon>`;
    }
    return html`<span>${this.status ?? ''}</span>`;
  }

  private renderNewlyChanged() {
    if (!this.newlyChanged) return;
    return html`
      <gr-tooltip-content
        title=${this.computeLabel()}
        has-tooltip
        aria-label="newly"
      >
        <gr-icon icon="new_releases" class="size-16"></gr-icon>
      </gr-tooltip-content>
    `;
  }

  private computeLabel() {
    if (!this.status) return '';
    const prefix = this.newlyChanged ? 'Newly ' : '';
    return `${prefix}${statusString(this.status)}${this.labelPostfix}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-status': GrFileStatus;
  }
}
