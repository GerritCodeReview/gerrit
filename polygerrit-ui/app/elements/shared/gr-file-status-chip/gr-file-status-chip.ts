/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {FileInfoStatus, SpecialFilePath} from '../../../constants/constants';
import {NormalizedFileInfo} from '../../change/gr-file-list/gr-file-list';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

@customElement('gr-file-status-chip')
export class GrFileStatusChip extends LitElement {
  @property({type: Object})
  file?: NormalizedFileInfo;

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
    return html` <span
      class=${this.computeStatusClass(this.file)}
      tabindex="0"
      title=${this.computeLabel(this.file?.status)}
      aria-label=${this.computeLabel(this.file?.status)}
    >
      ${this.computeLabel(this.file?.status)}
    </span>`;
  }

  /**
   * Get a descriptive label for use in the status indicator's tooltip and
   * ARIA label.
   */
  // visible for testing
  computeLabel(status?: FileInfoStatus) {
    const statusCode = this.computeFileStatus(status);
    switch (statusCode) {
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
    }
  }

  // visible for testing
  computeClass(baseClass?: string, path?: string) {
    const classes = [];
    if (baseClass) {
      classes.push(baseClass);
    }
    if (
      path === SpecialFilePath.COMMIT_MESSAGE ||
      path === SpecialFilePath.MERGE_LIST
    ) {
      classes.push('invisible');
    }
    return classes.join(' ');
  }

  // visible for testing
  computeFileStatus(status?: FileInfoStatus): FileInfoStatus {
    return status || FileInfoStatus.MODIFIED;
  }

  private computeStatusClass(file?: NormalizedFileInfo) {
    if (!file) return '';
    const classStr = this.computeClass('status', file.__path);
    return `${classStr} ${this.computeFileStatus(file.status)}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-status-chip': GrFileStatusChip;
  }
}
