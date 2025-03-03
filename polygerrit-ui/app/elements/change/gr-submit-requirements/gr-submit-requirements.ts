/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-label-info/gr-label-info';
import '../../shared/gr-icon/gr-icon';
import '../gr-submit-requirement-hovercard/gr-submit-requirement-hovercard';
import '../gr-trigger-vote/gr-trigger-vote';
import '../gr-change-summary/gr-change-summary';
import '../../shared/gr-limited-text/gr-limited-text';
import '../../shared/gr-vote-chip/gr-vote-chip';
import {LitElement, css, html, TemplateResult, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {ParsedChangeInfo} from '../../../types/types';
import {
  AccountInfo,
  isDetailedLabelInfo,
  isQuickLabelInfo,
  LabelNameToInfoMap,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {
  extractAssociatedLabels,
  extractLabelsWithCountFrom,
  getAllUniqueApprovals,
  getRequirements,
  getTriggerVotes,
  hasApprovedVote,
  hasNeutralStatus,
  hasRejectedVote,
  hasVotes,
  iconForRequirement,
  orderSubmitRequirements,
} from '../../../utils/label-util';
import {fontStyles} from '../../../styles/gr-font-styles';
import {capitalizeFirstLetter, charsOnly} from '../../../utils/string-util';
import {subscribe} from '../../lit/subscription-controller';
import {CheckRun} from '../../../models/checks/checks-model';
import {getResultsOf, hasResultsOf} from '../../../models/checks/checks-util';
import {Category, RunStatus} from '../../../api/checks';
import {fireShowTab} from '../../../utils/event-util';
import {Tab} from '../../../constants/constants';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';
import {resolve} from '../../../models/dependency';
import {checksModelToken} from '../../../models/checks/checks-model';
import {map} from 'lit/directives/map.js';

/**
 * @attr {Boolean} suppress-title - hide titles, currently for hovercard view
 */
@customElement('gr-submit-requirements')
export class GrSubmitRequirements extends LitElement {
  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Boolean})
  mutable?: boolean;

  @property({type: Boolean, attribute: 'disable-hovercards'})
  disableHovercards = false;

  @property({type: Boolean, attribute: 'disable-endpoints'})
  disableEndpoints = false;

  @state()
  runs: CheckRun[] = [];

  static override get styles() {
    return [
      fontStyles,
      submitRequirementsStyles,
      css`
        :host([suppress-title]) .metadata-title {
          display: none;
        }
        .metadata-title {
          color: var(--deemphasized-text-color);
          padding-left: var(--metadata-horizontal-padding);
          margin: 0 0 var(--spacing-s);
          padding-top: var(--spacing-s);
        }
        gr-icon {
          font-size: var(--line-height-normal, 20px);
        }
        .requirements,
        section.trigger-votes {
          margin-left: var(--spacing-l);
        }
        .trigger-votes {
          padding-top: var(--spacing-s);
          display: flex;
          flex-wrap: wrap;
          gap: var(--spacing-s);
          /* Setting max-width as defined in Submit Requirements design,
           *  to wrap overflowed items to next row.
           */
          max-width: 390px;
        }
        gr-limited-text.name {
          font-weight: var(--font-weight-medium);
        }
        table {
          border-collapse: collapse;
          border-spacing: 0;
        }
        td {
          padding: var(--spacing-s);
          white-space: nowrap;
          vertical-align: top;
        }
        .votes {
          display: flex;
          flex-flow: column;
          row-gap: var(--spacing-s);
        }
        .votes-line {
          display: flex;
          flex-flow: wrap;
        }
        gr-vote-chip {
          margin-right: var(--spacing-s);
        }
        gr-checks-chip {
          /* .checksChip has top: 2px, this is canceling it */
          margin-top: -2px;
        }
      `,
    ];
  }

  private readonly getChecksModel = resolve(this, checksModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().allRunsLatestPatchsetLatestAttempt$,
      x => (this.runs = x)
    );
  }

  override render() {
    return html`${this.renderSubmitRequirements()}${this.renderTriggerVotes()}`;
  }

  private renderSubmitRequirements() {
    const submit_requirements = orderSubmitRequirements(
      getRequirements(this.change)
    );
    if (submit_requirements.length === 0) return nothing;
    return html` <h3
        class="metadata-title heading-3"
        id="submit-requirements-caption"
      >
        Submit Requirements
      </h3>
      <table class="requirements" aria-labelledby="submit-requirements-caption">
        <thead hidden>
          <tr>
            <th>Status</th>
            <th>Name</th>
            <th>Votes</th>
          </tr>
        </thead>
        <tbody>
          ${submit_requirements.map((requirement, index) =>
            this.renderRequirement(requirement, index)
          )}
        </tbody>
      </table>
      ${this.disableHovercards
        ? ''
        : submit_requirements.map(
            (requirement, index) => html`
              <gr-submit-requirement-hovercard
                for="requirement-${index}-${charsOnly(requirement.name)}"
                .requirement=${requirement}
                .change=${this.change}
                .account=${this.account}
                .mutable=${this.mutable ?? false}
              ></gr-submit-requirement-hovercard>
            `
          )}`;
  }

  private renderRequirement(
    requirement: SubmitRequirementResultInfo,
    index: number
  ) {
    const row = html`
     <td>${this.renderStatus(requirement)}</td>
        <td class="name">
          <gr-limited-text
            class="name"
            .text=${requirement.name}
          ></gr-limited-text>
        </td>
        <td>
          ${this.renderEndpoint(requirement, this.renderVoteCell(requirement))}
        </td>
      </tr>
    `;

    if (this.disableHovercards) {
      // when hovercards are disabled, we don't make line focusable (tabindex)
      // since otherwise there is no action associated with the line
      return html`<tr>
        ${row}
      </tr>`;
    } else {
      return html`<tr
        id="requirement-${index}-${charsOnly(requirement.name)}"
        role="button"
        tabindex="0"
      >
        ${row}
      </tr>`;
    }
  }

  renderEndpoint(
    requirement: SubmitRequirementResultInfo,
    slot: TemplateResult
  ) {
    if (this.disableEndpoints)
      return html`<div class="votes-cell">${slot}</div>`;

    const endpointName = this.computeEndpointName(requirement.name);
    return html`<gr-endpoint-decorator class="votes-cell" name=${endpointName}>
      <gr-endpoint-param
        name="change"
        .value=${this.change}
      ></gr-endpoint-param>
      <gr-endpoint-param
        name="requirement"
        .value=${requirement}
      ></gr-endpoint-param>
      ${slot}
    </gr-endpoint-decorator>`;
  }

  private renderStatus(requirement: SubmitRequirementResultInfo) {
    const icon = iconForRequirement(requirement);
    return html`<gr-icon
      class=${icon.icon}
      ?filled=${icon.filled}
      .icon=${icon.icon}
      role="img"
      aria-label=${requirement.status.toLowerCase()}
    ></gr-icon>`;
  }

  renderVoteCell(requirement: SubmitRequirementResultInfo) {
    if (requirement.status === SubmitRequirementStatus.ERROR) {
      return html`<span class="error">Error</span>`;
    }

    const requirementLabels = extractAssociatedLabels(requirement);
    const allLabels = this.change?.labels ?? {};
    const associatedLabels = Object.keys(allLabels).filter(label =>
      requirementLabels.includes(label)
    );

    const requirementWithoutLabelToVoteOn = associatedLabels.length === 0;
    if (requirementWithoutLabelToVoteOn) {
      const status = capitalizeFirstLetter(requirement.status.toLowerCase());
      return this.renderChecks(requirement) || html`${status}`;
    }

    const everyAssociatedLabelsIsWithoutVotes = associatedLabels.every(
      label => !hasVotes(allLabels[label])
    );
    if (everyAssociatedLabelsIsWithoutVotes) {
      return this.renderChecks(requirement) || html`No votes`;
    }

    const associatedLabelsWithVotes = associatedLabels.filter(label =>
      hasVotes(allLabels[label])
    );

    return html`<div class="votes">
      ${map(
        associatedLabelsWithVotes,
        label =>
          html`<div class="votes-line">
            ${this.renderLabelVote(label, allLabels)}
            ${this.renderVoteCountHelpLabel(requirement, label, allLabels)}
            ${this.renderOverrideLabels(
              requirement,
              label,
              associatedLabelsWithVotes.length > 1
            )}
            ${this.renderChecks(requirement, label)}
          </div>`
      )}
    </div> `;
  }

  // Help when submit requirement needs more votes and there is already 1 vote
  renderVoteCountHelpLabel(
    requirement: SubmitRequirementResultInfo,
    label: string,
    labels: LabelNameToInfoMap
  ) {
    if (requirement.status !== SubmitRequirementStatus.UNSATISFIED) {
      return nothing;
    }

    const labelInfo = labels[label];
    if (!hasApprovedVote(labelInfo) || hasRejectedVote(labelInfo)) {
      return nothing;
    }

    const count = extractLabelsWithCountFrom(
      requirement.submittability_expression_result.expression
    ).find(labelWithCount => labelWithCount.label === label)?.count;

    if (!count || count === 1) return nothing;

    return html`Requires ${count} votes`;
  }

  renderLabelVote(label: string, labels: LabelNameToInfoMap) {
    const labelInfo = labels[label];
    if (isDetailedLabelInfo(labelInfo)) {
      const uniqueApprovals = getAllUniqueApprovals(labelInfo).filter(
        approval => !hasNeutralStatus(labelInfo, approval)
      );
      return uniqueApprovals.map(
        approvalInfo =>
          html`<gr-vote-chip
            .vote=${approvalInfo}
            .label=${labelInfo}
            .more=${(labelInfo.all ?? []).filter(
              other => other.value === approvalInfo.value
            ).length > 1}
          ></gr-vote-chip>`
      );
    } else if (isQuickLabelInfo(labelInfo)) {
      return [html`<gr-vote-chip .label=${labelInfo}></gr-vote-chip>`];
    } else {
      return html``;
    }
  }

  renderOverrideLabels(
    requirement: SubmitRequirementResultInfo,
    forLabel: string,
    showForAllLabel: boolean
  ) {
    if (
      !showForAllLabel &&
      requirement.status !== SubmitRequirementStatus.OVERRIDDEN
    )
      return;
    const requirementLabels = extractAssociatedLabels(
      requirement,
      showForAllLabel ? 'all' : 'onlyOverride'
    )
      .filter(label => label === forLabel)
      .filter(label => {
        const allLabels = this.change?.labels ?? {};
        return allLabels[label] && hasVotes(allLabels[label]);
      });
    return requirementLabels.map(
      label => html`<span class="overrideLabel">${label}</span>`
    );
  }

  renderChecks(requirement: SubmitRequirementResultInfo, labelName?: string) {
    const requirementLabels = extractAssociatedLabels(requirement);
    const errorRuns = this.runs
      .filter(run => hasResultsOf(run, Category.ERROR))
      .filter(run => {
        if (labelName) {
          return labelName === run.labelName;
        } else {
          return run.labelName && requirementLabels.includes(run.labelName);
        }
      });
    const errorRunsCount = errorRuns.reduce(
      (sum, run) => sum + getResultsOf(run, Category.ERROR).length,
      0
    );
    if (errorRunsCount > 0) {
      return this.renderChecksCategoryChip(
        errorRuns,
        errorRunsCount,
        Category.ERROR
      );
    }
    const runningRuns = this.runs
      .filter(r => r.isLatestAttempt)
      .filter(
        r => r.status === RunStatus.RUNNING || r.status === RunStatus.SCHEDULED
      )
      .filter(run => {
        if (labelName) {
          return labelName === run.labelName;
        } else {
          return run.labelName && requirementLabels.includes(run.labelName);
        }
      });

    const runningRunsCount = runningRuns.length;
    if (runningRunsCount > 0) {
      return this.renderChecksCategoryChip(
        runningRuns,
        runningRunsCount,
        RunStatus.RUNNING
      );
    }
    return;
  }

  renderChecksCategoryChip(
    runs: CheckRun[],
    runsCount: Number,
    category: Category | RunStatus
  ) {
    if (runsCount === 0) return;
    const links = [];
    if (runs.length === 1 && runs[0].statusLink) {
      links.push(runs[0].statusLink);
    }
    return html`<gr-checks-chip
      .text=${`${runsCount}`}
      .links=${links}
      .statusOrCategory=${category}
      @click=${() => {
        fireShowTab(this, Tab.CHECKS, false, {
          checksTab: {
            statusOrCategory: category,
          },
        });
      }}
    ></gr-checks-chip>`;
  }

  renderTriggerVotes() {
    const labels = this.change?.labels ?? {};
    const triggerVotes = getTriggerVotes(this.change).filter(label =>
      hasVotes(labels[label])
    );
    if (!triggerVotes.length) return;
    return html`<h3 class="metadata-title heading-3">
        ${this.computeTriggerVotesTitle()}
      </h3>
      <section class="trigger-votes">
        ${triggerVotes.map(
          label =>
            html`<gr-trigger-vote
              .label=${label}
              .labelInfo=${labels[label]}
              .change=${this.change}
              .account=${this.account}
              .mutable=${this.mutable ?? false}
              .disableHovercards=${this.disableHovercards}
            ></gr-trigger-vote>`
        )}
      </section>`;
  }

  private computeTriggerVotesTitle() {
    if (getRequirements(this.change).length === 0) {
      // This is special case for old changes without submit requirements.
      return 'Label Votes';
    } else {
      return 'Trigger Votes';
    }
  }

  // not private for tests
  computeEndpointName(requirementName: string) {
    // remove class name annnotation after ~
    const name = requirementName.split('~')[0];
    const normalizedName = charsOnly(name).toLowerCase();
    return `submit-requirement-${normalizedName}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirements': GrSubmitRequirements;
  }
}
