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
  extractAssociatedLabels,
  iconForStatus,
} from '../../../utils/label-util';
import {ParsedChangeInfo} from '../../../types/types';
import {Label} from '../gr-change-requirements/gr-change-requirements';
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
        .condition {
          background-color: var(--gray-background);
          padding: var(--spacing-m);
          flex-grow: 1;
        }
        .expression {
          color: var(--gray-foreground);
        }
        iron-icon.check {
          color: var(--success-foreground);
        }
        iron-icon.close {
          color: var(--warning-foreground);
        }
        .showConditions iron-icon {
          color: inherit;
        }
        div.showConditions {
          border-top: 1px solid var(--border-color);
          margin-top: var(--spacing-m);
          padding: var(--spacing-m) var(--spacing-xl) 0;
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
      ${this.renderLabelSection()} ${this.renderConditionSection()}
    </div>`;
  }

  private renderLabelSection() {
    const labels = this.computeLabels();
    const showLabelName = labels.length >= 2;
    return html` <div class="section">
      <div class="sectionIcon"></div>
      <div class="row">
        <div>${labels.map(l => this.renderLabel(l, showLabelName))}</div>
      </div>
    </div>`;
  }

  private renderLabel(label: Label, showLabelName: boolean) {
    return html`
      ${showLabelName ? html`<div>${label.labelName} votes</div>` : ''}
      <gr-label-info
        .change=${this.change}
        .account=${this.account}
        .mutable=${this.mutable}
        .label="${label.labelName}"
        .labelInfo="${label.labelInfo}"
      ></gr-label-info>
    `;
  }

  private renderConditionSection() {
    if (!this.expanded) {
      return html` <div class="showConditions">
        <gr-button
          link=""
          class="showConditions"
          @click="${(_: MouseEvent) => this.handleShowConditions()}"
        >
          View condition
          <iron-icon icon="gr-icons:expand-more"></iron-icon
        ></gr-button>
      </div>`;
    } else {
      return html`
        <div class="section">
          <div class="sectionIcon">
            <iron-icon icon="gr-icons:description"></iron-icon>
          </div>
          <div class="sectionContent">${this.requirement?.description}</div>
        </div>
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
  }

  private computeLabels() {
    if (!this.requirement) return [];
    const requirementLabels = extractAssociatedLabels(this.requirement);
    const labels = this.change?.labels ?? {};

    const allLabels: Label[] = [];

    for (const label of Object.keys(labels)) {
      if (requirementLabels.includes(label)) {
        allLabels.push({
          labelName: label,
          icon: '',
          style: '',
          labelInfo: labels[label],
        });
      }
    }
    return allLabels;
  }

  private renderCondition(
    name: string,
    expression?: SubmitRequirementExpressionInfo
  ) {
    if (!expression?.expression) return '';
    return html`
      <div class="section">
        <div class="sectionIcon"></div>
        <div class="sectionContent condition">
          ${name}:<br />
          <span class="expression"> ${expression.expression} </span>
        </div>
      </div>
    `;
  }

  private handleShowConditions() {
    this.expanded = true;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirement-hovercard': GrSubmitRequirementHovercard;
  }
}
