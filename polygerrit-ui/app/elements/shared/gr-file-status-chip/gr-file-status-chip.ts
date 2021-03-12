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
import '../../../styles/shared-styles';
import {htmlTemplate} from './gr-file-status-chip_html';
import {customElement, property} from '@polymer/decorators';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {SpecialFilePath} from '../../../constants/constants';
import {NormalizedFileInfo} from '../../change/gr-file-list/gr-file-list';
import {hasOwnProperty} from '../../../utils/common-util';

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
export class GrFileStatusChip extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  file?: NormalizedFileInfo;

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
