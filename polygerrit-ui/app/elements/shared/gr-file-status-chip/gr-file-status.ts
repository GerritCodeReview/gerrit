/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {FileInfoStatus} from '../../../constants/constants';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {assertNever} from '../../../utils/common-util';
import '@polymer/iron-icon/iron-icon';
import {iconStyles} from '../../../styles/gr-icon-styles';

/**
 * This is a square colored box with a single letter, which can be used as a
 * prefix column before file names.
 *
 * It can also show an additional "new" icon for indicating that a file was
 * newly added in a patchset.
 *
 * `gr-file-status-chip` was a chip for being shown after a file name. It will
 * become obsolete.
 */
@customElement('gr-file-status')
export class GrFileStatus extends LitElement {
  @property({type: String})
  status?: FileInfoStatus;

  /**
   * Show an additional "new" icon for indicating that a file was newly added
   * in a patchset.
   */
  @property({type: Boolean})
  new = false;

  /**
   * What postfix should the tooltip have? For example you can set
   * ' in ps 5', such that the 'Added' tooltip becomes 'Added in ps 5'.
   */
  @property({type: String})
  labelPostfix = '';

  static override get styles() {
    return [
      iconStyles,
      css`
        :host {
          display: inline-block;
        }
        iron-icon.new {
          margin-right: var(--spacing-xs);
        }
        div.status {
          display: inline-block;
          width: var(--line-height-normal);
          text-align: center;
          border-radius: var(--border-radius);
          background-color: transparent;
        }
        div.status.M {
          background-color: var(--file-status-modified);
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
      `,
    ];
  }

  override render() {
    return html`${this.renderNew()}${this.renderStatus()}`;
  }

  private renderStatus() {
    const classes = ['status', this.status];
    const label = this.computeLabel();
    const text = this.status ?? ' ';
    return html`<div
      class=${classes.join(' ')}
      tabindex="0"
      title=${label}
      aria-label=${label}
    >
      ${text}
    </div>`;
  }

  private renderNew() {
    if (!this.new) return;
    return html`<iron-icon
      class="size-16 new"
      icon="gr-icons:new"
    ></iron-icon>`;
  }

  private computeLabel() {
    if (!this.status) return '';
    const prefix = this.new ? 'Newly ' : '';
    const postfix = this.labelPostfix;
    let statusLabel = '';
    switch (this.status) {
      case FileInfoStatus.ADDED:
        statusLabel = 'Added';
        break;
      case FileInfoStatus.COPIED:
        statusLabel = 'Copied';
        break;
      case FileInfoStatus.DELETED:
        statusLabel = 'Deleted';
        break;
      case FileInfoStatus.MODIFIED:
        statusLabel = 'Modified';
        break;
      case FileInfoStatus.RENAMED:
        statusLabel = 'Renamed';
        break;
      case FileInfoStatus.REWRITTEN:
        statusLabel = 'Rewritten';
        break;
      case FileInfoStatus.UNMODIFIED:
        statusLabel = 'Unchanged';
        break;
      default:
        assertNever(this.status, `Unsupported status: ${this.status}`);
    }
    return prefix + statusLabel + postfix;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-status': GrFileStatus;
  }
}
