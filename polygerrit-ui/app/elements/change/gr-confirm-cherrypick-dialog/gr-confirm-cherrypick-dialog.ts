/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '@polymer/iron-input/iron-input';
import '../../../styles/shared-styles';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-dialog/gr-dialog';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-confirm-cherrypick-dialog_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {appContext} from '../../../services/app-context';
import {
  ChangeInfo,
  BranchInfo,
  RepoName,
  CommitId,
  ChangeInfoId,
} from '../../../types/common';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {customElement, property, observe} from '@polymer/decorators';
import {AutocompleteSuggestion} from '../../shared/gr-autocomplete/gr-autocomplete';
import {HttpMethod, ChangeStatus} from '../../../constants/constants';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {fireEvent} from '../../../utils/event-util';

const SUGGESTIONS_LIMIT = 15;
const CHANGE_SUBJECT_LIMIT = 50;
enum CherryPickType {
  SINGLE_CHANGE = 1,
  TOPIC,
}

// These values are directly displayed in the dialog to show progress of change
enum ProgressStatus {
  RUNNING = 'RUNNING',
  FAILED = 'FAILED',
  NOT_STARTED = 'NOT STARTED',
  SUCCESSFUL = 'SUCCESSFUL',
}

type Statuses = {[changeId: string]: Status};

interface Status {
  status: ProgressStatus;
  msg?: string;
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-cherrypick-dialog': GrConfirmCherrypickDialog;
  }
}

export interface GrConfirmCherrypickDialog {
  $: {
    branchInput: HTMLElement;
  };
}

@customElement('gr-confirm-cherrypick-dialog')
export class GrConfirmCherrypickDialog extends PolymerElement {
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
  branch = '';

  @property({type: String})
  baseCommit?: string;

  @property({type: String})
  changeStatus?: ChangeStatus;

  @property({type: String})
  commitMessage?: string;

  @property({type: String})
  commitNum?: CommitId;

  @property({type: String})
  message = '';

  @property({type: String})
  project?: RepoName;

  @property({type: Array})
  changes: ChangeInfo[] = [];

  @property({type: Object})
  _query: (input: string) => Promise<AutocompleteSuggestion[]>;

  @property({type: Boolean})
  _showCherryPickTopic = false;

  @property({type: Number})
  _changesCount?: number;

  @property({type: Number})
  _cherryPickType = CherryPickType.SINGLE_CHANGE;

  @property({type: Boolean})
  _duplicateProjectChanges = false;

  @property({type: Object})
  // Status of each change that is being cherry picked together
  _statuses: Statuses;

  @property({type: Boolean})
  _invalidBranch = false;

  @property({type: Object})
  reporting: ReportingService;

  private selectedChangeIds = new Set<ChangeInfoId>();

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this._statuses = {};
    this.reporting = appContext.reportingService;
    this._query = (text: string) => this._getProjectBranchesSuggestions(text);
  }

  containsDuplicateProject(changes: ChangeInfo[]) {
    const projects: {[projectName: string]: boolean} = {};
    for (let i = 0; i < changes.length; i++) {
      const change = changes[i];
      if (projects[change.project]) {
        return true;
      }
      projects[change.project] = true;
    }
    return false;
  }

  updateChanges(changes: ChangeInfo[]) {
    this.changes = changes;
    this._statuses = {};
    changes.forEach(change => {
      this.selectedChangeIds.add(change.id);
    });
    this._duplicateProjectChanges = this.containsDuplicateProject(changes);
    this._changesCount = changes.length;
    this._showCherryPickTopic = changes.length > 1;
  }

  @observe('branch')
  _updateBranch(branch: string) {
    const invalidChars = [',', ' '];
    this._invalidBranch = !!(
      branch && invalidChars.some(c => branch.includes(c))
    );
  }

  _isChangeSelected(changeId: ChangeInfoId) {
    return this.selectedChangeIds.has(changeId);
  }

  _toggleChangeSelected(e: Event) {
    const changeId = ((dom(e) as EventApi).localTarget as HTMLElement).dataset[
      'item'
    ]! as ChangeInfoId;
    if (this.selectedChangeIds.has(changeId))
      this.selectedChangeIds.delete(changeId);
    else this.selectedChangeIds.add(changeId);
    const changes = this.changes.filter(change =>
      this.selectedChangeIds.has(change.id)
    );
    this._duplicateProjectChanges = this.containsDuplicateProject(changes);
  }

  _computeTopicErrorMessage(duplicateProjectChanges: boolean) {
    if (duplicateProjectChanges) {
      return 'Two changes cannot be of the same project';
    }
    return '';
  }

  updateStatus(change: ChangeInfo, status: Status) {
    this._statuses = {...this._statuses, [change.id]: status};
  }

  _computeStatus(change: ChangeInfo, statuses: Statuses) {
    if (!change || !statuses || !statuses[change.id])
      return ProgressStatus.NOT_STARTED;
    return statuses[change.id].status;
  }

  _computeStatusClass(change: ChangeInfo, statuses: Statuses) {
    if (!change || !statuses || !statuses[change.id]) return '';
    return statuses[change.id].status === ProgressStatus.FAILED ? 'error' : '';
  }

  _computeError(change: ChangeInfo, statuses: Statuses) {
    if (!change || !statuses || !statuses[change.id]) return '';
    if (statuses[change.id].status === ProgressStatus.FAILED) {
      return statuses[change.id].msg;
    }
    return '';
  }

  _getChangeId(change: ChangeInfo) {
    return change.change_id.substring(0, 10);
  }

  _getTrimmedChangeSubject(subject: string) {
    if (!subject) return '';
    if (subject.length < CHANGE_SUBJECT_LIMIT) return subject;
    return subject.substring(0, CHANGE_SUBJECT_LIMIT) + '...';
  }

  _computeCancelLabel(statuses: Statuses) {
    const isRunningChange = Object.values(statuses).some(
      v => v.status === ProgressStatus.RUNNING
    );
    return isRunningChange ? 'Close' : 'Cancel';
  }

  _computeDisableCherryPick(
    cherryPickType: CherryPickType,
    duplicateProjectChanges: boolean,
    statuses: Statuses,
    branch: string
  ) {
    if (!branch) return true;
    const duplicateProject =
      cherryPickType === CherryPickType.TOPIC && duplicateProjectChanges;
    if (duplicateProject) return true;
    if (!statuses) return false;
    const isRunningChange = Object.values(statuses).some(
      v => v.status === ProgressStatus.RUNNING
    );
    return isRunningChange;
  }

  _computeIfSinglecherryPick(cherryPickType: CherryPickType) {
    return cherryPickType === CherryPickType.SINGLE_CHANGE;
  }

  _computeIfCherryPickTopic(cherryPickType: CherryPickType) {
    return cherryPickType === CherryPickType.TOPIC;
  }

  _handlecherryPickSingleChangeClicked() {
    this._cherryPickType = CherryPickType.SINGLE_CHANGE;
    fireEvent(this, 'iron-resize');
  }

  _handlecherryPickTopicClicked() {
    this._cherryPickType = CherryPickType.TOPIC;
    fireEvent(this, 'iron-resize');
  }

  @observe('changeStatus', 'commitNum', 'commitMessage')
  _computeMessage(
    changeStatus?: string,
    commitNum?: number,
    commitMessage?: string
  ) {
    // Polymer 2: check for undefined
    if (
      changeStatus === undefined ||
      commitNum === undefined ||
      commitMessage === undefined
    ) {
      return;
    }

    let newMessage = commitMessage;

    if (changeStatus === 'MERGED') {
      if (!newMessage.endsWith('\n')) {
        newMessage += '\n';
      }
      newMessage += '(cherry picked from commit ' + commitNum.toString() + ')';
    }
    this.message = newMessage;
  }

  _generateRandomCherryPickTopic(change: ChangeInfo) {
    const randomString = Math.random().toString(36).substr(2, 10);
    const message = `cherrypick-${change.topic}-${randomString}`;
    return message;
  }

  _handleCherryPickFailed(change: ChangeInfo, response?: Response | null) {
    if (!response) return;
    response.text().then((errText: string) => {
      this.updateStatus(change, {status: ProgressStatus.FAILED, msg: errText});
    });
  }

  _handleCherryPickTopic() {
    const changes = this.changes.filter(change =>
      this.selectedChangeIds.has(change.id)
    );
    if (!changes.length) {
      const errorSpan = this.shadowRoot?.querySelector('.error-message');
      errorSpan!.innerHTML = 'No change selected';
      return;
    }
    const topic = this._generateRandomCherryPickTopic(changes[0]);
    changes.forEach(change => {
      this.updateStatus(change, {status: ProgressStatus.RUNNING});
      const payload = {
        destination: this.branch,
        base: null,
        topic,
        allow_conflicts: true,
        allow_empty: true,
      };
      const handleError = (response?: Response | null) => {
        this._handleCherryPickFailed(change, response);
      };
      // revisions and current_revision must exist hence casting
      const patchNum = change.revisions![change.current_revision!]._number;
      this.restApiService
        .executeChangeAction(
          change._number,
          HttpMethod.POST,
          '/cherrypick',
          patchNum,
          payload,
          handleError
        )
        .then(() => {
          this.updateStatus(change, {status: ProgressStatus.SUCCESSFUL});
          const failedOrPending = Object.values(this._statuses).find(
            v => v.status !== ProgressStatus.SUCCESSFUL
          );
          if (!failedOrPending) {
            /* This needs some more work, as the new topic may not always be
          created, instead we may end up creating a new patchset */
            GerritNav.navigateToSearchQuery(`topic: "${topic}"`);
          }
        });
    });
  }

  _handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    if (this._cherryPickType === CherryPickType.TOPIC) {
      this.reporting.reportInteraction('cherry-pick-topic-clicked', {});
      this._handleCherryPickTopic();
      return;
    }
    // Cherry pick single change
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

  resetFocus() {
    this.$.branchInput.focus();
  }

  _getProjectBranchesSuggestions(
    input: string
  ): Promise<AutocompleteSuggestion[]> {
    if (!this.project) {
      this.reporting.error(new Error('no project specified'));
      return Promise.resolve([]);
    }
    if (input.startsWith('refs/heads/')) {
      input = input.substring('refs/heads/'.length);
    }
    return this.restApiService
      .getRepoBranches(input, this.project, SUGGESTIONS_LIMIT)
      .then((response: BranchInfo[] | undefined) => {
        if (!response) return [];
        const branches = [];
        for (const branchInfo of response) {
          let branch;
          if (branchInfo.ref.startsWith('refs/heads/')) {
            branch = branchInfo.ref.substring('refs/heads/'.length);
          } else {
            branch = branchInfo.ref;
          }
          branches.push({name: branch});
        }
        return branches;
      });
  }
}
