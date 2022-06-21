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

function statusString(status: FileInfoStatus) {
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
 *
 * `gr-file-status-chip` was a chip for being shown after a file name. It will
 * become obsolete.
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
    return html`${this.renderNewlyChanged()}${this.renderStatus()}`;
  }

  private renderStatus() {
    const classes = ['status', this.status];
    return html`<div
      class=${classes.join(' ')}
      tabindex="0"
      title=${this.computeLabel()}
      aria-label=${this.computeLabel()}
    >
      ${this.status ?? ''}
    </div>`;
  }

  private renderNewlyChanged() {
    if (!this.newlyChanged) return;
    return html`<iron-icon
      class="size-16 new"
      icon="gr-icons:new"
      title=${this.computeLabel()}
    ></iron-icon>`;
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
