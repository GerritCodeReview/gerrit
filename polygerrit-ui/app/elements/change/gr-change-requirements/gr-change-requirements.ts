/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-label/gr-label';
import '../../shared/gr-label-info/gr-label-info';
import '../../shared/gr-limited-text/gr-limited-text';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-requirements_html';
import {customElement, property, observe} from '@polymer/decorators';
import {
  ChangeInfo,
  AccountInfo,
  QuickLabelInfo,
  Requirement,
  RequirementType,
  LabelNameToInfoMap,
  LabelInfo,
} from '../../../types/common';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {appContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';
import {labelCompare} from '../../../utils/label-util';

interface ChangeRequirement extends Requirement {
  satisfied: boolean;
  style: string;
}

interface ChangeWIP {
  type: RequirementType;
  fallback_text: string;
  tooltip: string;
}

interface Label {
  labelName: string;
  labelInfo: LabelInfo;
  icon: string;
  style: string;
}

@customElement('gr-change-requirements')
class GrChangeRequirements extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Object})
  account?: AccountInfo;

  @property({type: Boolean})
  mutable?: boolean;

  @property({type: Array, computed: '_computeRequirements(change)'})
  _requirements?: Array<ChangeRequirement | ChangeWIP>;

  @property({type: Array})
  _requiredLabels: Label[] = [];

  @property({type: Array})
  _optionalLabels: Label[] = [];

  @property({type: Boolean, computed: '_computeShowWip(change)'})
  _showWip?: boolean;

  @property({type: Boolean})
  _showOptionalLabels = true;

  @property({type: Boolean})
  _isNewChangeSummaryUiEnabled = appContext.flagsService.isEnabled(
    KnownExperimentId.NEW_CHANGE_SUMMARY_UI
  );

  private readonly reporting = appContext.reportingService;

  _computeShowWip(change: ChangeInfo) {
    return change.work_in_progress;
  }

  _computeRequirements(change: ChangeInfo) {
    const _requirements: Array<ChangeRequirement | ChangeWIP> = [];

    if (change.requirements) {
      for (const requirement of change.requirements) {
        const satisfied = requirement.status === 'OK';
        const style = this._computeRequirementClass(satisfied);
        _requirements.push({...requirement, satisfied, style});
      }
    }
    if (change.work_in_progress) {
      _requirements.push({
        type: 'wip' as RequirementType,
        fallback_text: 'Work-in-progress',
        tooltip: "Change must not be in 'Work in Progress' state.",
      });
    }

    return _requirements;
  }

  _computeRequirementClass(requirementStatus: boolean) {
    return requirementStatus ? 'approved' : '';
  }

  _computeRequirementIcon(requirementStatus: boolean) {
    return requirementStatus ? 'gr-icons:check' : 'gr-icons:schedule';
  }

  @observe('change.labels.*')
  _computeLabels(
    labelsRecord: PolymerDeepPropertyChange<
      LabelNameToInfoMap,
      LabelNameToInfoMap
    >
  ) {
    const labels = labelsRecord.base || {};
    const allLabels: Label[] = [];

    for (const label of Object.keys(labels).sort(labelCompare)) {
      allLabels.push({
        labelName: label,
        icon: this._computeLabelIcon(labels[label]),
        style: this._computeLabelClass(labels[label]),
        labelInfo: labels[label],
      });
    }
    this._optionalLabels = allLabels.filter(label => label.labelInfo.optional);
    this._requiredLabels = allLabels.filter(label => !label.labelInfo.optional);
  }

  /**
   * @return The icon name, or undefined if no icon should
   * be used.
   */
  _computeLabelIcon(labelInfo: QuickLabelInfo) {
    if (labelInfo.approved) {
      return 'gr-icons:check';
    }
    if (labelInfo.rejected) {
      return 'gr-icons:close';
    }
    return 'gr-icons:schedule';
  }

  _computeLabelClass(labelInfo: QuickLabelInfo) {
    if (labelInfo.approved) {
      return 'approved';
    }
    if (labelInfo.rejected) {
      return 'rejected';
    }
    return '';
  }

  _computeShowOptional(
    optionalFieldsRecord: PolymerDeepPropertyChange<Label[], Label[]>
  ) {
    return optionalFieldsRecord.base.length ? '' : 'hidden';
  }

  _computeLabelValue(value: number) {
    return `${value > 0 ? '+' : ''}${value}`;
  }

  _computeShowHideIcon(showOptionalLabels: boolean) {
    return showOptionalLabels ? 'gr-icons:expand-less' : 'gr-icons:expand-more';
  }

  _computeSectionClass(show: boolean) {
    return show ? '' : 'hidden';
  }

  _handleShowHide() {
    this._showOptionalLabels = !this._showOptionalLabels;
    this.reporting.reportInteraction('toggle show all button', {
      sectionName: 'optional labels',
      toState: this._showOptionalLabels ? 'Show all' : 'Show less',
    });
  }

  _computeSubmitRequirementEndpoint(item: ChangeRequirement | ChangeWIP) {
    return `submit-requirement-item-${item.type}`;
  }

  _computeShowAllLabelText(_showOptionalLabels: boolean) {
    if (_showOptionalLabels) {
      return 'Show less';
    } else {
      return 'Show all';
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-requirements': GrChangeRequirements;
  }
}
