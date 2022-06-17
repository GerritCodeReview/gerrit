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

@customElement('gr-file-status')
export class GrFileStatus extends LitElement {
  @property({type: String})
  status?: FileInfoStatus;

  @property({type: Boolean})
  new = false;

  static override get styles() {
    return [
      iconStyles,
      css`
        :host {
          display: block;
        }
        iron-icon.new {
          margin-right: 2px;
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
    switch (this.status) {
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
        assertNever(this.status, `Unsupported status: ${this.status}`);
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-status': GrFileStatus;
  }
}
