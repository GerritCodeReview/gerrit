/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import {SpecialFilePath} from '../../../constants/constants';
import {NormalizedFileInfo} from '../../change/gr-file-list/gr-file-list';
import {hasOwnProperty} from '../../../utils/common-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

const FileStatus = {
  A: 'Added',
  C: 'Copied',
  D: 'Deleted',
  M: 'Modified',
  R: 'Renamed',
  W: 'Rewritten',
  U: 'Unchanged',
};

@customElement('gr-file-status-chip')
export class GrFileStatusChip extends LitElement {
  @property({type: Object})
  file?: NormalizedFileInfo;

  static get styles() {
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
      class="${this._computeStatusClass(this.file)}"
      tabindex="0"
      title="${this._computeFileStatusLabel(this.file?.status)}"
      aria-label="${this._computeFileStatusLabel(this.file?.status)}"
    >
      ${this._computeFileStatusLabel(this.file?.status)}
    </span>`;
  }

  /**
   * Get a descriptive label for use in the status indicator's tooltip and
   * ARIA label.
   */
  _computeFileStatusLabel(status?: keyof typeof FileStatus) {
    const statusCode = this._computeFileStatus(status);
    return hasOwnProperty(FileStatus, statusCode)
      ? FileStatus[statusCode]
      : 'Status Unknown';
  }

  _computeClass(baseClass?: string, path?: string) {
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

  _computeFileStatus(
    status?: keyof typeof FileStatus
  ): keyof typeof FileStatus {
    return status || 'M';
  }

  _computeStatusClass(file?: NormalizedFileInfo) {
    if (!file) return '';
    const classStr = this._computeClass('status', file.__path);
    return `${classStr} ${this._computeFileStatus(file.status)}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-status-chip': GrFileStatusChip;
  }
}
