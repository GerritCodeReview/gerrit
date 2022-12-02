/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-label-info/gr-label-info';
import {customElement, property} from 'lit/decorators.js';
import {
  AccountInfo,
  ChangeStatus,
  isDetailedLabelInfo,
  SubmitRequirementExpressionInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {
  canVote,
  extractAssociatedLabels,
  getApprovalInfo,
  hasVotes,
  iconForRequirement,
} from '../../../utils/label-util';
import {ParsedChangeInfo} from '../../../types/types';
import {css, html, LitElement} from 'lit';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {fontStyles} from '../../../styles/gr-font-styles';
import {DraftsAction} from '../../../constants/constants';
import {ReviewInput} from '../../../types/common';
import {getAppContext} from '../../../services/app-context';
import {assertIsDefined} from '../../../utils/common-util';
import {fireReload} from '../../../utils/event-util';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';
import {
  atomizeExpression,
  SubmitRequirementExpressionAtomStatus,
} from '../../../utils/submit-requirement-util';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-submit-requirement-hovercard')
export class GrSubmitRequirementHovercard extends base {
  @property({type: Object})
  requirement?: SubmitRequirementResultInfo;

  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Boolean})
  mutable = false;

  @property({type: Boolean})
  expanded = false;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      fontStyles,
      submitRequirementsStyles,
      base.styles || [],
      css`
        #container {
          min-width: 356px;
          max-width: 356px;
          padding: var(--spacing-xl) 0 var(--spacing-m) 0;
        }
        section.label {
          display: table-row;
        }
        .label-title {
          min-width: 10em;
          padding-top: var(--spacing-s);
        }
        .label-value {
          padding-top: var(--spacing-s);
        }
        .label-title,
        .label-value {
          display: table-cell;
          vertical-align: top;
        }
        .row {
          display: flex;
        }
        .title {
          color: var(--deemphasized-text-color);
          margin-right: var(--spacing-m);
        }
        div.section {
          margin: 0 var(--spacing-xl) var(--spacing-m) var(--spacing-xl);
          display: flex;
          align-items: center;
        }
        div.sectionIcon {
          flex: 0 0 30px;
        }
        div.sectionIcon gr-icon {
          position: relative;
        }
        .section.condition > .sectionContent {
          background-color: var(--gray-background);
          padding: var(--spacing-m);
          flex-grow: 1;
        }
        .button ~ .condition {
          margin-top: var(--spacing-m);
        }
        .expression {
          color: var(--gray-foreground);
        }
        .expression .failing.atom {
          border-bottom: 2px solid var(--error-foreground);
        }
        .expression .passing.atom {
          border-bottom: 2px solid var(--success-foreground);
        }
        .button gr-icon {
          color: inherit;
        }
        div.button {
          border-top: 1px solid var(--border-color);
          margin-top: var(--spacing-m);
          padding: var(--spacing-m) var(--spacing-xl) 0;
        }
      `,
    ];
  }

  override render() {
    if (!this.requirement) return;
    return html` <div id="container" role="tooltip" tabindex="-1">
      <div class="section">
        <div class="sectionIcon">${this.renderStatus(this.requirement)}</div>
        <div class="sectionContent">
          <h3 class="name heading-3">
            <span>${this.requirement.name}</span>
          </h3>
        </div>
      </div>
      <div class="section">
        <div class="sectionIcon">
          <gr-icon class="small" icon="info"></gr-icon>
        </div>
        <div class="sectionContent">
          <div class="row">
            <div class="title">Status</div>
            <div>${this.requirement.status}</div>
          </div>
        </div>
      </div>
      ${this.renderLabelSection()}${this.renderDescription()}
      ${this.renderShowHideConditionButton()}${this.renderConditionSection()}
      ${this.renderVotingButtons()}
    </div>`;
  }

  private renderStatus(requirement: SubmitRequirementResultInfo) {
    const icon = iconForRequirement(requirement);
    return html`<gr-icon
      class=${icon.icon}
      icon=${icon.icon}
      ?filled=${icon.filled}
      role="img"
      aria-label=${requirement.status.toLowerCase()}
    ></gr-icon>`;
  }

  private renderDescription() {
    let description = this.requirement?.description;
    if (this.requirement?.status === SubmitRequirementStatus.ERROR) {
      const submitRecord = this.change?.submit_records?.filter(
        record => record.rule_name === this.requirement?.name
      );
      if (submitRecord?.length === 1 && submitRecord[0].error_message) {
        description = submitRecord[0].error_message;
      }
    }
    if (!description) return;
    return html`<div class="section description">
      <div class="sectionIcon">
        <gr-icon icon="description"></gr-icon>
      </div>
      <div class="sectionContent">
        <gr-formatted-text
          .markdown=${true}
          .content=${description}
        ></gr-formatted-text>
      </div>
    </div>`;
  }

  private renderLabelSection() {
    if (!this.requirement) return;
    const requirementLabels = extractAssociatedLabels(this.requirement);
    const allLabels = this.change?.labels ?? {};
    const labels: string[] = [];
    for (const label of Object.keys(allLabels)) {
      if (requirementLabels.includes(label)) {
        const labelInfo = allLabels[label];
        const canSomeoneVote = (this.change?.reviewers['REVIEWER'] ?? []).some(
          reviewer => canVote(labelInfo, reviewer)
        );
        if (hasVotes(labelInfo) || canSomeoneVote) {
          labels.push(label);
        }
      }
    }

    if (labels.length === 0) return;
    const showLabelName = labels.length >= 2;
    return html` <div class="section">
      <div class="sectionIcon"></div>
      <div class="row">
        <div>${labels.map(l => this.renderLabel(l, showLabelName))}</div>
      </div>
    </div>`;
  }

  private renderLabel(labelName: string, showLabelName: boolean) {
    const labels = this.change?.labels ?? {};
    return html`
      ${showLabelName ? html`<div>${labelName} votes</div>` : ''}
      <gr-label-info
        .change=${this.change}
        .account=${this.account}
        .mutable=${this.mutable}
        .label=${labelName}
        .labelInfo=${labels[labelName]}
        @reload=${() => this.dispatchEventThroughTarget('reload')}
      ></gr-label-info>
    `;
  }

  private renderShowHideConditionButton() {
    const buttonText = this.expanded ? 'Hide conditions' : 'View conditions';
    const icon = this.expanded ? 'expand_less' : 'expand_more';

    return html` <div class="button">
      <gr-button
        link=""
        id="toggleConditionsButton"
        @click=${(_: MouseEvent) => this.toggleConditionsVisibility()}
      >
        ${buttonText}
        <gr-icon .icon=${icon}></gr-icon>
      </gr-button>
    </div>`;
  }

  private renderVotingButtons() {
    if (!this.requirement) return;
    if (!this.account) return;
    if (this.change?.status === ChangeStatus.MERGED) return;

    const submittabilityLabels = extractAssociatedLabels(
      this.requirement,
      'onlySubmittability'
    );
    const submittabilityVotes = submittabilityLabels.map(labelName =>
      this.renderLabelVote(labelName, 'submittability')
    );

    const overrideLabels = extractAssociatedLabels(
      this.requirement,
      'onlyOverride'
    );
    const overrideVotes = overrideLabels.map(labelName =>
      this.renderLabelVote(labelName, 'override')
    );

    return submittabilityVotes.concat(overrideVotes);
  }

  private renderLabelVote(
    labelName: string,
    type: 'override' | 'submittability'
  ) {
    const labels = this.change?.labels ?? {};
    const labelInfo = labels[labelName];
    if (!labelInfo || !isDetailedLabelInfo(labelInfo)) return;
    if (!this.account || !canVote(labelInfo, this.account)) return;

    const approvalInfo = getApprovalInfo(labelInfo, this.account);
    const maxVote = approvalInfo?.permitted_voting_range?.max;
    if (!maxVote || maxVote <= 0) return;
    if (approvalInfo?.value === maxVote) return; // Already voted maxVote
    return html` <div class="button quickApprove">
      <gr-button
        link=""
        @click=${(_: MouseEvent) => this.quickApprove(labelName, maxVote)}
      >
        ${this.computeVoteButtonName(labelName, maxVote, type)}
      </gr-button>
    </div>`;
  }

  private computeVoteButtonName(
    labelName: string,
    maxVote: number,
    type: 'override' | 'submittability'
  ) {
    if (type === 'override') {
      return `Override (${labelName})`;
    } else {
      return `Vote ${labelName} +${maxVote}`;
    }
  }

  private quickApprove(label: string, score: number) {
    assertIsDefined(this.change, 'change');
    const review: ReviewInput = {
      drafts: DraftsAction.PUBLISH_ALL_REVISIONS,
      labels: {
        [label]: score,
      },
    };
    return this.restApiService
      .saveChangeReview(
        this.change._number,
        this.change.current_revision,
        review
      )
      .then(() => {
        fireReload(this, true);
      });
  }

  private renderConditionSection() {
    if (!this.expanded) return;
    return html`
      ${this.renderCondition(
        'Submit condition',
        this.requirement?.submittability_expression_result
      )}
      ${this.renderCondition(
        'Application condition',
        this.requirement?.applicability_expression_result
      )}
      ${this.renderCondition(
        'Override condition',
        this.requirement?.override_expression_result
      )}
    `;
  }

  private getClassFromAtomStatus(
    status: SubmitRequirementExpressionAtomStatus
  ) {
    switch (status) {
      case SubmitRequirementExpressionAtomStatus.PASSING:
        return 'passing atom';
      case SubmitRequirementExpressionAtomStatus.FAILING:
        return 'failing atom';
      default:
        return 'atom';
    }
  }

  private getTitleFromAtomStatus(
    status: SubmitRequirementExpressionAtomStatus
  ) {
    switch (status) {
      case SubmitRequirementExpressionAtomStatus.PASSING:
        return 'Atom evaluates to True';
      case SubmitRequirementExpressionAtomStatus.FAILING:
        return 'Atom evaluates to False';
      default:
        return 'Atom value is unknown';
    }
  }

  private renderCondition(
    name: string,
    expression?: SubmitRequirementExpressionInfo
  ) {
    if (!expression?.expression) return '';
    return html`
      <div class="section condition">
        <div class="sectionContent">
          ${name}:<br />
          <span class="expression">
            ${atomizeExpression(expression).map(part =>
              part.isAtom
                ? html`<span
                    class=${this.getClassFromAtomStatus(part.atomStatus!)}
                    title=${this.getTitleFromAtomStatus(part.atomStatus!)}
                    >${part.value}</span
                  >`
                : part.value
            )}
          </span>
        </div>
      </div>
    `;
  }

  private toggleConditionsVisibility() {
    this.expanded = !this.expanded;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirement-hovercard': GrSubmitRequirementHovercard;
  }
}
