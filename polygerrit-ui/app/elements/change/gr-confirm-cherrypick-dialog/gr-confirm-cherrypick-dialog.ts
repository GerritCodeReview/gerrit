/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '@polymer/iron-input/iron-input';
import '../../../styles/shared-styles';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-dialog/gr-dialog';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {getAppContext} from '../../../services/app-context';
import {
  ChangeInfo,
  BranchName,
  RepoName,
  CommitId,
  ChangeInfoId,
  TopicName,
  ChangeActionDialog,
} from '../../../types/common';
import {customElement, property, query, state} from 'lit/decorators.js';
import {
  AutocompleteQuery,
  AutocompleteSuggestion,
  GrTypedAutocomplete,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {
  HttpMethod,
  ChangeStatus,
  ProgressStatus,
} from '../../../constants/constants';
import {fire, fireNoBubble} from '../../../utils/event-util';
import {css, html, LitElement, PropertyValues} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {choose} from 'lit/directives/choose.js';
import {when} from 'lit/directives/when.js';
import {BindValueChangeEvent} from '../../../types/events';
import {resolve} from '../../../models/dependency';
import {createSearchUrl} from '../../../models/views/search';
import {throwingErrorCallback} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {uuid} from '../../../utils/common-util';
import {ParsedChangeInfo} from '../../../types/types';

const SUGGESTIONS_LIMIT = 15;
const CHANGE_SUBJECT_LIMIT = 50;
enum CherryPickType {
  SINGLE_CHANGE = 1,
  TOPIC,
}

export type Statuses = {[changeId: string]: Status};

interface Status {
  status: ProgressStatus;
  msg?: string;
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-cherrypick-dialog': GrConfirmCherrypickDialog;
  }
}

@customElement('gr-confirm-cherrypick-dialog')
export class GrConfirmCherrypickDialog
  extends LitElement
  implements ChangeActionDialog
{
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
  branch = '' as BranchName;

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
  changes: (ParsedChangeInfo | ChangeInfo)[] = [];

  @state()
  private query: AutocompleteQuery;

  @state()
  private showCherryPickTopic = false;

  @state()
  private changesCount?: number;

  @state()
  cherryPickType = CherryPickType.SINGLE_CHANGE;

  @state()
  private duplicateProjectChanges = false;

  @state()
  // Status of each change that is being cherry picked together
  private statuses: Statuses;

  @state()
  private invalidBranch = false;

  @query('#branchInput')
  branchInput!: GrTypedAutocomplete<BranchName>;

  private selectedChangeIds = new Set<ChangeInfoId>();

  private readonly restApiService = getAppContext().restApiService;

  private readonly reporting = getAppContext().reportingService;

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    this.statuses = {};
    this.query = (text: string) => this.getProjectBranchesSuggestions(text);
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('branch')) {
      this.updateBranch();
    }
    if (
      changedProperties.has('changeStatus') ||
      changedProperties.has('commitNum') ||
      changedProperties.has('commitMessage')
    ) {
      this.computeMessage();
    }
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
        }
        :host([disabled]) {
          opacity: 0.5;
          pointer-events: none;
        }
        label {
          cursor: pointer;
        }
        .main {
          display: flex;
          flex-direction: column;
          width: 100%;
        }
        .main label,
        .main input[type='text'] {
          display: block;
          width: 100%;
        }
        iron-autogrow-textarea {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          width: 73ch; /* Add a char to account for the border. */
        }
        .cherryPickTopicLayout {
          display: flex;
          align-items: center;
          margin-bottom: var(--spacing-m);
        }
        .cherryPickSingleChange,
        .cherryPickTopic {
          margin-left: var(--spacing-m);
        }
        .cherry-pick-topic-message {
          margin-bottom: var(--spacing-m);
        }
        label[for='messageInput'],
        label[for='baseInput'] {
          margin-top: var(--spacing-m);
        }
        .title {
          font-weight: var(--font-weight-bold);
        }
        tr > td {
          padding: var(--spacing-m);
        }
        th {
          color: var(--deemphasized-text-color);
        }
        table {
          border-collapse: collapse;
        }
        tr {
          border-bottom: 1px solid var(--border-color);
        }
        .error {
          color: var(--error-text-color);
        }
        .error-message {
          color: var(--error-text-color);
          margin: var(--spacing-m) 0 var(--spacing-m) 0;
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-dialog
        confirm-label="Cherry Pick"
        .cancelLabel=${this.computeCancelLabel()}
        ?disabled=${this.computeDisableCherryPick(
          this.cherryPickType,
          this.duplicateProjectChanges,
          this.statuses,
          this.branch
        )}
        @confirm=${this.handleConfirmTap}
        @cancel=${this.handleCancelTap}
      >
        <div class="header title" slot="header">
          Cherry Pick Change to Another Branch
        </div>
        <div class="main" slot="main">
          <gr-endpoint-decorator name="cherrypick-main">
            <gr-endpoint-param name="changes" .value=${this.changes}>
            </gr-endpoint-param>
            <gr-endpoint-slot name="top"></gr-endpoint-slot>
            ${when(this.showCherryPickTopic, () =>
              this.renderCherrypickTopicLayout()
            )}
            <label for="branchInput"> Cherry Pick to branch </label>
            <gr-autocomplete
              id="branchInput"
              .text=${this.branch}
              .query=${this.query}
              placeholder="Destination branch"
              @text-changed=${(e: BindValueChangeEvent) =>
                (this.branch = e.detail.value as BranchName)}
            >
            </gr-autocomplete>
            ${when(
              this.invalidBranch,
              () => html`
                <span class="error"
                  >Branch name cannot contain space or commas.</span
                >
              `
            )}
            ${choose(this.cherryPickType, [
              [
                CherryPickType.SINGLE_CHANGE,
                () => this.renderCherrypickSingleChangeInputs(),
              ],
              [CherryPickType.TOPIC, () => this.renderCherrypickTopicTable()],
            ])}
            <gr-endpoint-slot name="bottom"></gr-endpoint-slot>
          </gr-endpoint-decorator>
        </div>
      </gr-dialog>
    `;
  }

  private renderCherrypickTopicLayout() {
    return html`
      <div class="cherryPickTopicLayout">
        <input
          name="cherryPickOptions"
          type="radio"
          id="cherryPickSingleChange"
          @change=${this.handlecherryPickSingleChangeClicked}
          checked
        />
        <label for="cherryPickSingleChange" class="cherryPickSingleChange">
          Cherry Pick single change
        </label>
      </div>
      <div class="cherryPickTopicLayout">
        <input
          name="cherryPickOptions"
          type="radio"
          id="cherryPickTopic"
          @change=${this.handlecherryPickTopicClicked}
        />
        <label for="cherryPickTopic" class="cherryPickTopic">
          Cherry Pick entire topic (${this.changesCount} Changes)
        </label>
      </div>
    `;
  }

  private renderCherrypickSingleChangeInputs() {
    return html`
      <label for="baseInput"> Provide base commit sha1 for cherry-pick </label>
      <iron-input
        .bindValue=${this.baseCommit}
        @bind-value-changed=${(e: BindValueChangeEvent) =>
          (this.baseCommit = e.detail.value)}
      >
        <input
          is="iron-input"
          id="baseCommitInput"
          maxlength="40"
          placeholder="(optional)"
        />
      </iron-input>
      <label for="messageInput"> Cherry Pick Commit Message </label>
      <iron-autogrow-textarea
        id="messageInput"
        class="message"
        autocomplete="on"
        rows="4"
        .maxRows=${15}
        .bindValue=${this.message}
        @bind-value-changed=${(e: BindValueChangeEvent) =>
          (this.message = e.detail.value ?? '')}
      ></iron-autogrow-textarea>
    `;
  }

  private renderCherrypickTopicTable() {
    return html`
      <span class="error-message">${this.computeTopicErrorMessage()}</span>
      <span class="cherry-pick-topic-message">
        Commit Message will be auto generated
      </span>
      <table>
        <thead>
          <tr>
            <th></th>
            <th>Change</th>
            <th>Status</th>
            <th>Subject</th>
            <th>Project</th>
            <th>Progress</th>
            <!-- Error Message -->
            <th></th>
          </tr>
        </thead>
        <tbody>
          ${this.changes.map(
            item => html`
              <tr>
                <td>
                  <input
                    type="checkbox"
                    data-item=${item.id as string}
                    @change=${this.toggleChangeSelected}
                    ?checked=${this.isChangeSelected(item.id)}
                  />
                </td>
                <td><span> ${this.getChangeId(item)} </span></td>
                <td><span> ${item.status} </span></td>
                <td>
                  <span> ${this.getTrimmedChangeSubject(item.subject)} </span>
                </td>
                <td><span> ${item.project} </span></td>
                <td>
                  <span class=${this.computeStatusClass(item, this.statuses)}>
                    ${this.computeStatus(item, this.statuses)}
                  </span>
                </td>
                <td>
                  <span class="error">
                    ${this.computeError(item, this.statuses)}
                  </span>
                </td>
              </tr>
            `
          )}
        </tbody>
      </table>
    `;
  }

  containsDuplicateProject(changes: (ChangeInfo | ParsedChangeInfo)[]) {
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

  updateChanges(changes: (ParsedChangeInfo | ChangeInfo)[]) {
    this.changes = changes;
    this.statuses = {};
    changes.forEach(change => {
      this.selectedChangeIds.add(change.id);
    });
    this.duplicateProjectChanges = this.containsDuplicateProject(changes);
    this.changesCount = changes.length;
    this.showCherryPickTopic = changes.length > 1;
  }

  private updateBranch() {
    const invalidChars = [',', ' '];
    this.invalidBranch = !!(
      this.branch && invalidChars.some(c => this.branch.includes(c))
    );
  }

  private isChangeSelected(changeId: ChangeInfoId) {
    return this.selectedChangeIds.has(changeId);
  }

  private toggleChangeSelected(e: Event) {
    const changeId = (e.target as HTMLElement).dataset['item']! as ChangeInfoId;
    if (this.selectedChangeIds.has(changeId))
      this.selectedChangeIds.delete(changeId);
    else this.selectedChangeIds.add(changeId);
    const changes = this.changes.filter(change =>
      this.selectedChangeIds.has(change.id)
    );
    this.duplicateProjectChanges = this.containsDuplicateProject(changes);
  }

  private computeTopicErrorMessage() {
    if (this.duplicateProjectChanges) {
      return 'Two changes cannot be of the same project';
    }
    return '';
  }

  updateStatus(change: ChangeInfo | ParsedChangeInfo, status: Status) {
    this.statuses = {...this.statuses, [change.id]: status};
  }

  private computeStatus(
    change: ChangeInfo | ParsedChangeInfo,
    statuses: Statuses
  ) {
    if (!change || !statuses || !statuses[change.id])
      return ProgressStatus.NOT_STARTED;
    return statuses[change.id].status;
  }

  computeStatusClass(
    change: ChangeInfo | ParsedChangeInfo,
    statuses: Statuses
  ) {
    if (!change || !statuses || !statuses[change.id]) return '';
    return statuses[change.id].status === ProgressStatus.FAILED ? 'error' : '';
  }

  private computeError(
    change: ChangeInfo | ParsedChangeInfo,
    statuses: Statuses
  ) {
    if (!change || !statuses || !statuses[change.id]) return '';
    if (statuses[change.id].status === ProgressStatus.FAILED) {
      return statuses[change.id].msg;
    }
    return '';
  }

  private getChangeId(change: ChangeInfo | ParsedChangeInfo) {
    return change.change_id.substring(0, 10);
  }

  private getTrimmedChangeSubject(subject: string) {
    if (!subject) return '';
    if (subject.length < CHANGE_SUBJECT_LIMIT) return subject;
    return subject.substring(0, CHANGE_SUBJECT_LIMIT) + '...';
  }

  private computeCancelLabel() {
    const isRunningChange = Object.values(this.statuses).some(
      v => v.status === ProgressStatus.RUNNING
    );
    return isRunningChange ? 'Close' : 'Cancel';
  }

  private computeDisableCherryPick(
    cherryPickType: CherryPickType,
    duplicateProjectChanges: boolean,
    statuses: Statuses,
    branch: BranchName
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

  private handlecherryPickSingleChangeClicked() {
    this.cherryPickType = CherryPickType.SINGLE_CHANGE;
    fire(this, 'iron-resize', {});
  }

  private handlecherryPickTopicClicked() {
    this.cherryPickType = CherryPickType.TOPIC;
    fire(this, 'iron-resize', {});
  }

  private computeMessage() {
    if (
      this.changeStatus === undefined ||
      this.commitNum === undefined ||
      this.commitMessage === undefined
    ) {
      return;
    }

    let newMessage = this.commitMessage;

    if (this.changeStatus === 'MERGED') {
      if (!newMessage.endsWith('\n')) {
        newMessage += '\n';
      }
      newMessage += '(cherry picked from commit ' + this.commitNum + ')';
    }
    this.message = newMessage;
  }

  private generateRandomCherryPickTopic(change: ChangeInfo) {
    const message = `cherrypick-${change.topic}-${uuid()}`;
    return message;
  }

  private handleCherryPickFailed(
    change: ParsedChangeInfo | ChangeInfo,
    response?: Response | null
  ) {
    if (!response) return;
    response.text().then((errText: string) => {
      this.updateStatus(change, {status: ProgressStatus.FAILED, msg: errText});
    });
  }

  private handleCherryPickTopic() {
    const changes = this.changes.filter(change =>
      this.selectedChangeIds.has(change.id)
    );
    if (!changes.length) {
      const errorSpan = this.shadowRoot?.querySelector('.error-message');
      errorSpan!.innerHTML = 'No change selected';
      return;
    }
    const topic = this.generateRandomCherryPickTopic(changes[0]);
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
        this.handleCherryPickFailed(change, response);
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
          const failedOrPending = Object.values(this.statuses).find(
            v => v.status !== ProgressStatus.SUCCESSFUL
          );
          if (!failedOrPending) {
            // This needs some more work, as the new topic may not always be
            // created, instead we may end up creating a new patchset */
            this.getNavigation().setUrl(
              createSearchUrl({topic: topic as TopicName})
            );
          }
        });
    });
  }

  private handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    if (this.cherryPickType === CherryPickType.TOPIC) {
      this.reporting.reportInteraction('cherry-pick-topic-clicked', {});
      this.handleCherryPickTopic();
      return;
    }
    // Cherry pick single change
    fireNoBubble(this, 'confirm', {});
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubble(this, 'cancel', {});
  }

  resetFocus() {
    this.branchInput.focus();
  }

  async getProjectBranchesSuggestions(
    input: string
  ): Promise<AutocompleteSuggestion[]> {
    if (!this.project) return Promise.reject(new Error('Missing project'));
    if (input.startsWith('refs/heads/')) {
      input = input.substring('refs/heads/'.length);
    }
    return this.restApiService
      .getRepoBranches(
        input,
        this.project,
        SUGGESTIONS_LIMIT,
        /* offset=*/ undefined,
        throwingErrorCallback
      )
      .then(response => {
        if (!response) return [];
        const branches: Array<{name: BranchName}> = [];
        for (const branchInfo of response) {
          let name: string = branchInfo.ref;
          if (name.startsWith('refs/heads/')) {
            name = name.substring('refs/heads/'.length);
          }
          branches.push({name: name as BranchName});
        }
        return branches;
      });
  }
}
