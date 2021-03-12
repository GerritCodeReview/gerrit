/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
import '../../shared/gr-dialog/gr-dialog';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-confirm-revert-submission-dialog_html';
import {customElement, property} from '@polymer/decorators';
import {ChangeInfo} from '../../../types/common';
import {fireAlert} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';

const ERR_COMMIT_NOT_FOUND = 'Unable to find the commit hash of this change.';
const CHANGE_SUBJECT_LIMIT = 50;

@customElement('gr-confirm-revert-submission-dialog')
export class GrConfirmRevertSubmissionDialog extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  @property({type: String})
  message?: string;

  @property({type: String})
  commitMessage?: string;

  private readonly jsAPI = appContext.jsApiService;

  _getTrimmedChangeSubject(subject: string) {
    if (!subject) return '';
    if (subject.length < CHANGE_SUBJECT_LIMIT) return subject;
    return subject.substring(0, CHANGE_SUBJECT_LIMIT) + '...';
  }

  _modifyRevertSubmissionMsg(change?: ChangeInfo) {
    if (!change || !this.message || !this.commitMessage) {
      return this.message;
    }
    return this.jsAPI.modifyRevertSubmissionMsg(
      change,
      this.message,
      this.commitMessage
    );
  }

  _populateRevertSubmissionMessage(
    change?: ChangeInfo,
    changes?: ChangeInfo[]
  ) {
    if (change === undefined) {
      return;
    }
    // Follow the same convention of the revert
    const commitHash = change.current_revision;
    if (!commitHash) {
      fireAlert(this, ERR_COMMIT_NOT_FOUND);
      return;
    }
    const revertTitle = `Revert submission ${change.submission_id}`;
    this.message =
      revertTitle + '\n\n' + 'Reason for revert: <INSERT REASONING HERE>\n';
    changes = changes || [];
    if (changes.length) {
      this.message += 'Reverted Changes:\n';
      changes.forEach(change => {
        this.message +=
          `${change.change_id.substring(0, 10)}: ` +
          `${this._getTrimmedChangeSubject(change.subject)}\n`;
      });
    }
    this.message = this._modifyRevertSubmissionMsg(change);
  }

  _handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: false,
      })
    );
  }

  _handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: false,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-revert-submission-dialog': GrConfirmRevertSubmissionDialog;
  }
}
