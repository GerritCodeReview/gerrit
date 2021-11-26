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
import '../../shared/gr-button/gr-button';
import '../../shared/gr-label-info/gr-label-info';
import {customElement, property} from 'lit/decorators';
import {
  AccountInfo,
  SubmitRequirementExpressionInfo,
  SubmitRequirementResultInfo,
} from '../../../api/rest-api';
import {
  canVote,
  extractAssociatedLabels,
  hasVotes,
  iconForStatus,
} from '../../../utils/label-util';
import {ParsedChangeInfo} from '../../../types/types';
import {css, html, LitElement} from 'lit';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {fontStyles} from '../../../styles/gr-font-styles';

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

  static override get styles() {
    return [
      fontStyles,
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
        div.sectionIcon iron-icon {
          position: relative;
          width: 20px;
          height: 20px;
        }
        .section.condition > .sectionContent {
          background-color: var(--gray-background);
          padding: var(--spacing-m);
          flex-grow: 1;
        }
        .showConditions ~ .condition {
          margin-top: var(--spacing-m);
        }
        .expression {
          color: var(--gray-foreground);
        }
        iron-icon.check,
        iron-icon.overridden {
          color: var(--success-foreground);
        }
        iron-icon.close {
          color: var(--error-foreground);
        }
        .showConditions iron-icon {
          color: inherit;
        }
        div.showConditions {
          border-top: 1px solid var(--border-color);
          margin-top: var(--spacing-m);
          padding: var(--spacing-m) var(--spacing-xl) 0;
        }
        .section.description > .sectionContent {
          white-space: pre-wrap;
        }
      `,
    ];
  }

  override render() {
    if (!this.requirement) return;
    const icon = iconForStatus(this.requirement.status);
    return html` <div id="container" role="tooltip" tabindex="-1">
      <div class="section">
        <div class="sectionIcon">
          <iron-icon class="${icon}" icon="gr-icons:${icon}"></iron-icon>
        </div>
        <div class="sectionContent">
          <h3 class="name heading-3">
            <span>${this.requirement.name}</span>
          </h3>
        </div>
      </div>
      <div class="section">
        <div class="sectionIcon">
          <iron-icon class="small" icon="gr-icons:info-outline"></iron-icon>
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
    </div>`;
  }

  private renderDescription() {
    if (!this.requirement?.description) return;
    return html`<div class="section description">
      <div class="sectionIcon">
        <iron-icon icon="gr-icons:description"></iron-icon>
      </div>
      <div class="sectionContent">${this.requirement.description}</div>
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
      ></gr-label-info>
    `;
  }

  private renderShowHideConditionButton() {
    const buttonText = this.expanded ? 'Hide conditions' : 'View conditions';
    const icon = this.expanded ? 'expand-less' : 'expand-more';

    return html` <div class="showConditions">
      <gr-button
        link=""
        id="toggleConditionsButton"
        @click="${(_: MouseEvent) => this.toggleConditionsVisibility()}"
      >
        ${buttonText}
        <iron-icon icon="gr-icons:${icon}"></iron-icon
      ></gr-button>
    </div>`;
  }

  private renderConditionSection() {
    if (!this.expanded) return;
    return html`
      ${this.renderCondition(
        'Blocking condition',
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

  private renderCondition(
    name: string,
    expression?: SubmitRequirementExpressionInfo
  ) {
    if (!expression?.expression) return '';
    return html`
      <div class="section condition">
        <div class="sectionContent">
          ${name}:<br />
          <span class="expression"> ${expression.expression} </span>
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
