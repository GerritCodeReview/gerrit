/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import '../../shared/gr-label-info/gr-label-info';
import '../gr-submit-requirement-hovercard/gr-submit-requirement-hovercard';
import '../gr-trigger-vote/gr-trigger-vote';
import '../gr-change-summary/gr-change-summary';
import '../../shared/gr-limited-text/gr-limited-text';
import '../../shared/gr-vote-chip/gr-vote-chip';
import {LitElement, css, html, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators';
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
  getAllUniqueApprovals,
  getRequirements,
  getTriggerVotes,
  hasNeutralStatus,
  hasVotes,
  iconForStatus,
  orderSubmitRequirements,
} from '../../../utils/label-util';
import {fontStyles} from '../../../styles/gr-font-styles';
import {capitalizeFirstLetter, charsOnly} from '../../../utils/string-util';
import {subscribe} from '../../lit/subscription-controller';
import {CheckRun} from '../../../models/checks/checks-model';
import {getResultsOf, hasResultsOf} from '../../../models/checks/checks-util';
import {Category, RunStatus} from '../../../api/checks';
import {fireShowPrimaryTab} from '../../../utils/event-util';
import {PrimaryTab} from '../../../constants/constants';
import {submitRequirementsStyles} from '../../../styles/gr-submit-requirements-styles';
import {resolve} from '../../../models/dependency';
import {checksModelToken} from '../../../models/checks/checks-model';
import {join} from 'lit/directives/join';
import {map} from 'lit/directives/map';

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
        iron-icon {
          width: var(--line-height-normal, 20px);
          height: var(--line-height-normal, 20px);
          vertical-align: top;
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
          font-weight: var(--font-weight-bold);
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
        .votes-cell {
          display: flex;
          flex-flow: wrap;
        }
        .votes-cell .separator {
          width: 100%;
          margin-top: var(--spacing-s);
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
    const submit_requirements = orderSubmitRequirements(
      getRequirements(this.change)
    );

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
          )}
      ${this.renderTriggerVotes()}`;
  }

  renderRequirement(requirement: SubmitRequirementResultInfo, index: number) {
    const row = html`
     <td>${this.renderStatus(requirement.status)}</td>
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

    const endpointName = this.calculateEndpointName(requirement.name);
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

  renderStatus(status: SubmitRequirementStatus) {
    const icon = iconForStatus(status);
    return html`<iron-icon
      class=${icon}
      icon="gr-icons:${icon}"
      role="img"
      aria-label=${status.toLowerCase()}
    ></iron-icon>`;
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

    return html`${join(
      map(
        associatedLabelsWithVotes,
        label =>
          html`${this.renderLabelVote(label, allLabels)}
          ${this.renderOverrideLabels(requirement, label)}`
      ),
      html`<span class="separator"></span>`
    )}
    ${this.renderChecks(requirement)}`;
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
    forLabel: string
  ) {
    if (requirement.status !== SubmitRequirementStatus.OVERRIDDEN) return;
    const requirementLabels = extractAssociatedLabels(
      requirement,
      'onlyOverride'
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

  renderChecks(requirement: SubmitRequirementResultInfo) {
    const requirementLabels = extractAssociatedLabels(requirement);
    const errorRuns = this.runs
      .filter(run => hasResultsOf(run, Category.ERROR))
      .filter(
        run => run.labelName && requirementLabels.includes(run.labelName)
      );
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
      .filter(
        run => run.labelName && requirementLabels.includes(run.labelName)
      );

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
        fireShowPrimaryTab(this, PrimaryTab.CHECKS, false, {
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
    return html`<h3 class="metadata-title heading-3">Trigger Votes</h3>
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

  // not private for tests
  calculateEndpointName(requirementName: string) {
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
