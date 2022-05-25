/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {FileInfoStatus, SpecialFilePath} from '../../../constants/constants';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';
import {assertNever} from '../../../utils/common-util';
import {NormalizedFileInfo} from '../../change/gr-file-list/gr-file-list';

/**
 * This component does not really care about the full glory of FileInfo and
 * NormalizedFileInfo objects, so let's define the component's expectations
 * in a dedicated narrow type.
 */
type File = Pick<NormalizedFileInfo, 'status' | '__path'>;

@customElement('gr-file-status-chip')
export class GrFileStatusChip extends LitElement {
  @property({type: Object})
  file?: File;

  static override get styles() {
    return [
      sharedStyles,
      css`
        .status {
          display: inline-block;
          border-radius: var(--border-radius);
          margin-left: var(--spacing-s);
          padding: 0 var(--spacing-m);
          color: var(--primary-text-color);
          font-size: var(--font-size-small);
          background-color: var(--file-status-added);
        }
        .status.invisible,
        .status.M {
          display: none;
        }
        .status.D,
        .status.R,
        .status.W {
          background-color: var(--file-status-changed);
        }
        .status.U {
          background-color: var(--file-status-unchanged);
        }
      `,
    ];
  }

  override render() {
    if (!this.file) return;
    const classes = ['status', this.status()];
    if (this.isSpecial()) classes.push('invisible');
    const label = this.computeLabel();
    return html`<span
      class=${classes.join(' ')}
      tabindex="0"
      title=${label}
      aria-label=${label}
    >
      ${label}
    </span>`;
  }

  private computeLabel() {
    const status = this.status();
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

  private status(): FileInfoStatus {
    return this.file?.status ?? FileInfoStatus.MODIFIED;
  }

  private isSpecial() {
    const path = this.file?.__path;
    return (
      path === SpecialFilePath.COMMIT_MESSAGE ||
      path === SpecialFilePath.MERGE_LIST
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-status-chip': GrFileStatusChip;
  }
}
