/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import './gr-checks-chip';
import './gr-summary-chip';
import '../../shared/gr-avatar/gr-avatar-stack';
import '../../shared/gr-icon/gr-icon';
import '../../checks/gr-checks-action';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {sharedStyles} from '../../../styles/shared-styles';
import {getAppContext} from '../../../services/app-context';
import {
  CheckResult,
  CheckRun,
  ErrorMessages,
} from '../../../models/checks/checks-model';
import {Action, Category, RunStatus} from '../../../api/checks';
import {fireShowTab} from '../../../utils/event-util';
import {
  compareByWorstCategory,
  getResultsOf,
  hasCompletedWithoutResults,
  hasResults,
  hasResultsOf,
  isRunningOrScheduled,
  isRunningScheduledOrCompleted,
} from '../../../models/checks/checks-util';
import {
  CommentThread,
  getFirstComment,
  getMentionedThreads,
  hasHumanReply,
  isResolved,
  isRobotThread,
  isUnresolved,
} from '../../../utils/comment-util';
import {pluralize} from '../../../utils/string-util';
import {AccountInfo} from '../../../types/common';
import {notUndefined} from '../../../types/types';
import {Tab} from '../../../constants/constants';
import {ChecksTabState, CommentTabState} from '../../../types/events';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {DropdownLink} from '../../shared/gr-dropdown/gr-dropdown';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {resolve} from '../../../models/dependency';
import {checksModelToken} from '../../../models/checks/checks-model';
import {changeModelToken} from '../../../models/change/change-model';
import {Interaction} from '../../../constants/reporting';
import {roleDetails} from '../../../utils/change-util';
import {handleSpaceOrEnter} from '../../../utils/dom-util';

import {SummaryChipStyles} from './gr-summary-chip';
import {when} from 'lit/directives/when.js';
import {KnownExperimentId} from '../../../services/flags/flags';
import {combineLatest} from 'rxjs';

<<<<<<< PATCH SET (10708e Make hovercards stay permanently if opened by keyboard.)
@customElement('gr-summary-chip')
export class GrSummaryChip extends LitElement {
  @property()
  icon = '';

  @property()
  styleType = SummaryChipStyles.UNDEFINED;

  @property()
  category?: CommentTabState;

  private readonly reporting = getAppContext().reportingService;

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        .summaryChip {
          color: var(--chip-color);
          cursor: pointer;
          display: inline-block;
          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)
            var(--spacing-s);
          margin-right: var(--spacing-s);
          border-radius: 12px;
          border: 1px solid gray;
          vertical-align: top;
          /* centered position of 20px chips in 24px line-height inline flow */
          vertical-align: top;
          position: relative;
          top: 2px;
        }
        iron-icon {
          width: var(--line-height-small);
          height: var(--line-height-small);
          vertical-align: top;
        }
        .summaryChip.warning {
          border-color: var(--warning-foreground);
          background: var(--warning-background);
        }
        .summaryChip.warning:hover {
          background: var(--warning-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .summaryChip.warning:focus-within {
          background: var(--warning-background-focus);
        }
        .summaryChip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .summaryChip.check {
          border-color: var(--gray-foreground);
          background: var(--gray-background);
        }
        .summaryChip.check:hover {
          background: var(--gray-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .summaryChip.check:focus-within {
          background: var(--gray-background-focus);
        }
        .summaryChip.check iron-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  override render() {
    const chipClass = `summaryChip font-small ${this.styleType}`;
    const grIcon = this.icon ? `gr-icons:${this.icon}` : '';
    return html`<button class="${chipClass}" @click="${this.handleClick}">
      ${this.icon && html`<iron-icon icon="${grIcon}"></iron-icon>`}
      <slot></slot>
    </button>`;
  }

  private handleClick(e: MouseEvent) {
    e.stopPropagation();
    e.preventDefault();
    this.reporting.reportInteraction('comment chip click', {
      category: this.category,
    });
    fireShowPrimaryTab(this, PrimaryTab.COMMENT_THREADS, true, {
      commentTab: this.category,
    });
  }
}

@customElement('gr-checks-chip')
export class GrChecksChip extends LitElement {
  @property()
  statusOrCategory?: Category | RunStatus;

  @property()
  text = '';

  @property()
  links: string[] = [];

  private readonly reporting = getAppContext().reportingService;

  static override get styles() {
    return [
      fontStyles,
      sharedStyles,
      css`
        :host {
          display: inline-block;
          position: relative;
          white-space: nowrap;
        }
        .checksChip {
          color: var(--chip-color);
          cursor: pointer;
          display: inline-block;
          margin-right: var(--spacing-s);
          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)
            var(--spacing-s);
          border-radius: 12px;
          border: 1px solid gray;
          /* centered position of 20px chips in 24px line-height inline flow */
          vertical-align: top;
          position: relative;
          top: 2px;
        }
        .checksChip.hoverFullLength {
          position: absolute;
          z-index: 1;
          display: none;
        }
        .checksChip.hoverFullLength .text {
          max-width: 500px;
        }
        :host(:hover) .checksChip.hoverFullLength {
          display: inline-block;
        }
        .checksChip .text {
          display: inline-block;
          max-width: 120px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          vertical-align: top;
        }
        iron-icon {
          width: var(--line-height-small);
          height: var(--line-height-small);
          vertical-align: top;
        }
        .checksChip a iron-icon.launch {
          color: var(--link-color);
        }
        .checksChip.error {
          color: var(--error-foreground);
          border-color: var(--error-foreground);
          background: var(--error-background);
        }
        .checksChip.error:hover {
          background: var(--error-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.error:focus-within {
          background: var(--error-background-focus);
        }
        .checksChip.error iron-icon {
          color: var(--error-foreground);
        }
        .checksChip.warning {
          border-color: var(--warning-foreground);
          background: var(--warning-background);
        }
        .checksChip.warning:hover {
          background: var(--warning-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.warning:focus-within {
          background: var(--warning-background-focus);
        }
        .checksChip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .checksChip.info-outline {
          border-color: var(--info-foreground);
          background: var(--info-background);
        }
        .checksChip.info-outline:hover {
          background: var(--info-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.info-outline:focus-within {
          background: var(--info-background-focus);
        }
        .checksChip.info-outline iron-icon {
          color: var(--info-foreground);
        }
        .checksChip.check-circle-outline {
          border-color: var(--success-foreground);
          background: var(--success-background);
        }
        .checksChip.check-circle-outline:hover {
          background: var(--success-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.check-circle-outline:focus-within {
          background: var(--success-background-focus);
        }
        .checksChip.check-circle-outline iron-icon {
          color: var(--success-foreground);
        }
        .checksChip.timelapse,
        .checksChip.scheduled {
          border-color: var(--gray-foreground);
          background: var(--gray-background);
        }
        .checksChip.timelapse:hover,
        .checksChip.scheduled:hover {
          background: var(--gray-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.timelapse:focus-within,
        .checksChip.scheduled:focus-within {
          background: var(--gray-background-focus);
        }
        .checksChip.timelapse iron-icon,
        .checksChip.scheduled iron-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  override render() {
    if (!this.text) return;
    if (!this.statusOrCategory) return;
    const icon = iconFor(this.statusOrCategory);
    const label = labelFor(this.statusOrCategory);
    const count = Number(this.text);
    let ariaLabel = label;
    if (!isNaN(count)) {
      const type = isStatus(this.statusOrCategory) ? 'run' : 'result';
      const plural = count > 1 ? 's' : '';
      ariaLabel = `${this.text} ${label} ${type}${plural}`;
    }
    const chipClass = `checksChip font-small ${icon}`;
    const chipClassFullLength = `${chipClass} hoverFullLength`;
    const grIcon = `gr-icons:${icon}`;
    // 15 is roughly the number of chars for the chip exceeding its 120px width.
    return html`
      ${this.text.length > 15
        ? html` ${this.renderChip(chipClassFullLength, ariaLabel, grIcon)}`
        : ''}
      ${this.renderChip(chipClass, ariaLabel, grIcon)}
    `;
  }

  private renderChip(clazz: string, ariaLabel: string, icon: string) {
    return html`
      <div class="${clazz}" role="link" tabindex="0" aria-label="${ariaLabel}">
        <iron-icon icon="${icon}"></iron-icon>
        ${this.renderLinks()}
        <div class="text">${this.text}</div>
      </div>
    `;
  }

  private renderLinks() {
    return this.links.map(
      link => html`
        <a
          href="${link}"
          target="_blank"
          @click="${this.onLinkClick}"
          @keydown="${this.onLinkKeyDown}"
          aria-label="Link to check details"
          ><iron-icon class="launch" icon="gr-icons:launch"></iron-icon
        ></a>
      `
    );
  }

  private onLinkKeyDown(e: KeyboardEvent) {
    // Prevents onChipKeyDown() from reacting to <a> link keyboard events.
    e.stopPropagation();
  }

  private onLinkClick(e: MouseEvent) {
    // Prevents onChipClick() from reacting to <a> link clicks.
    e.stopPropagation();
    this.reporting.reportInteraction(Interaction.CHECKS_CHIP_LINK_CLICKED, {
      text: this.text,
      status: this.statusOrCategory,
    });
  }
=======
function handleSpaceOrEnter(e: KeyboardEvent, handler: () => void) {
  if (modifierPressed(e)) return;
  if (e.key !== 'Enter' && e.key !== ' ') return;
  e.preventDefault();
  e.stopPropagation();
  handler();
>>>>>>> BASE      (6fbebb Merge "Hide suggest edit button in permanent editing mode")
}

/** What is the maximum number of detailed checks chips? */
const DETAILS_QUOTA: Map<RunStatus | Category, number> = new Map();
DETAILS_QUOTA.set(Category.ERROR, 7);
DETAILS_QUOTA.set(Category.WARNING, 2);
DETAILS_QUOTA.set(RunStatus.RUNNING, 2);

@customElement('gr-change-summary')
export class GrChangeSummary extends LitElement {
  @state()
  commentThreads?: CommentThread[];

  @state()
  mentionCount = 0;

  @state()
  selfAccount?: AccountInfo;

  @state()
  runs: CheckRun[] = [];

  @state()
  showChecksSummary = false;

  @state()
  someProvidersAreLoading = false;

  @state()
  errorMessages: ErrorMessages = {};

  @state()
  loginCallback?: () => void;

  @state()
  actions: Action[] = [];

  @state()
  messages: string[] = [];

  @state()
  draftCount = 0;

  private readonly showAllChips = new Map<RunStatus | Category, boolean>();

  // private but used in tests
  readonly getCommentsModel = resolve(this, commentsModelToken);

  // private but used in tests
  readonly userModel = getAppContext().userModel;

  private readonly getChecksModel = resolve(this, checksModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly reporting = getAppContext().reportingService;

  private readonly flagsService = getAppContext().flagsService;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().allRunsLatestPatchsetLatestAttempt$,
      x => (this.runs = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().aPluginHasRegistered$,
      x => (this.showChecksSummary = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().someProvidersAreLoadingFirstTime$,
      x => (this.someProvidersAreLoading = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().errorMessagesLatest$,
      x => (this.errorMessages = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().loginCallbackLatest$,
      x => (this.loginCallback = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().topLevelActionsLatest$,
      x => (this.actions = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().topLevelMessagesLatest$,
      x => (this.messages = x)
    );
    subscribe(
      this,
      () => this.getCommentsModel().draftsCount$,
      x => (this.draftCount = x)
    );
    subscribe(
      this,
      () => this.getCommentsModel().threads$,
      x => (this.commentThreads = x)
    );
    subscribe(
      this,
      () => this.userModel.account$,
      x => (this.selfAccount = x)
    );
    if (this.flagsService.isEnabled(KnownExperimentId.MENTION_USERS)) {
      subscribe(
        this,
        () =>
          combineLatest([
            this.userModel.account$,
            this.getCommentsModel().threads$,
          ]),
        ([selfAccount, threads]) => {
          if (!selfAccount || !selfAccount.email) return;
          const unresolvedThreadsMentioningSelf = getMentionedThreads(
            threads,
            selfAccount
          ).filter(isUnresolved);
          this.mentionCount = unresolvedThreadsMentioningSelf.length;
        }
      );
    }
  }

  static override get styles() {
    return [
      sharedStyles,
      spinnerStyles,
      css`
        :host {
          display: block;
          color: var(--deemphasized-text-color);
          max-width: 625px;
          margin-bottom: var(--spacing-m);
        }
        .zeroState {
          color: var(--deemphasized-text-color);
        }
        .loading.zeroState {
          margin-right: var(--spacing-m);
        }
        div.info,
        div.error,
        .login {
          display: flex;
          color: var(--primary-text-color);
          padding: 0 var(--spacing-s);
          margin: var(--spacing-xs) 0;
          width: 490px;
        }
        div.info {
          background-color: var(--info-background);
        }
        div.error {
          background-color: var(--error-background);
        }
        div.info gr-icon,
        div.error gr-icon {
          font-size: 16px;
          position: relative;
          top: 4px;
          margin-right: var(--spacing-s);
        }
        div.info gr-icon {
          color: var(--info-foreground);
        }
        div.error gr-icon {
          color: var(--error-foreground);
        }
        div.info .right,
        div.error .right {
          overflow: hidden;
        }
        div.info .right .message,
        div.error .right .message {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .login {
          justify-content: space-between;
          background: var(--info-background);
        }
        .login gr-icon {
          color: var(--info-foreground);
        }
        .login gr-button {
          margin: -4px var(--spacing-s);
        }
        td.key {
          padding-right: var(--spacing-l);
          padding-bottom: var(--spacing-s);
          line-height: calc(var(--line-height-normal) + var(--spacing-s));
        }
        td.value {
          padding-right: var(--spacing-l);
          padding-bottom: var(--spacing-s);
          line-height: calc(var(--line-height-normal) + var(--spacing-s));
        }
        gr-avatar-stack {
          --avatar-size: var(--line-height-small, 16px);
          --stack-border-color: var(--warning-background);
        }
        .unresolvedIcon {
          font-size: var(--line-height-small);
          color: var(--warning-foreground);
        }
        /* The basics of .loadingSpin are defined in shared styles. */
        .loadingSpin {
          width: calc(var(--line-height-normal) - 2px);
          height: calc(var(--line-height-normal) - 2px);
          display: inline-block;
          vertical-align: top;
          position: relative;
          /* Making up for the 2px reduced height above. */
          top: 1px;
        }
        .actions {
          margin-left: calc(0px - var(--spacing-m));
          line-height: var(--line-height-normal);
        }
        .actions gr-checks-action,
        .actions gr-dropdown {
          vertical-align: top;
          --gr-button-padding: 0 var(--spacing-m);
        }
        .actions #moreMessage {
          display: none;
        }
        .summaryMessage {
          line-height: var(--line-height-normal);
          color: var(--primary-text-color);
        }
      `,
    ];
  }

  private renderActions() {
    const actions = this.actions ?? [];
    const summaryActions = actions.filter(a => a.summary).slice(0, 2);
    if (summaryActions.length === 0) return;
    const topActions = summaryActions.slice(0, 2);
    const overflowActions = summaryActions.slice(2).map(action => {
      return {...action, id: action.name};
    });
    const disabledActionIds = overflowActions
      .filter(action => action.disabled)
      .map(action => action.id);

    return html`
      <div class="actions">
        ${topActions.map(this.renderAction)}
        ${this.renderOverflow(overflowActions, disabledActionIds)}
      </div>
    `;
  }

  private renderAction(action?: Action) {
    if (!action) return;
    return html`<gr-checks-action
      context="summary"
      .action=${action}
    ></gr-checks-action>`;
  }

  private handleAction(e: CustomEvent<Action>) {
    this.getChecksModel().triggerAction(
      e.detail,
      undefined,
      'summary-dropdown'
    );
  }

  private renderOverflow(items: DropdownLink[], disabledIds: string[] = []) {
    if (items.length === 0) return;
    return html`
      <gr-dropdown
        id="moreActions"
        link=""
        vertical-offset="32"
        horizontal-align="right"
        @tap-item=${this.handleAction}
        .items=${items}
        .disabledIds=${disabledIds}
      >
        <gr-icon icon="more_vert" aria-labelledby="moreMessage"></gr-icon>
        <span id="moreMessage">More</span>
      </gr-dropdown>
    `;
  }

  private renderSummaryMessage() {
    return this.messages.map(
      m => html`
        <div class="info">
          <div class="left">
            <gr-icon icon="info" filled></gr-icon>
          </div>
          <div class="right">
            <div class="message" title=${m}>${m}</div>
          </div>
        </div>
      `
    );
  }

  renderErrorMessages() {
    return Object.entries(this.errorMessages).map(
      ([plugin, message]) =>
        html`
          <div class="error zeroState">
            <div class="left">
              <gr-icon icon="error" filled></gr-icon>
            </div>
            <div class="right">
              <div class="message" title=${message}>
                Error while fetching results for ${plugin}: ${message}
              </div>
            </div>
          </div>
        `
    );
  }

  renderChecksLogin() {
    if (!this.loginCallback) return;
    return html`
      <div class="login">
        <div class="left">
          <gr-icon icon="info"></gr-icon>
          Not logged in
        </div>
        <div class="right">
          <gr-button @click=${this.loginCallback} link>Sign in</gr-button>
        </div>
      </div>
    `;
  }

  renderChecksZeroState() {
    if (Object.keys(this.errorMessages).length > 0) return;
    if (this.loginCallback) return;
    if (this.runs.some(isRunningScheduledOrCompleted)) return;
    const msg = this.someProvidersAreLoading ? 'Loading results' : 'No results';
    return html`<span role="status" class="loading zeroState">${msg}</span>`;
  }

  renderChecksChipForCategory(category: Category) {
    const runs = this.runs.filter(run => {
      if (hasResultsOf(run, category)) return true;
      return category === Category.SUCCESS && hasCompletedWithoutResults(run);
    });
    const count = (run: CheckRun) => getResultsOf(run, category);
    if (category === Category.SUCCESS || category === Category.INFO) {
      return this.renderChecksChipsCollapsed(runs, category, count);
    }
    return this.renderChecksChipsExpanded(runs, category);
  }

  renderChecksChipRunning() {
    const runs = this.runs
      .filter(isRunningOrScheduled)
      .sort(compareByWorstCategory);
    return this.renderChecksChipsExpanded(runs, RunStatus.RUNNING);
  }

  renderChecksChipsExpanded(
    runs: CheckRun[],
    statusOrCategory: RunStatus | Category
  ) {
    if (runs.length === 0) return;
    const showAll = this.showAllChips.get(statusOrCategory) ?? false;
    let count = showAll ? 999 : DETAILS_QUOTA.get(statusOrCategory) ?? 2;
    if (count === runs.length - 1) count = runs.length;
    const more = runs.length - count;
    return html`${runs
      .slice(0, count)
      .map(run =>
        this.renderChecksChipDetailed(run, statusOrCategory)
      )}${this.renderChecksChipPlusMore(statusOrCategory, more)}`;
  }

  private renderChecksChipsCollapsed(
    runs: CheckRun[],
    statusOrCategory: RunStatus | Category,
    resultFilter: (run: CheckRun) => CheckResult[]
  ) {
    const count = runs.reduce(
      (sum, run) => sum + (resultFilter(run).length || 1),
      0
    );
    if (count === 0) return;
    const handler = () => this.onChipClick({statusOrCategory});
    return html`<gr-checks-chip
      .statusOrCategory=${statusOrCategory}
      .text=${`${count}`}
      @click=${handler}
      @keydown=${(e: KeyboardEvent) => handleSpaceOrEnter(e, handler)}
    ></gr-checks-chip>`;
  }

  private renderChecksChipPlusMore(
    statusOrCategory: RunStatus | Category,
    count: number
  ) {
    if (count <= 0) return;
    if (this.showAllChips.get(statusOrCategory) === true) return;
    const handler = () => {
      this.showAllChips.set(statusOrCategory, true);
      this.requestUpdate();
    };
    return html`<gr-checks-chip
      .statusOrCategory=${statusOrCategory}
      .text="+ ${count} more"
      @click=${handler}
      @keydown=${(e: KeyboardEvent) => handleSpaceOrEnter(e, handler)}
    ></gr-checks-chip>`;
  }

  private renderChecksChipDetailed(
    run: CheckRun,
    statusOrCategory: RunStatus | Category
  ) {
    const links = [];
    if (run.statusLink) links.push(run.statusLink);
    const text = `${run.checkName}`;
    const tabState: ChecksTabState = {
      checkName: run.checkName,
      statusOrCategory,
    };
    // Scheduled runs are rendered in the RUNNING section, but the icon of the
    // chip must be the one for SCHEDULED.
    if (
      statusOrCategory === RunStatus.RUNNING &&
      run.status === RunStatus.SCHEDULED
    ) {
      statusOrCategory = RunStatus.SCHEDULED;
    }
    const handler = () => this.onChipClick(tabState);
    return html`<gr-checks-chip
      .statusOrCategory=${statusOrCategory}
      .text=${text}
      .links=${links}
      @click=${handler}
      @keydown=${(e: KeyboardEvent) => handleSpaceOrEnter(e, handler)}
    ></gr-checks-chip>`;
  }

  private onChipClick(state: ChecksTabState) {
    this.reporting.reportInteraction(Interaction.CHECKS_CHIP_CLICKED, {
      statusOrCategory: state.statusOrCategory,
      checkName: state.checkName,
      ...roleDetails(this.getChangeModel().getChange(), this.selfAccount),
    });
    fireShowTab(this, Tab.CHECKS, false, {
      checksTab: state,
    });
  }

  override render() {
    const commentThreads =
      this.commentThreads?.filter(t => !isRobotThread(t) || hasHumanReply(t)) ??
      [];
    const countResolvedComments = commentThreads.filter(isResolved).length;
    const unresolvedThreads = commentThreads.filter(isUnresolved);
    const countUnresolvedComments = unresolvedThreads.length;
    const unresolvedAuthors = this.getAccounts(unresolvedThreads);
    return html`
      <div>
        <table>
          <tr>
            <td class="key">Comments</td>
            <td class="value">
              ${this.renderZeroState(
                countResolvedComments,
                countUnresolvedComments
              )}
              ${this.renderDraftChip()} ${this.renderMentionChip()}
              ${this.renderUnresolvedCommentsChip(
                countUnresolvedComments,
                unresolvedAuthors
              )}
              ${this.renderResolvedCommentsChip(countResolvedComments)}
            </td>
          </tr>
          ${this.renderChecksSummary()}
        </table>
      </div>
    `;
  }

  private renderZeroState(
    countResolvedComments: number,
    countUnresolvedComments: number
  ) {
    if (
      !!countResolvedComments ||
      !!this.draftCount ||
      !!countUnresolvedComments
    )
      return nothing;
    return html`<span class="zeroState"> No comments</span>`;
  }

  private renderMentionChip() {
    if (!this.flagsService.isEnabled(KnownExperimentId.MENTION_USERS))
      return nothing;
    if (!this.mentionCount) return nothing;
    return html` <gr-summary-chip
      class="mentionSummary"
      styleType=${SummaryChipStyles.WARNING}
      category=${CommentTabState.MENTIONS}
      icon="alternate_email"
    >
      ${pluralize(this.mentionCount, 'mention')}</gr-summary-chip
    >`;
  }

  private renderDraftChip() {
    if (!this.draftCount) return nothing;
    return html` <gr-summary-chip
      styleType=${SummaryChipStyles.INFO}
      category=${CommentTabState.DRAFTS}
      icon="rate_review"
      iconFilled
    >
      ${pluralize(this.draftCount, 'draft')}</gr-summary-chip
    >`;
  }

  private renderUnresolvedCommentsChip(
    countUnresolvedComments: number,
    unresolvedAuthors: AccountInfo[]
  ) {
    if (!countUnresolvedComments) return nothing;
    return html` <gr-summary-chip
      styleType=${SummaryChipStyles.WARNING}
      category=${CommentTabState.UNRESOLVED}
      ?hidden=${!countUnresolvedComments}
    >
      <gr-avatar-stack .accounts=${unresolvedAuthors} imageSize="32">
        <gr-icon
          slot="fallback"
          icon="chat_bubble"
          filled
          class="unresolvedIcon"
        >
        </gr-icon>
      </gr-avatar-stack>
      ${countUnresolvedComments} unresolved</gr-summary-chip
    >`;
  }

  private renderResolvedCommentsChip(countResolvedComments: number) {
    if (!countResolvedComments) return nothing;
    return html` <gr-summary-chip
      styleType=${SummaryChipStyles.CHECK}
      category=${CommentTabState.SHOW_ALL}
      icon="mark_chat_read"
      >${countResolvedComments} resolved</gr-summary-chip
    >`;
  }

  private renderChecksSummary() {
    const hasNonRunningChip = this.runs.some(
      run => hasCompletedWithoutResults(run) || hasResults(run)
    );
    const hasRunningChip = this.runs.some(isRunningOrScheduled);
    if (!this.showChecksSummary) return nothing;
    return html` <tr>
      <td class="key">Checks</td>
      <td class="value">
        <div class="checksSummary">
          ${this.renderChecksZeroState()}${this.renderChecksChipForCategory(
            Category.ERROR
          )}${this.renderChecksChipForCategory(
            Category.WARNING
          )}${this.renderChecksChipForCategory(
            Category.INFO
          )}${this.renderChecksChipForCategory(
            Category.SUCCESS
          )}${hasNonRunningChip && hasRunningChip
            ? html`<br />`
            : ''}${this.renderChecksChipRunning()}
          ${when(
            this.someProvidersAreLoading,
            () => html`<span class="loadingSpin"></span>`
          )}
          ${this.renderErrorMessages()} ${this.renderChecksLogin()}
          ${this.renderSummaryMessage()} ${this.renderActions()}
        </div>
      </td>
    </tr>`;
  }

  getAccounts(commentThreads: CommentThread[]): AccountInfo[] {
    return commentThreads
      .map(getFirstComment)
      .map(comment => comment?.author ?? this.selfAccount)
      .filter(notUndefined);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-summary': GrChangeSummary;
  }
}
