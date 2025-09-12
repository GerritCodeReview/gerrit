/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '../../../styles/shared-styles';
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-dialog/gr-dialog';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {getAppContext} from '../../../services/app-context';
import {
  BranchName,
  ChangeActionDialog,
  ChangeInfo,
  ChangeInfoId,
  CommitId,
  EmailInfo,
  GitPersonInfo,
  RepoName,
  TopicName,
} from '../../../types/common';
import {customElement, property, query, state} from 'lit/decorators.js';
import {
  AutocompleteQuery,
  GrTypedAutocomplete,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {AutocompleteSuggestion} from '../../../utils/autocomplete-util';
import {
  ChangeStatus,
  HttpMethod,
  ProgressStatus,
} from '../../../constants/constants';
import {subscribe} from '../../lit/subscription-controller';
import {fire, fireNoBubble} from '../../../utils/event-util';
import {trimWithEllipsis} from '../../../utils/string-util';
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
import {formStyles} from '../../../styles/form-styles';
import {branchName} from '../../../utils/patch-set-util';
import {changeModelToken} from '../../../models/change/change-model';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import '@material/web/textfield/outlined-text-field';
import {materialStyles} from '../../../styles/gr-material-styles';

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

  @state()
  emails: EmailInfo[] = [];

  @query('#branchInput')
  branchInput!: GrTypedAutocomplete<BranchName>;

  @state()
  committerEmail?: string;

  @state()
  latestCommitter?: GitPersonInfo;

  @state()
  selectedChangeIds = new Set<ChangeInfoId>();

  private readonly restApiService = getAppContext().restApiService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly reporting = getAppContext().reportingService;

  private readonly getNavigation = resolve(this, navigationToken);

  constructor() {
    super();
    this.statuses = {};
    this.query = (text: string) => this.getProjectBranchesSuggestions(text);
    subscribe(
      this,
      () => this.getChangeModel().latestCommitter$,
      x => (this.latestCommitter = x)
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    this.loadEmails();
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
      materialStyles,
      formStyles,
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
        gr-autogrow-textarea {
          font-family: var(--monospace-font-family);
          font-size: var(--font-size-mono);
          line-height: var(--line-height-mono);
          width: 73ch; /* Add a char to account for the border. */
        }
        gr-autogrow-textarea:focus {
          border: 2px solid var(--input-focus-border-color);
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
          font-weight: var(--font-weight-medium);
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
        ?disabled=${this.computeDisableCherryPick()}
        title=${this.computeTitleForCherryPick()}
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
              .showBlueFocusBorder=${true}
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
      <md-outlined-text-field
        id="baseCommitInput"
        class="showBlueFocusBorder"
        maxlength="40"
        placeholder="(optional)"
        .value=${this.baseCommit ?? ''}
        @input=${(e: InputEvent) => {
          const target = e.target as HTMLInputElement;
          this.baseCommit = target.value;
        }}
      >
      </md-outlined-text-field>
      <label for="messageInput"> Cherry Pick Commit Message </label>
      <gr-autogrow-textarea
        id="messageInput"
        class="message"
        autocomplete="on"
        .rows=${4}
        .maxRows=${15}
        .value=${this.message}
        @input=${(e: InputEvent) => {
          const value = (e.target as GrAutogrowTextarea).value ?? '';
          this.message = value;
        }}
      ></gr-autogrow-textarea>
      ${when(
        this.canShowEmailDropdown(),
        () => html`<div id="cherryPickEmailDropdown">Cherry Pick Committer Email
            <gr-dropdown-list
                .items=${this.getEmailDropdownItems()}
                .value=${this.committerEmail ?? ''}
                @value-change=${this.setCommitterEmail}
            >
            </gr-dropdown-list>
            <span></div>`
      )}
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
                  <span>
                    ${trimWithEllipsis(item.subject, CHANGE_SUBJECT_LIMIT)}
                  </span>
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

  // private but compute in tests
  computeTitleForCherryPick() {
    if (!this.computeDisableCherryPick()) {
      return 'Cherry pick changes';
    }
    if (this.noChangesSelected()) {
      return 'Disabled because no changes are selected';
    }

    if (!this.branch) {
      return 'Disabled because no branch is selected';
    }

    if (
      this.cherryPickType === CherryPickType.TOPIC &&
      this.duplicateProjectChanges
    ) {
      return 'Duplicate projects selected';
    }
    return 'Cherry pick button disabled';
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
    // Explicitly trigger a re-render
    this.selectedChangeIds = new Set(this.selectedChangeIds);
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

  private computeCancelLabel() {
    const isRunningChange = Object.values(this.statuses).some(
      v => v.status === ProgressStatus.RUNNING
    );
    return isRunningChange ? 'Close' : 'Cancel';
  }

  private noChangesSelected() {
    const changes = this.changes.filter(change =>
      this.selectedChangeIds.has(change.id)
    );
    if (this.cherryPickType === CherryPickType.TOPIC && changes.length === 0)
      return true;
    return false;
  }

  private computeDisableCherryPick() {
    if (!this.branch) return true;
    if (this.noChangesSelected()) return true;
    const duplicateProject =
      this.cherryPickType === CherryPickType.TOPIC &&
      this.duplicateProjectChanges;
    if (duplicateProject) return true;
    if (!this.statuses) return false;
    const isRunningChange = Object.values(this.statuses).some(
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

  private generateRandomCherryPickTopic(change: ChangeInfo | ParsedChangeInfo) {
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
        .then(response => {
          if (!response.ok) {
            return;
          }
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
    return this.restApiService
      .getRepoBranches(
        branchName(input),
        this.project,
        SUGGESTIONS_LIMIT,
        /* offset=*/ undefined,
        throwingErrorCallback
      )
      .then(response => {
        if (!response) return [];
        const branches: Array<{name: BranchName}> = [];
        for (const branchInfo of response) {
          branches.push({name: branchName(branchInfo.ref)});
        }
        return branches;
      });
  }

  async loadEmails() {
    const accountEmails: EmailInfo[] =
      (await this.restApiService.getAccountEmails()) ?? [];
    let selectedEmail: string | undefined;
    accountEmails.forEach(e => {
      if (e.preferred) {
        selectedEmail = e.email;
      }
    });

    if (accountEmails.some(e => e.email === this.latestCommitter?.email)) {
      selectedEmail = this.latestCommitter?.email;
    }
    this.emails = accountEmails;
    this.committerEmail = selectedEmail;
  }

  private canShowEmailDropdown() {
    return this.emails.length > 1;
  }

  private getEmailDropdownItems() {
    return this.emails.map(e => {
      return {
        text: e.email,
        value: e.email,
      };
    });
  }

  private setCommitterEmail(e: CustomEvent<{value: string}>) {
    this.committerEmail = e.detail.value;
  }
}
