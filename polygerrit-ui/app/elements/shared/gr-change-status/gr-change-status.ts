/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import '../gr-tooltip-content/gr-tooltip-content';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-status_html';
import {customElement, observe, property} from '@polymer/decorators';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  getRevertCommitHash,
  getCommitFromMessage,
} from '../../../utils/message-util';
import {ChangeId, ChangeInfo, NumericChangeId} from '../../../types/common';
import {ParsedChangeInfo} from '../../../types/types';
import {appContext} from '../../../services/app-context';
import {ChangeStatus, MessageTag} from '../../../constants/constants';

enum ChangeStates {
  MERGED = 'Merged',
  ABANDONED = 'Abandoned',
  MERGE_CONFLICT = 'Merge Conflict',
  WIP = 'WIP',
  PRIVATE = 'Private',
  // This state is not surfaced to the user, but is a temporary state until
  // we can determine if the created revert was submitted or not
  REVERT_CREATED_OR_SUBMITTED = 'Revert Created or Submitted',
  REVERT_CREATED = 'Revert Created',
  REVERT_SUBMITTED = 'Revert Submitted',
}

const WIP_TOOLTIP =
  "This change isn't ready to be reviewed or submitted. " +
  "It will not appear on dashboards unless you are CC'ed or assigned, " +
  'and email notifications will be silenced until the review is started.';

export const MERGE_CONFLICT_TOOLTIP =
  'This change has merge conflicts. ' +
  'Download the patch and run "git rebase". ' +
  'Upload a new patchset after resolving all merge conflicts.';

const PRIVATE_TOOLTIP =
  'This change is only visible to its owner and ' +
  'current reviewers (or anyone with "View Private Changes" permission).';

@customElement('gr-change-status')
class GrChangeStatus extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Boolean, reflectToAttribute: true})
  flat = false;

  @property({type: Object})
  change?: ChangeInfo | ParsedChangeInfo;

  @property({type: String, observer: '_updateChipDetails'})
  status?: ChangeStates;

  @property({type: String})
  tooltipText = '';

  private readonly restApiService = appContext.restApiService;

  private revertSubmittedChangeNum?: NumericChangeId;

  @observe('status', 'change')
  computeRevertSubmittedStatus(
    status?: ChangeStates,
    change?: ChangeInfo | ParsedChangeInfo
  ) {
    if (
      status !== ChangeStates.REVERT_CREATED_OR_SUBMITTED ||
      !change?.messages
    )
      return;
    const revertMessages = change.messages?.filter(
      m => m.tag === MessageTag.TAG_REVERT
    );
    const promises: Promise<ChangeInfo | undefined | null>[] = [];
    revertMessages.forEach(revertMessage => {
      const commit = getCommitFromMessage(revertMessage);
      promises.push(
        this.restApiService.getChange(commit as ChangeId, () => {})
      );
    });
    Promise.all(promises)
      .then(
        changes =>
          changes.find(change => change?.status === ChangeStatus.MERGED)
            ?._number
      )
      .then(changeNum => {
        if (changeNum) {
          this.revertSubmittedChangeNum = changeNum;
          this.set('status', ChangeStates.REVERT_SUBMITTED);
        } else {
          this.set('status', ChangeStates.REVERT_CREATED);
        }
      });
  }

  _computeStatusString(status: ChangeStates) {
    if (status === ChangeStates.REVERT_CREATED_OR_SUBMITTED) return;
    if (status === ChangeStates.WIP && !this.flat) {
      return 'Work in Progress';
    }
    return status;
  }

  _toClassName(str?: ChangeStates) {
    return str ? str.toLowerCase().replace(/\s/g, '-') : '';
  }

  hasStatusLink(status: ChangeStates) {
    return (
      status === ChangeStates.REVERT_CREATED ||
      status === ChangeStates.REVERT_SUBMITTED
    );
  }

  getStatusLink(change?: ParsedChangeInfo, status?: ChangeStates) {
    if (!change) return;
    if (status === ChangeStates.REVERT_CREATED) {
      const revertCommit = getRevertCommitHash(change.messages);
      if (!revertCommit) return;
      return GerritNav.getUrlForSearchQuery(revertCommit);
    }
    if (this.revertSubmittedChangeNum) {
      return GerritNav.getUrlForSearchQuery(`${this.revertSubmittedChangeNum}`);
    }
    return;
  }

  _updateChipDetails(status?: ChangeStates, previousStatus?: ChangeStates) {
    if (previousStatus) {
      this.classList.remove(this._toClassName(previousStatus));
    }
    this.classList.add(this._toClassName(status));

    switch (status) {
      case ChangeStates.WIP:
        this.tooltipText = WIP_TOOLTIP;
        break;
      case ChangeStates.PRIVATE:
        this.tooltipText = PRIVATE_TOOLTIP;
        break;
      case ChangeStates.MERGE_CONFLICT:
        this.tooltipText = MERGE_CONFLICT_TOOLTIP;
        break;
      default:
        this.tooltipText = '';
        break;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-status': GrChangeStatus;
  }
}
