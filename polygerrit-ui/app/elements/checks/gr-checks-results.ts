/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../shared/gr-icon/gr-icon';
import {classMap} from 'lit/directives/class-map.js';
import {repeat} from 'lit/directives/repeat.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {
  css,
  html,
  LitElement,
  nothing,
  PropertyValues,
  TemplateResult,
} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import './gr-checks-action';
import './gr-hovercard-run';
import '../shared/gr-tooltip-content/gr-tooltip-content';
import {
  Action,
  Category,
  Link,
  LinkIcon,
  NOT_USEFUL,
  RunStatus,
  Tag,
  USEFUL,
} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {CheckRun, RunResult, runResult} from '../../models/checks/checks-model';
import {
  ALL_ATTEMPTS,
  allResults,
  AttemptChoice,
  attemptChoiceLabel,
  computeIsExpandable,
  createFixAction,
  createGetAiFixAction,
  firstPrimaryLink,
  hasCompletedWithoutResults,
  iconFor,
  iconForLink,
  isAttemptChoice,
  isCategory,
  LATEST_ATTEMPT,
  otherPrimaryLinks,
  rectifyFix,
  secondaryLinks,
  sortAttemptChoices,
  stringToAttemptChoice,
  tooltipForLink,
} from '../../models/checks/checks-util';
import {assert, assertIsDefined, unique} from '../../utils/common-util';
import {modifierPressed, whenVisible} from '../../utils/dom-util';
import {durationString} from '../../utils/date-util';
import {charsOnly} from '../../utils/string-util';
import {isAttemptSelected, matches} from './gr-checks-util';
import {ChecksTabState, ValueChangedEvent} from '../../types/events';
import {
  DropdownLink,
  LabelNameToInfoMap,
  PARENT,
  PatchSetNumber,
} from '../../types/common';
import {spinnerStyles} from '../../styles/gr-spinner-styles';
import {
  getLabelStatus,
  getRepresentativeValue,
  valueString,
} from '../../utils/label-util';
import {subscribe} from '../lit/subscription-controller';
import {fontStyles} from '../../styles/gr-font-styles';
import {fire, fireAlert} from '../../utils/event-util';
import {resolve} from '../../models/dependency';
import {checksModelToken} from '../../models/checks/checks-model';
import {Interaction} from '../../constants/reporting';
import {Deduping} from '../../api/reporting';
import {changeModelToken} from '../../models/change/change-model';
import {getAppContext} from '../../services/app-context';
import {when} from 'lit/directives/when.js';
import {DropdownItem} from '../shared/gr-dropdown-list/gr-dropdown-list';
import './gr-checks-attempt';
import './gr-checks-fix-preview';
import {changeViewModelToken} from '../../models/views/change';
import {formStyles} from '../../styles/form-styles';
import {isDefined} from '../../types/types';
import {KnownExperimentId} from '../../services/flags/flags';
import {
  ReportSource,
  suggestionsServiceToken,
} from '../../services/suggestions/suggestions-service';
import {FixSuggestionInfo, RevisionPatchSetNum} from '../../api/rest-api';

/**
 * Firing this event sets the regular expression of the results filter.
 */
export interface ChecksResultsFilterDetail {
  filterRegExp?: string;
}
export type ChecksResultsFilterEvent = CustomEvent<ChecksResultsFilterDetail>;

declare global {
  interface HTMLElementEventMap {
    'checks-results-filter': ChecksResultsFilterEvent;
    'get-ai-fix-for-check-result': CustomEvent;
  }
}

@customElement('gr-result-row')
export class GrResultRow extends LitElement {
  @query('td.nameCol div.name')
  nameEl?: HTMLElement;

  @property({attribute: false})
  result?: RunResult;

  @state()
  isExpanded = false;

  @property({type: Boolean, reflect: true})
  isExpandable = false;

  @state()
  shouldRender = false;

  @state()
  labels?: LabelNameToInfoMap;

  @state()
  latestPatchNum?: PatchSetNumber;

  @state()
  selectedAttempt: AttemptChoice = LATEST_ATTEMPT;

  @state()
  isOwner = false;

  @state()
  suggestionLoading = false;

  @state()
  suggestion?: FixSuggestionInfo;

  private getChangeModel = resolve(this, changeModelToken);

  private getChecksModel = resolve(this, checksModelToken);

  private readonly reporting = getAppContext().reportingService;

  private readonly getSuggestionsService = resolve(
    this,
    suggestionsServiceToken
  );

  private readonly flagsService = getAppContext().flagsService;

  constructor() {
    super();
    this.addEventListener('get-ai-fix-for-check-result', () => {
      this.handleAIFix();
    });
    subscribe(
      this,
      () => this.getChangeModel().labels$,
      x => (this.labels = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchNum = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedAttemptNumber$,
      x => (this.selectedAttempt = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().isOwner$,
      x => (this.isOwner = x)
    );
    subscribe(
      this,
      () => this.getSuggestionsService().suggestionsServiceUpdated$,
      updated => {
        if (updated) {
          this.requestUpdate();
        }
      }
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: contents;
        }
        :host([isexpandable]) {
          cursor: pointer;
        }
        gr-result-expanded {
          cursor: default;
        }
        tr.container {
          border-top: 1px solid var(--border-color);
        }
        a.link {
          margin-right: var(--spacing-s);
        }
        gr-icon.link {
          color: var(--link-color);
        }
        td.nameCol div.flex {
          display: flex;
        }
        td.nameCol .name {
          overflow: hidden;
          text-overflow: ellipsis;
          margin-right: var(--spacing-s);
          outline-offset: var(--spacing-xs);
        }
        td.nameCol .space {
          flex-grow: 1;
        }
        td.nameCol gr-checks-action {
          display: none;
        }
        tr:focus-within td.nameCol gr-checks-action,
        tr:hover td.nameCol gr-checks-action {
          display: inline-block;
          /* The button should fit into the 20px line-height. The negative
             margin provides the extra space needed for the vertical padding.
             Alternatively we could have set the vertical padding to 0, but
             that would not have been a nice click target. */
          margin: calc(0px - var(--spacing-s)) 0px;
          margin-left: var(--spacing-s);
        }
        td {
          white-space: nowrap;
          padding: var(--spacing-s);
        }
        td.expandedCol,
        td.nameCol {
          padding-left: var(--spacing-l);
        }
        td.expandedCol,
        td.expanderCol {
          padding-right: var(--spacing-l);
        }
        td .summary-cell {
          display: flex;
        }
        td .summary-cell .summary {
          font-weight: var(--font-weight-medium);
          flex-shrink: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          margin-right: var(--spacing-s);
        }
        td .summary-cell .message {
          flex-grow: 1;
          /* Looks a bit stupid, but the idea is that .message shrinks first,
             and only when that has shrunken to 0, then .summary should also
             start shrinking (substantially). */
          flex-shrink: 1000000;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        tr.container:hover {
          background: var(--hover-background-color);
        }
        tr.container:focus-within {
          background: var(--selection-background-color);
        }
        tr.container td .summary-cell .links,
        tr.container td .summary-cell .actions,
        tr.container.collapsed:focus-within td .summary-cell .links,
        tr.container.collapsed:focus-within td .summary-cell .actions,
        tr.container.collapsed:hover td .summary-cell .links,
        tr.container.collapsed:hover td .summary-cell .actions,
        :host(.dropdown-open) tr td .summary-cell .links,
        :host(.dropdown-open) tr td .summary-cell .actions {
          display: inline-block;
          margin-left: var(--spacing-s);
        }
        /* actions-shown-on-collapsed are shown only when .actions is hidden
          and vice versa. */
        tr.container td .summary-cell .actions-shown-on-collapsed,
        tr.container.collapsed:focus-within
          td
          .summary-cell
          .actions-shown-on-collapsed,
        tr.container.collapsed:hover
          td
          .summary-cell
          .actions-shown-on-collapsed,
        :host(.dropdown-open) tr td .summary-cell .actions-shown-on-collapsed {
          display: none;
        }
        tr.container.collapsed td .summary-cell .message {
          color: var(--deemphasized-text-color);
        }
        tr.container.collapsed td .summary-cell .links,
        tr.container.collapsed td .summary-cell .actions {
          display: none;
        }
        tr.container.collapsed td .summary-cell .actions-shown-on-collapsed {
          display: inline-block;
          margin-left: var(--spacing-s);
        }
        tr.detailsRow.collapsed {
          display: none;
        }
        td .summary-cell .tags .tag {
          color: var(--primary-text-color);
          display: inline-block;
          border-radius: 20px;
          background-color: var(--tag-background);
          padding: 0 var(--spacing-m);
          margin-left: var(--spacing-s);
          cursor: pointer;
        }
        td .summary-cell .tag.gray {
          background-color: var(--tag-gray);
        }
        td .summary-cell .tag.yellow {
          background-color: var(--tag-yellow);
        }
        td .summary-cell .tag.pink {
          background-color: var(--tag-pink);
        }
        td .summary-cell .tag.purple {
          background-color: var(--tag-purple);
        }
        td .summary-cell .tag.cyan {
          background-color: var(--tag-cyan);
        }
        td .summary-cell .tag.brown {
          background-color: var(--tag-brown);
        }
        .actions-shown-on-collapsed gr-checks-action,
        .actions gr-checks-action,
        .actions gr-dropdown {
          /* Fitting a 28px button into 20px line-height. */
          margin: -4px 0;
          vertical-align: top;
        }
        #moreActions gr-icon {
          color: var(--link-color);
        }
        #moreMessage {
          display: none;
        }
        td .summary-cell .label {
          margin-left: var(--spacing-s);
          border-radius: var(--border-radius);
          color: var(--vote-text-color);
          display: inline-block;
          padding: 0 var(--spacing-s);
          text-align: center;
        }
        td .summary-cell .label.neutral {
          background-color: var(--vote-color-neutral);
        }
        td .summary-cell .label.recommended,
        td .summary-cell .label.disliked {
          line-height: calc(var(--line-height-normal) - 2px);
          color: var(--chip-color);
        }
        td .summary-cell .label.recommended {
          background-color: var(--vote-color-recommended);
          border: 1px solid var(--vote-outline-recommended);
        }
        td .summary-cell .label.disliked {
          background-color: var(--vote-color-disliked);
          border: 1px solid var(--vote-outline-disliked);
        }
        td .summary-cell .label.approved {
          background-color: var(--vote-color-approved);
        }
        td .summary-cell .label.rejected {
          background-color: var(--vote-color-rejected);
        }
      `,
    ];
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('result')) {
      this.isExpandable = computeIsExpandable(this.result);
    }
  }

  override focus() {
    if (this.nameEl) this.nameEl.focus();
  }

  override firstUpdated() {
    const loading = this.shadowRoot?.querySelector('.container');
    assertIsDefined(loading, '"Loading" element');
    whenVisible(
      loading,
      () => {
        this.shouldRender = true;
      },
      200
    );
  }

  override render() {
    if (!this.result) return '';
    if (!this.shouldRender) {
      return html`
        <tr class="container">
          <td class="nameCol">
            <div><span class="loading">Loading...</span></div>
          </td>
          <td class="summaryCol"></td>
          <td class="expanderCol"></td>
        </tr>
      `;
    }
    return html`
      <tr class=${classMap({container: true, collapsed: !this.isExpanded})}>
        <td class="nameCol" @click=${this.toggleExpandedClick}>
          <div class="flex">
            <gr-hovercard-run .run=${this.result}></gr-hovercard-run>
            <div
              class="name"
              role="button"
              tabindex="0"
              @click=${this.toggleExpandedClick}
              @keydown=${this.toggleExpandedPress}
            >
              ${this.result.checkName}
            </div>
            ${this.renderAttempt()}
            <div class="space"></div>
          </div>
        </td>
        <td class="summaryCol">
          <div class="summary-cell">
            ${this.renderLink(firstPrimaryLink(this.result))}
            ${this.renderSummary(this.result.summary)}
            <div class="message" @click=${this.toggleExpandedClick}>
              ${this.isExpanded ? '' : this.result.message}
            </div>
            ${this.renderLinks()} ${this.renderActions()}
            <div class="tags">
              ${(this.result.tags ?? []).map(t => this.renderTag(t))}
            </div>
            ${this.renderLabel()}
          </div>
        </td>
        <td class="expanderCol" @click=${this.toggleExpandedClick}>
          <div
            class="show-hide"
            role="switch"
            tabindex="0"
            ?hidden=${!this.isExpandable}
            aria-checked=${this.isExpanded ? 'true' : 'false'}
            aria-label=${this.isExpanded
              ? 'Collapse result row'
              : 'Expand result row'}
            @keydown=${this.toggleExpandedPress}
          >
            <gr-icon
              icon=${this.isExpanded ? 'expand_less' : 'expand_more'}
            ></gr-icon>
          </div>
        </td>
      </tr>
      <tr class=${classMap({detailsRow: true, collapsed: !this.isExpanded})}>
        <td class="expandedCol" colspan="3">${this.renderExpanded()}</td>
      </tr>
    `;
  }

  private renderAttempt() {
    if (this.selectedAttempt !== ALL_ATTEMPTS) return nothing;
    return html`<gr-checks-attempt .run=${this.result}></gr-checks-attempt>`;
  }

  private renderExpanded() {
    if (!this.isExpanded) return;
    return html`<gr-result-expanded .result=${this.result}></gr-result-expanded>
      ${this.renderSuggestionPreview()}`;
  }

  private toggleExpandedClick(e: MouseEvent) {
    if (!this.isExpandable) return;
    e.preventDefault();
    e.stopPropagation();
    this.toggleExpanded();
  }

  private tagClick(e: MouseEvent, tagName: string) {
    e.preventDefault();
    e.stopPropagation();
    this.reporting.reportInteraction(Interaction.CHECKS_TAG_CLICKED, {
      tagName,
      checkName: this.result?.checkName,
    });
    fire(this, 'checks-results-filter', {filterRegExp: tagName});
  }

  private toggleExpandedPress(e: KeyboardEvent) {
    if (!this.isExpandable) return;
    if (modifierPressed(e)) return;
    if (e.key !== 'Enter' && e.key !== ' ') return;
    e.preventDefault();
    e.stopPropagation();
    this.toggleExpanded();
  }

  /** Toggles the expanded state, or if `setExpanded` is provided sets it to the desired state. */
  toggleExpanded(setExpanded?: boolean) {
    if (!this.isExpandable) return;
    this.isExpanded =
      setExpanded === undefined ? !this.isExpanded : setExpanded;
    this.reporting.reportInteraction(Interaction.CHECKS_RESULT_ROW_TOGGLE, {
      expanded: this.isExpanded,
      checkName: this.result?.checkName,
    });
  }

  renderSummary(text?: string) {
    text = text ?? '';
    return html`
      <!-- The &nbsp; is for being able to shrink a tiny amount without
       the text itself getting shrunk with an ellipsis. -->
      <div class="summary" @click=${this.toggleExpandedClick} title=${text}>
        ${text}&nbsp;
      </div>
    `;
  }

  renderLabel() {
    const category = this.result?.category;
    if (category !== Category.ERROR && category !== Category.WARNING) return;
    const label = this.result?.labelName;
    if (!label) return;
    if (!this.result?.isLatestAttempt) return;
    // For check results on older patchsets it is impossible to decide whether
    // the current label score is still influenced by them. But typically it
    // is really confusing for the user, if we claim that an old (error) result
    // influences the current (positive) score. So we prefer to be conservative
    // and only display the label chip for checks results on the latest ps.
    if (this.result.patchset !== this.latestPatchNum) return;
    const info = this.labels?.[label];
    const status = getLabelStatus(info).toLowerCase();
    const value = getRepresentativeValue(info);
    // A neutral vote is not interesting for the user to see and is just
    // cluttering the UI.
    if (value === 0) return;
    const valueStr = valueString(value);
    return html`
      <gr-tooltip-content
        has-tooltip
        ?position-below=${true}
        title="The check result has (probably) influenced this label vote."
      >
        <div class="label ${status}">
          <span>${label} ${valueStr}</span>
        </div>
      </gr-tooltip-content>
    `;
  }

  renderLinks() {
    const links = otherPrimaryLinks(this.result)
      // Showing the same icons twice without text is super confusing.
      .filter(
        (link: Link, index: number, array: Link[]) =>
          array.findIndex(other => link.icon === other.icon) === index
      )
      // 4 is enough for the summary row. All are shown in expanded state.
      .slice(0, 4);
    if (links.length === 0) return;
    return html`<div class="links">
      ${links.map(link => this.renderLink(link))}
    </div>`;
  }

  renderLink(link?: Link) {
    // The expanded state renders all links in more detail. Hide in summary.
    if (this.isExpanded) return;
    if (!link) return;
    const tooltipText = link.tooltip ?? tooltipForLink(link.icon);
    const icon = iconForLink(link.icon);
    return html`<gr-tooltip-content
      has-tooltip
      ?position-below=${true}
      title=${tooltipText}
    >
      <a
        href=${link.url}
        class="link"
        target="_blank"
        rel="noopener noreferrer"
      >
        <gr-icon
          icon=${icon.name}
          ?filled=${!!icon.filled}
          aria-label="external link to details"
          class="link"
        ></gr-icon>
      </a>
    </gr-tooltip-content>`;
  }

  private renderActions() {
    const actions = [...(this.result?.actions ?? [])].filter(
      action => action.name !== USEFUL && action.name !== NOT_USEFUL
    );
    let fixAction: Action | undefined = undefined;
    let getAiFixAction: Action | undefined = undefined;
    if (!this.isExpanded) {
      fixAction = createFixAction(this, this.result);
      if (fixAction) {
        actions.unshift(fixAction);
      }
    }

    if (
      this.flagsService.isEnabled(KnownExperimentId.GET_AI_FIX) &&
      this.getSuggestionsService()?.isGeneratedSuggestedFixEnabled(
        this.result?.codePointers?.[0]?.path
      ) &&
      this.isOwner &&
      // without fixes
      !this.result?.fixes?.length &&
      // disable on codepointer with range pointing to 0 line
      this.result?.codePointers?.[0]?.range.start_line !== 0 &&
      this.result?.codePointers?.[0]?.range.end_line !== 0 &&
      this.result?.category !== Category.SUCCESS &&
      this.result?.category !== Category.INFO &&
      !fixAction
    ) {
      getAiFixAction = createGetAiFixAction(this);
      if (getAiFixAction) {
        actions.unshift(getAiFixAction);
      }
    }
    if (actions.length === 0) return;
    const overflowItems = actions.slice(2).map(action => {
      return {...action, id: action.name};
    });
    const disabledItems = overflowItems
      .filter(action => action.disabled)
      .map(action => action.id);
    return html` ${when(
        fixAction || getAiFixAction,
        () =>
          html`<div class="actions-shown-on-collapsed">
            ${this.renderAction(fixAction || getAiFixAction)}
          </div> `
      )}
      <div class="actions">
        ${this.renderAction(actions[0])} ${this.renderAction(actions[1])}
        <gr-dropdown
          id="moreActions"
          link=""
          vertical-offset="32"
          horizontal-align="right"
          @tap-item=${this.handleAction}
          @opened-changed=${(e: ValueChangedEvent<boolean>) =>
            this.classList.toggle('dropdown-open', e.detail.value)}
          ?hidden=${overflowItems.length === 0}
          .items=${overflowItems}
          .disabledIds=${disabledItems}
        >
          <gr-icon icon="more_vert" aria-labelledby="moreMessage"></gr-icon>
          <span id="moreMessage">More</span>
        </gr-dropdown>
      </div>`;
  }

  private handleAction(e: CustomEvent<Action>) {
    this.getChecksModel().triggerAction(
      e.detail,
      this.result,
      'result-row-dropdown'
    );
  }

  private renderAction(action?: Action) {
    if (!action) return;
    return html`<gr-checks-action
      context="result-row"
      .action=${action}
    ></gr-checks-action>`;
  }

  renderTag(tag: Tag) {
    return html`<gr-tooltip-content
      has-tooltip
      ?position-below=${true}
      title=${tag.tooltip ??
      'A category tag for this check result. Click to filter.'}
    >
      <button
        class="tag ${tag.color}"
        @click=${(e: MouseEvent) => this.tagClick(e, tag.name)}
      >
        <span>${tag.name}</span>
      </button>
    </gr-tooltip-content>`;
  }

  private renderSuggestionPreview() {
    if (!this.suggestion) return nothing;
    return html`<gr-checks-fix-preview
      .fixSuggestionInfos=${[this.suggestion]}
      .patchSet=${this.result?.patchset}
    ></gr-checks-fix-preview>`;
  }

  private async handleAIFix(): Promise<void> {
    const codePointer = this.result?.codePointers?.[0];
    if (!this.result || !this.result.message || !codePointer) return;

    this.suggestionLoading = true;
    let suggestion: FixSuggestionInfo | undefined;
    try {
      suggestion = await this.getSuggestionsService().generateSuggestedFix({
        prompt: this.result.message,
        patchsetNumber: this.result.patchset as RevisionPatchSetNum,
        filePath: codePointer.path,
        range: codePointer.range,
        reportSource: ReportSource.GET_AI_FIX_FOR_CHECK,
      });
    } finally {
      this.suggestionLoading = false;
    }
    if (!suggestion) {
      fireAlert(this, 'No suitable AI fix could be found');
      return;
    }
    suggestion.description =
      ReportSource.GET_AI_FIX_FOR_CHECK + ' ' + suggestion.description;
    this.suggestion = suggestion;
    this.toggleExpanded(/* setExpanded= */ true);
  }
}

@customElement('gr-result-expanded')
export class GrResultExpanded extends LitElement {
  @property({attribute: false})
  result?: RunResult;

  @property({type: Boolean})
  hideCodePointers = false;

  @state()
  notUsefulLabel = 'Was this helpful?';

  private getChangeModel = resolve(this, changeModelToken);

  private readonly getViewModel = resolve(this, changeViewModelToken);

  static override get styles() {
    return [
      sharedStyles,
      css`
        .links {
          white-space: normal;
        }
        .links a {
          display: inline-block;
          margin-right: var(--spacing-xl);
        }
        .links a gr-icon {
          margin-right: var(--spacing-xs);
        }
        .message {
          padding: var(--spacing-m) 0;
        }
        gr-checks-fix-preview {
          margin: var(--spacing-l) 0;
          max-width: 800px;
        }
        .useful {
          display: flex;
          justify-content: flex-end;
          align-items: center;
          margin: var(--spacing-s) 0;
        }
        .useful .title {
          margin-right: var(--spacing-s);
        }
        .useful gr-checks-action {
          display: block;
        }
      `,
    ];
  }

  override render() {
    if (!this.result) return '';
    return html`
      ${this.renderFirstPrimaryLink()} ${this.renderOtherPrimaryLinks()}
      ${this.renderSecondaryLinks()} ${this.renderCodePointers()}
      <gr-endpoint-decorator
        name="check-result-expanded"
        .targetPlugin=${this.result.pluginName}
      >
        <gr-endpoint-param name="run" .value=${this.result}></gr-endpoint-param>
        <gr-endpoint-param
          name="result"
          .value=${this.result}
        ></gr-endpoint-param>
        <gr-formatted-text
          class="message"
          .markdown=${true}
          .content=${this.result.message ?? ''}
        ></gr-formatted-text>
      </gr-endpoint-decorator>
      ${this.renderFix()} ${this.renderNotUseful()}
    `;
  }

  private renderFirstPrimaryLink() {
    const link = firstPrimaryLink(this.result);
    if (!link) return;
    return html`<div class="links">${this.renderLink(link)}</div>`;
  }

  private renderOtherPrimaryLinks() {
    const links = otherPrimaryLinks(this.result);
    if (links.length === 0) return;
    return html`<div class="links">
      ${links.map(link => this.renderLink(link))}
    </div>`;
  }

  private renderSecondaryLinks() {
    const links = secondaryLinks(this.result);
    if (links.length === 0) return;
    return html`<div class="links">
      ${links.map(link => this.renderLink(link))}
    </div>`;
  }

  private renderCodePointers() {
    if (this.hideCodePointers) return;
    const pointers = this.result?.codePointers ?? [];
    if (pointers.length === 0) return;
    const links = pointers.map(pointer => {
      let rangeText = '';
      const start = pointer?.range?.start_line;
      const end = pointer?.range?.end_line;
      if (start) rangeText += `#${start}`;
      if (end && start !== end) rangeText += `-${end}`;
      const change = this.getChangeModel().getChange();
      if (!change) return undefined;
      const path = pointer.path;
      const patchset = this.result?.patchset as PatchSetNumber;
      const line = pointer?.range?.start_line;
      return {
        icon: LinkIcon.CODE,
        tooltip: `${path}${rangeText}`,
        url: this.getViewModel().diffUrl({
          basePatchNum: PARENT,
          patchNum: patchset,
          checksPatchset: patchset,
          diffView: {path, lineNum: line},
        }),
        primary: true,
      };
    });
    return links.map(
      link => html`<div class="links">${this.renderLink(link, false)}</div>`
    );
  }

  private renderFix() {
    const fixSuggestionInfos =
      this.result?.fixes
        ?.map(fix => rectifyFix(fix, this.result?.checkName))
        .filter(isDefined) ?? [];
    if (fixSuggestionInfos.length === 0) return;
    return html`
      <gr-checks-fix-preview
        .fixSuggestionInfos=${fixSuggestionInfos}
        .patchSet=${this.result?.patchset}
      ></gr-checks-fix-preview>
    `;
  }

  private renderNotUseful() {
    const actions = this.result?.actions ?? [];
    const useful = actions.find(a => a.name === USEFUL);
    const notUseful = actions.find(a => a.name === NOT_USEFUL);
    if (!useful || !notUseful) return;
    return html`
      <div class="useful">
        <div class="title">${this.notUsefulLabel}</div>
        <gr-checks-action
          @click=${() => (this.notUsefulLabel = 'Thanks!')}
          .action=${useful}
        ></gr-checks-action>
        <gr-checks-action
          @click=${() => (this.notUsefulLabel = 'Sorry about that')}
          .action=${notUseful}
        ></gr-checks-action>
      </div>
    `;
  }

  private renderLink(link?: Link, targetBlank = true) {
    if (!link) return;
    const text = link.tooltip ?? tooltipForLink(link.icon);
    const target = targetBlank ? '_blank' : undefined;
    const icon = iconForLink(link.icon);
    return html`<a
      href=${link.url}
      target=${ifDefined(target)}
      rel="noopener noreferrer"
    >
      <gr-icon
        icon=${icon.name}
        class="link"
        ?filled=${!!icon.filled}
      ></gr-icon>
      <span>${text}</span>
    </a>`;
  }
}

const CATEGORY_TOOLTIPS: Map<Category, string> = new Map();
CATEGORY_TOOLTIPS.set(Category.ERROR, 'Must be fixed and is blocking submit');
CATEGORY_TOOLTIPS.set(
  Category.WARNING,
  'Should be checked but is not blocking submit'
);
CATEGORY_TOOLTIPS.set(
  Category.INFO,
  'Does not have to be checked, for your information only'
);
CATEGORY_TOOLTIPS.set(
  Category.SUCCESS,
  'Successful runs without results and individual successful results'
);

@customElement('gr-checks-results')
export class GrChecksResults extends LitElement {
  @query('#filterInput')
  filterInput?: HTMLInputElement;

  @state()
  filterRegExp = '';

  /** All runs. Shown should only the selected/filtered ones. */
  @property({attribute: false})
  runs: CheckRun[] = [];

  /**
   * Check names of runs that are selected in the runs panel. When this array
   * is empty, then no run is selected and all runs should be shown.
   */
  @state()
  selectedRuns: Set<string> = new Set();

  @state()
  actions: Action[] = [];

  @state()
  links: Link[] = [];

  @property({attribute: false})
  tabState?: ChecksTabState;

  @state()
  someProvidersAreLoading = false;

  @state()
  checksPatchsetNumber: PatchSetNumber | undefined = undefined;

  @state()
  latestPatchsetNumber: PatchSetNumber | undefined = undefined;

  @state()
  selectedAttempt: AttemptChoice = LATEST_ATTEMPT;

  /** Maintains the state of which result sections should show all results. */
  @state()
  isShowAll: Map<Category, boolean> = new Map();

  /** Maintains the state of which result sections has all results expanded. */
  @state()
  allResultsExpanded: Map<Category, boolean> = new Map();

  /**
   * This is the current state of whether a section is expanded or not. As long
   * as isSectionExpandedByUser is false this will be computed by a default rule
   * on every render.
   */
  private isSectionExpanded = new Map<Category, boolean>();

  /**
   * Keeps track of whether the user intentionally changed the expansion state.
   * Once this is true the default rule for showing a section expanded or not
   * is not applied anymore.
   */
  private isSectionExpandedByUser = new Map<Category, boolean>();

  private readonly getViewModel = resolve(this, changeViewModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getChecksModel = resolve(this, checksModelToken);

  private readonly reporting = getAppContext().reportingService;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().topLevelActionsSelected$,
      x => (this.actions = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().topLevelLinksSelected$,
      x => (this.links = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedPatchsetNumber$,
      x => (this.checksPatchsetNumber = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedAttemptNumber$,
      x => (this.selectedAttempt = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchsetNumber = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().someProvidersAreLoadingSelected$,
      x => (this.someProvidersAreLoading = x)
    );
    subscribe(
      this,
      () => this.getViewModel().checksRunsSelected$,
      x => (this.selectedRuns = x)
    );
    subscribe(
      this,
      () => this.getViewModel().checksResultsFilter$,
      x => (this.filterRegExp = x)
    );
  }

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      spinnerStyles,
      fontStyles,
      css`
        :host {
          display: block;
          background-color: var(--background-color-secondary);
        }
        .header {
          display: block;
          background-color: var(--background-color-primary);
          padding: var(--spacing-l) var(--spacing-xl) var(--spacing-m)
            var(--spacing-xl);
          border-bottom: 1px solid var(--border-color);
        }
        .header.notLatest {
          background-color: var(--emphasis-color);
        }
        .headerTopRow,
        .headerBottomRow {
          display: flex;
          justify-content: space-between;
          align-items: flex-end;
        }
        .headerTopRow gr-dropdown-list {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          padding: 0 var(--spacing-m);
        }
        .headerTopRow h2 {
          display: inline-block;
        }
        .headerTopRow .loading {
          display: inline-block;
          margin-left: var(--spacing-m);
          color: var(--deemphasized-text-color);
        }
        /* The basics of .loadingSpin are defined in shared styles. */
        .headerTopRow .loadingSpin {
          display: inline-block;
          margin-left: var(--spacing-s);
          width: 18px;
          height: 18px;
          vertical-align: top;
        }
        .headerBottomRow {
          margin-top: var(--spacing-s);
        }
        .headerTopRow .right,
        .headerBottomRow .right {
          display: flex;
          align-items: center;
        }
        .headerTopRow .right .goToLatest {
          display: none;
        }
        .notLatest .headerTopRow .right .goToLatest {
          display: block;
        }
        .headerTopRow .right > * {
          margin-left: var(--spacing-m);
        }
        .headerTopRow .right .goToLatest gr-button {
          --gr-button-padding: var(--spacing-s) var(--spacing-m);
        }
        .headerBottomRow gr-icon {
          color: var(--link-color);
        }
        .headerBottomRow .space {
          display: inline-block;
          width: var(--spacing-xl);
          height: var(--line-height-normal);
        }
        .headerBottomRow a {
          margin-right: var(--spacing-l);
        }
        #moreActions gr-icon {
          color: var(--link-color);
        }
        #moreMessage {
          display: none;
        }
        .body {
          display: block;
          padding: var(--spacing-s) var(--spacing-xl) var(--spacing-xl)
            var(--spacing-xl);
        }
        .filterDiv {
          display: flex;
          margin-top: var(--spacing-s);
          align-items: center;
        }
        .filterDiv input#filterInput {
          padding: var(--spacing-s) var(--spacing-m);
          min-width: 400px;
        }
        .filterDiv .selection {
          padding: var(--spacing-s) var(--spacing-m);
        }
        .categoryHeader {
          display: flex;
          justify-content: space-between;
          align-items: flex-end;
          margin-top: var(--spacing-l);
          margin-left: var(--spacing-l);
          cursor: default;
        }
        .categoryHeader .title {
          text-transform: capitalize;
        }
        .categoryHeader .expandIcon {
          width: var(--line-height-h3);
          height: var(--line-height-h3);
          margin-right: var(--spacing-s);
        }
        .categoryHeader .statusIconWrapper {
          display: inline-block;
        }
        .categoryHeader .statusIcon {
          position: relative;
          top: 2px;
        }
        .categoryHeader .statusIcon.error {
          color: var(--error-foreground);
        }
        .categoryHeader .statusIcon.warning {
          color: var(--warning-foreground);
        }
        .categoryHeader .statusIcon.info {
          color: var(--info-foreground);
        }
        .categoryHeader .statusIcon.success {
          color: var(--success-foreground);
        }
        .categoryHeader.empty gr-icon.statusIcon {
          color: var(--deemphasized-text-color);
        }
        .categoryHeader .filtered {
          color: var(--deemphasized-text-color);
        }
        .collapsed .noResultsMessage,
        .collapsed table {
          display: none;
        }
        .collapsed {
          border-bottom: 1px solid var(--border-color);
          padding-bottom: var(--spacing-m);
        }
        .noResultsMessage {
          width: 100%;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
          padding: var(--spacing-s)
            calc(20px + var(--spacing-l) + var(--spacing-m) + var(--spacing-s));
        }
        table.resultsTable {
          width: 100%;
          table-layout: fixed;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
        }
        tr.headerRow th {
          text-align: left;
          font-weight: var(--font-weight-medium);
          padding: var(--spacing-s);
        }
        tr.headerRow th.nameCol {
          padding-left: var(--spacing-l);
          width: 200px;
        }
        @media screen and (min-width: 1400px) {
          tr.headerRow th.nameCol.longNames {
            width: 300px;
          }
        }
        tr.headerRow th.summaryCol {
          width: 99%;
        }
        tr.headerRow th.expanderCol {
          width: 30px;
          padding-right: var(--spacing-l);
        }

        gr-button.showAll {
          margin: var(--spacing-m);
        }
        tr {
          border-top: 1px solid var(--border-color);
        }
      `,
    ];
  }

  protected override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('filterRegExp') && this.filterInput) {
      this.filterInput.value = this.filterRegExp;
    }
    if (changedProperties.has('tabState') && this.tabState) {
      const {statusOrCategory, checkName} = this.tabState;
      if (isCategory(statusOrCategory)) {
        const expanded = this.isSectionExpanded.get(statusOrCategory);
        if (!expanded) this.toggleExpanded(statusOrCategory);
      }
      if (checkName) {
        this.scrollElIntoView(`gr-result-row.${charsOnly(checkName)}`);
      } else if (
        statusOrCategory &&
        statusOrCategory !== RunStatus.RUNNING &&
        statusOrCategory !== RunStatus.RUNNABLE
      ) {
        const cat = statusOrCategory.toString().toLowerCase();
        this.scrollElIntoView(`.categoryHeader.${cat} + table gr-result-row`);
      }
    }
  }

  private scrollElIntoView(selector: string) {
    this.updateComplete.then(() => {
      let el = this.shadowRoot?.querySelector(selector);
      // el might be a <gr-result-row> with an empty shadowRoot. Let's wait a
      // moment before trying to find a child element in it.
      setTimeout(() => {
        if (el) (el as HTMLElement).focus();
        // If the target element is a <gr-result-row>, then expand it.
        (el as GrResultRow)?.toggleExpanded(true);
        // <gr-result-row> has display:contents and cannot be scrolled into view
        // itself. Thus we are preferring to scroll the first child into view.
        el = el?.shadowRoot?.firstElementChild ?? el;
        el?.scrollIntoView({block: 'center'});
      }, 0);
    });
  }

  override render() {
    const headerClasses = {
      header: true,
      notLatest:
        !!this.checksPatchsetNumber &&
        this.checksPatchsetNumber !== this.latestPatchsetNumber,
    };
    const attemptItems = this.createAttemptDropdownItems();
    return html`
      <div class=${classMap(headerClasses)}>
        <div class="headerTopRow">
          <div class="left">
            <h2 class="heading-2">Results</h2>
            <div class="loading" ?hidden=${!this.someProvidersAreLoading}>
              <span>Loading results </span>
              <span class="loadingSpin"></span>
            </div>
          </div>
          <div class="right">
            <div class="goToLatest">
              <gr-button @click=${this.goToLatestPatchset} link
                >Go To Latest Patchset</gr-button
              >
            </div>
            ${when(
              attemptItems.length > 0,
              () => html` <gr-dropdown-list
                value=${this.selectedAttempt ?? 0}
                .items=${attemptItems}
                @value-change=${this.onAttemptSelected}
              ></gr-dropdown-list>`
            )}
            <gr-dropdown-list
              value=${(this.checksPatchsetNumber ||
                this.latestPatchsetNumber) ??
              0}
              .items=${this.createPatchsetDropdownItems()}
              @value-change=${this.onPatchsetSelected}
            ></gr-dropdown-list>
          </div>
        </div>
        <div class="headerBottomRow">
          <div class="left">${this.renderFilter()}</div>
          <div class="right">${this.renderLinksAndActions()}</div>
        </div>
      </div>
      <div class="body">
        ${this.renderSection(Category.ERROR)}
        ${this.renderSection(Category.WARNING)}
        ${this.renderSection(Category.INFO)}
        ${this.renderSection(Category.SUCCESS)}
      </div>
    `;
  }

  private renderLinksAndActions() {
    const links = this.links ?? [];
    const primaryLinks = links
      .filter(a => a.primary)
      // Showing the same icons twice without text is super confusing.
      .filter(
        (link: Link, index: number, array: Link[]) =>
          array.findIndex(other => link.icon === other.icon) === index
      )
      .slice(0, 4);
    const overflowLinks = links.filter(a => !primaryLinks.includes(a));
    const overflowLinkItems: DropdownLink[] = overflowLinks.map(link => {
      return {
        ...link,
        id: link.tooltip,
        name: link.tooltip,
        target: '_blank',
        tooltip: undefined,
      };
    });

    const actions = this.actions ?? [];
    const primaryActions = actions.filter(a => a.primary).slice(0, 2);
    const overflowActions = actions.filter(a => !primaryActions.includes(a));
    const overflowActionItems = overflowActions.map(action => {
      return {...action, id: action.name};
    });
    const disabledActions = overflowActionItems
      .filter(action => action.disabled)
      .map(action => action.id);

    return html`
      ${primaryLinks.map(this.renderLink)}
      ${primaryLinks.length > 0 && primaryActions.length > 0
        ? html`<div class="space"></div>`
        : ''}
      ${primaryActions.map(this.renderAction)}
      ${this.renderOverflow(
        [...overflowLinkItems, ...overflowActionItems],
        disabledActions
      )}
    `;
  }

  private renderLink(link?: Link) {
    if (!link) return;
    const tooltipText = link.tooltip ?? tooltipForLink(link.icon);
    const icon = iconForLink(link.icon);
    return html`<gr-tooltip-content
      has-tooltip
      ?position-below=${true}
      title=${tooltipText}
    >
      <a href=${link.url} target="_blank" rel="noopener noreferrer">
        <gr-icon
          icon=${icon.name}
          aria-label=${tooltipText}
          class="link"
          ?filled=${!!icon.filled}
        ></gr-icon>
      </a>
    </gr-tooltip-content>`;
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

  private handleAction(e: CustomEvent<Action>) {
    this.getChecksModel().triggerAction(
      e.detail,
      undefined,
      'results-dropdown'
    );
  }

  private handleFilter(e: ChecksResultsFilterEvent) {
    const newValue = e.detail.filterRegExp ?? '';
    this.getViewModel().updateState({
      checksResultsFilter: this.filterRegExp === newValue ? '' : newValue,
    });
  }

  private renderAction(action?: Action) {
    if (!action) return;
    return html`<gr-checks-action
      context="results"
      .action=${action}
    ></gr-checks-action>`;
  }

  private onAttemptSelected(e: CustomEvent<{value: string | undefined}>) {
    const attempt = stringToAttemptChoice(e.detail.value);
    assertIsDefined(attempt, `unexpected attempt choice ${e.detail.value}`);
    this.getChecksModel().updateStateSetAttempt(attempt);
  }

  private onPatchsetSelected(e: CustomEvent<{value: string}>) {
    const patchset = Number(e.detail.value) as PatchSetNumber;
    assert(Number.isInteger(patchset), `patchset must be integer: ${patchset}`);
    this.getChecksModel().updateStateSetPatchset(patchset);
  }

  private goToLatestPatchset() {
    assertIsDefined(this.latestPatchsetNumber, 'latestPatchsetNumber');
    this.getChecksModel().updateStateSetPatchset(this.latestPatchsetNumber);
  }

  private createAttemptDropdownItems() {
    if (this.runs.every(run => run.isSingleAttempt)) return [];
    const attempts: AttemptChoice[] = this.runs
      .map(run => run.attempt ?? 0)
      .filter(isAttemptChoice)
      .filter(unique);
    attempts.push(LATEST_ATTEMPT);
    attempts.push(ALL_ATTEMPTS);
    const items: DropdownItem[] = attempts.sort(sortAttemptChoices).map(a => {
      return {
        value: a,
        text: attemptChoiceLabel(a),
      };
    });
    return items;
  }

  private createPatchsetDropdownItems() {
    if (!this.latestPatchsetNumber) return [];
    return Array.from(Array(this.latestPatchsetNumber), (_, i) => {
      assertIsDefined(this.latestPatchsetNumber, 'latestPatchsetNumber');
      const index = this.latestPatchsetNumber - i;
      const postfix = index === this.latestPatchsetNumber ? ' (latest)' : '';
      return {
        value: `${index}`,
        text: `Patchset ${index}${postfix}`,
      };
    });
  }

  isRunSelected(run: {checkName: string}) {
    return this.selectedRuns.size === 0 || this.selectedRuns.has(run.checkName);
  }

  renderFilter() {
    const runs = this.runs.filter(
      run =>
        this.isRunSelected(run) && isAttemptSelected(this.selectedAttempt, run)
    );
    if (
      this.selectedRuns.size === 0 &&
      allResults(runs).length <= 3 &&
      this.filterRegExp === ''
    ) {
      return;
    }
    return html`
      <div class="filterDiv">
        <input
          id="filterInput"
          type="text"
          placeholder="Filter results by tag or regular expression"
          @input=${this.onFilterInputChange}
        />
      </div>
    `;
  }

  onFilterInputChange() {
    assertIsDefined(this.filterInput, 'filter <input> element');
    this.reporting.reportInteraction(
      Interaction.CHECKS_RESULT_FILTER_CHANGED,
      {},
      {deduping: Deduping.EVENT_ONCE_PER_CHANGE}
    );
    this.getViewModel().updateState({
      checksResultsFilter: this.filterInput.value,
    });
  }

  renderSection(category: Category) {
    const catString = category.toString().toLowerCase();
    const isWarningOrError =
      category === Category.WARNING || category === Category.ERROR;
    const allRuns = this.runs.filter(run =>
      isAttemptSelected(this.selectedAttempt, run)
    );
    const all = allRuns.reduce(
      (results: RunResult[], run) => [
        ...results,
        ...this.computeRunResults(category, run),
      ],
      []
    );
    const isSelectionActive = this.selectedRuns.size > 0;
    const selected = all.filter(result => this.isRunSelected(result));
    const re = new RegExp(this.filterRegExp, 'i');
    const filtered = selected.filter(result => matches(result, re));
    const isFilterActiveWithResults =
      this.filterRegExp !== '' && filtered.length > 0;

    // The logic for deciding whether to expand a section by default is a bit
    // complicated, but we want to collapse empty and info/success sections by
    // default for a clean and focused user experience. However, as soon as the
    // user starts selecting or filtering we must take this into account and
    // prefer to expand the sections.
    let expanded = this.isSectionExpanded.get(category);
    const expandedByUser = this.isSectionExpandedByUser.get(category) ?? false;
    if (!expandedByUser || expanded === undefined) {
      // Note that we are using `selected` for `isEmpty` and not `filtered`,
      // because if the filter is what makes a section empty, then we want to
      // show an expanded section, which contains a message about this.
      const isEmpty = selected.length === 0;
      expanded =
        !isEmpty &&
        (isWarningOrError || isSelectionActive || isFilterActiveWithResults);
      this.isSectionExpanded.set(category, expanded);
    }
    const expandedClass = expanded ? 'expanded' : 'collapsed';

    const isShowAll = this.isShowAll.get(category) ?? false;
    const allExpanded = this.allResultsExpanded.get(category) ?? false;
    const hasExpandableResults = filtered.some(computeIsExpandable);
    const resultCount = filtered.length;
    const empty = resultCount === 0 ? 'empty' : '';
    const resultLimit = isShowAll ? 1000 : 20;
    const showAllButton = this.renderShowAllButton(
      category,
      isShowAll,
      resultLimit,
      resultCount
    );
    const icon = iconFor(category);
    return html`
      <div class="${expandedClass} ${catString}">
        <div
          class="categoryHeader ${catString} ${empty}"
          @click=${() => this.toggleExpanded(category)}
        >
          <h3 class="left heading-3">
            <gr-icon
              class="expandIcon"
              icon=${expanded ? 'expand_less' : 'expand_more'}
            ></gr-icon>
            <gr-tooltip-content
              has-tooltip
              ?position-below=${true}
              title=${ifDefined(CATEGORY_TOOLTIPS.get(category))}
            >
              <div class="statusIconWrapper">
                <gr-icon
                  icon=${icon.name}
                  ?filled=${!!icon.filled}
                  class="statusIcon ${catString}"
                ></gr-icon>
                <span class="title">${catString}</span>
                <span class="count">${this.renderCount(all, filtered)}</span>
              </div>
            </gr-tooltip-content>
          </h3>
          <div class="right">
            <gr-button
              link
              ?hidden=${!expanded || !hasExpandableResults}
              @click=${(e: MouseEvent) =>
                this.toggleResultsExpanded(e, category)}
            >
              ${allExpanded ? 'Collapse All' : 'Expand All'}</gr-button
            >
          </div>
        </div>
        ${when(expanded, () =>
          this.renderResults(
            all,
            selected,
            filtered,
            resultLimit,
            showAllButton
          )
        )}
      </div>
    `;
  }

  renderShowAllButton(
    category: Category,
    isShowAll: boolean,
    showAllThreshold: number,
    resultCount: number
  ) {
    if (resultCount <= showAllThreshold) return;
    const message = isShowAll ? 'Show Less' : `Show All (${resultCount})`;
    const handler = () => this.toggleShowAll(category);
    return html`
      <tr class="showAllRow">
        <td colspan="3">
          <gr-button class="showAll" link @click=${handler}
            >${message}</gr-button
          >
        </td>
      </tr>
    `;
  }

  toggleResultsExpanded(e: MouseEvent, category: Category) {
    e.preventDefault();
    // Clicking the header row would otherwise collapse the entire section.
    e.stopPropagation();
    const catString = category.toString().toLowerCase();
    const current = this.allResultsExpanded.get(category) ?? false;
    const desired = !current;
    this.allResultsExpanded.set(category, desired);
    // this.allResultsExpanded stays the same object, but changes its content
    this.requestUpdate();
    const rows = this.shadowRoot!.querySelectorAll<GrResultRow>(
      `.${catString} gr-result-row`
    );
    for (const row of rows) {
      row.toggleExpanded(desired);
    }
  }

  toggleShowAll(category: Category) {
    const current = this.isShowAll.get(category) ?? false;
    this.isShowAll.set(category, !current);
    this.reporting.reportInteraction(
      Interaction.CHECKS_RESULT_SECTION_SHOW_ALL,
      {
        category,
        showAll: !current,
      }
    );
    this.requestUpdate();
  }

  renderResults(
    all: RunResult[],
    selected: RunResult[],
    filtered: RunResult[],
    limit: number,
    showAll: TemplateResult | undefined
  ) {
    if (all.length === 0) {
      return html`<div class="noResultsMessage">No results</div>`;
    }
    if (selected.length === 0) {
      return html`<div class="noResultsMessage">
        No results for this filtered view
      </div>`;
    }
    if (filtered.length === 0) {
      return html`<div class="noResultsMessage">
        No results match the regular expression
      </div>`;
    }
    filtered = filtered.slice(0, limit);
    // Some hosts/plugins use really long check names. If we have space and the
    // check names are indeed very long, then set a more generous nameCol width.
    const longestNameLength = Math.max(...all.map(r => r.checkName.length));
    const nameColClasses = {nameCol: true, longNames: longestNameLength > 25};
    return html`
      <table class="resultsTable">
        <thead>
          <tr class="headerRow">
            <th class=${classMap(nameColClasses)}>Run</th>
            <th class="summaryCol">Summary</th>
            <th class="expanderCol"></th>
          </tr>
        </thead>
        <tbody @checks-results-filter=${this.handleFilter}>
          ${repeat(
            filtered,
            // @ts-ignore: temporarily unblock typescript 5.3 migration
            result => result.internalResultId,
            (result?: RunResult) => html`
              <gr-result-row
                class=${charsOnly(result!.checkName)}
                .result=${result}
              ></gr-result-row>
            `
          )}
          ${showAll}
        </tbody>
      </table>
    `;
  }

  renderCount(all: RunResult[], filtered: RunResult[]) {
    if (all.length === filtered.length) {
      return html`(${all.length})`;
    }
    return html`(${filtered.length} shown out of ${all.length})`;
  }

  toggleExpanded(category: Category) {
    const expanded = this.isSectionExpanded.get(category);
    assertIsDefined(expanded, 'expanded must have been set in initial render');
    this.isSectionExpanded.set(category, !expanded);
    this.isSectionExpandedByUser.set(category, true);
    this.reporting.reportInteraction(Interaction.CHECKS_RESULT_SECTION_TOGGLE, {
      expanded: !expanded,
      category,
    });
    this.requestUpdate();
  }

  computeRunResults(category: Category, run: CheckRun): RunResult[] {
    if (category === Category.SUCCESS && hasCompletedWithoutResults(run)) {
      return [this.computeSuccessfulRunResult(run)];
    }
    return (
      run.results
        ?.filter(result => result.category === category)
        .map(result => runResult(run, result)) ?? []
    );
  }

  computeSuccessfulRunResult(run: CheckRun): RunResult {
    const adaptedRun: RunResult = runResult(run, {
      internalResultId: run.internalRunId + '-0',
      category: Category.SUCCESS,
      summary: run.statusDescription ?? '',
    });
    if (!run.statusDescription) {
      const start = run.scheduledTimestamp ?? run.startedTimestamp;
      const end = run.finishedTimestamp;
      let duration = '';
      if (start && end) {
        duration = ` in ${durationString(start, end, true)}`;
      }
      adaptedRun.message = `Completed without results${duration}.`;
    }
    if (run.statusLink) {
      adaptedRun.links = [
        {
          url: run.statusLink,
          primary: true,
          icon: LinkIcon.EXTERNAL,
        },
      ];
    }
    return adaptedRun;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-result-row': GrResultRow;
    'gr-result-expanded': GrResultExpanded;
    'gr-checks-results': GrChecksResults;
  }
}
