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
import '../../../styles/gr-font-styles';
import '../../shared/gr-hovercard/gr-hovercard-shared-style';
import '../../shared/gr-button/gr-button';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {customElement, property} from '@polymer/decorators';
import {HovercardBehaviorMixin} from '../../shared/gr-hovercard/gr-hovercard-behavior';
import {htmlTemplate} from './gr-submit-requirement-hovercard_html';
import {
  AccountInfo,
  SubmitRequirementExpressionInfo,
  SubmitRequirementResultInfo,
  SubmitRequirementStatus,
} from '../../../api/rest-api';
import {
  extractAssociatedLabels,
  iconForStatus,
} from '../../../utils/label-util';
import {ParsedChangeInfo} from '../../../types/types';
import {Label} from '../gr-change-requirements/gr-change-requirements';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardBehaviorMixin(PolymerElement);

@customElement('gr-submit-requirement-hovercard')
export class GrHovercardRun extends base {
  static get template() {
    return htmlTemplate;
  }

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

  @property({type: Array, computed: 'computeLabels(change, requirement)'})
  _labels: Label[] = [];

  computeLabels(
    change?: ParsedChangeInfo,
    requirement?: SubmitRequirementResultInfo
  ) {
    if (!requirement) return [];
    const requirementLabels = extractAssociatedLabels(requirement);
    const labels = change?.labels ?? {};

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

  computeIcon(status: SubmitRequirementStatus) {
    return iconForStatus(status);
  }

  renderCondition(expression?: SubmitRequirementExpressionInfo) {
    if (!expression) return '';

    return expression.expression;
  }

  _handleShowConditions() {
    this.expanded = true;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirement-hovercard': GrHovercardRun;
  }
}
