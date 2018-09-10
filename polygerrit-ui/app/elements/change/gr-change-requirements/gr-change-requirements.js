/**
@license
Copyright (C) 2018 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/rest-client-behavior/rest-client-behavior.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-label/gr-label.js';
import '../../shared/gr-label-info/gr-label-info.js';
import '../../shared/gr-limited-text/gr-limited-text.js';
const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `<dom-module id="gr-change-requirements">
  <template strip-whitespace="">
    <style include="shared-styles">
      :host {
        display: table;
        width: 100%;
      }
      .status {
        color: #FFA62F;
        display: inline-block;
        font-family: var(--monospace-font-family);
        text-align: center;
      }
      .approved.status {
        color: var(--vote-text-color-recommended);
      }
      .rejected.status {
        color: var(--vote-text-color-disliked);
      }
      iron-icon {
        color: inherit;
      }
      .name {
        font-family: var(--font-family-bold);
      }
      section {
        display: table-row;
      }
      .show-hide {
        float: right;
      }
      .title {
        min-width: 10em;
        padding: .75em .5em 0 var(--requirements-horizontal-padding);
        vertical-align: top;
      }
      .value {
        padding: .6em .5em 0 0;
        vertical-align: middle;
      }
      .title,
      .value {
        display: table-cell;
      }
      .hidden {
        display: none;
      }
      .showHide {
        cursor: pointer;
      }
      .showHide .title {
        border-top: 1px solid var(--border-color);
        padding-bottom: .5em;
        padding-top: .5em;
      }
      .showHide .value {
        border-top: 1px solid var(--border-color);
        padding-top: 0;
        vertical-align: middle;
      }
      .showHide iron-icon {
        color: var(--deemphasized-text-color);
        float: right;
      }
      .spacer {
        height: .5em;
      }
    </style>
    <template is="dom-repeat" items="[[_requirements]]">
      <section>
        <div class="title requirement">
          <span class\$="status [[item.style]]">
            <iron-icon class="icon" icon="[[_computeRequirementIcon(item.satisfied)]]"></iron-icon>
          </span>
          <gr-limited-text class="name" limit="40" text="[[item.fallback_text]]"></gr-limited-text>
        </div>
      </section>
    </template>
    <template is="dom-repeat" items="[[_requiredLabels]]">
      <section>
        <div class="title">
          <span class\$="status [[item.style]]">
            <iron-icon class="icon" icon="[[item.icon]]"></iron-icon>
          </span>
          <gr-limited-text class="name" limit="40" text="[[item.label]]"></gr-limited-text>
        </div>
        <div class="value">
          <gr-label-info change="{{change}}" account="[[account]]" mutable="[[mutable]]" label="[[item.label]]" label-info="[[item.labelInfo]]"></gr-label-info>
        </div>
      </section>
    </template>
    <section class="spacer"></section>
    <section class\$="spacer [[_computeShowOptional(_optionalLabels.*)]]"></section>
    <section show-bottom-border\$="[[_showOptionalLabels]]" on-tap="_handleShowHide" class\$="showHide [[_computeShowOptional(_optionalLabels.*)]]">
      <div class="title">Other labels</div>
      <div class="value">
        <iron-icon id="showHide" icon="[[_computeShowHideIcon(_showOptionalLabels)]]">
        </iron-icon>
      
      </div>
    </section>
    <template is="dom-repeat" items="[[_optionalLabels]]">
      <section class\$="optional [[_computeSectionClass(_showOptionalLabels)]]">
        <div class="title">
          <span class\$="status [[item.style]]">
            <template is="dom-if" if="[[item.icon]]">
              <iron-icon class="icon" icon="[[item.icon]]"></iron-icon>
            </template>
            <template is="dom-if" if="[[!item.icon]]">
              <span>[[_computeLabelValue(item.labelInfo.value)]]</span>
            </template>
          </span>
          <gr-limited-text class="name" limit="40" text="[[item.label]]"></gr-limited-text>
        </div>
        <div class="value">
          <gr-label-info change="{{change}}" account="[[account]]" mutable="[[mutable]]" label="[[item.label]]" label-info="[[item.labelInfo]]"></gr-label-info>
        </div>
      </section>
    </template>
    <section class\$="spacer [[_computeShowOptional(_optionalLabels.*)]] [[_computeSectionClass(_showOptionalLabels)]]"></section>
  </template>
  
</dom-module>`;

document.head.appendChild($_documentContainer.content);

Polymer({
  is: 'gr-change-requirements',

  properties: {
    /** @type {?} */
    change: Object,
    account: Object,
    mutable: Boolean,
    _requirements: {
      type: Array,
      computed: '_computeRequirements(change)',
    },
    _requiredLabels: {
      type: Array,
      value: () => [],
    },
    _optionalLabels: {
      type: Array,
      value: () => [],
    },
    _showWip: {
      type: Boolean,
      computed: '_computeShowWip(change)',
    },
    _showOptionalLabels: {
      type: Boolean,
      value: true,
    },
  },

  behaviors: [
    Gerrit.RESTClientBehavior,
  ],

  observers: [
    '_computeLabels(change.labels.*)',
  ],

  _computeShowWip(change) {
    return change.work_in_progress;
  },

  _computeRequirements(change) {
    const _requirements = [];

    if (change.requirements) {
      for (const requirement of change.requirements) {
        requirement.satisfied = requirement.status === 'OK';
        requirement.style =
            this._computeRequirementClass(requirement.satisfied);
        _requirements.push(requirement);
      }
    }
    if (change.work_in_progress) {
      _requirements.push({
        fallback_text: 'Work-in-progress',
        tooltip: 'Change must not be in \'Work in Progress\' state.',
      });
    }

    return _requirements;
  },

  _computeRequirementClass(requirementStatus) {
    return requirementStatus ? 'approved' : '';
  },

  _computeRequirementIcon(requirementStatus) {
    return requirementStatus ? 'gr-icons:check' : 'gr-icons:hourglass';
  },

  _computeLabels(labelsRecord) {
    const labels = labelsRecord.base;
    this._optionalLabels = [];
    this._requiredLabels = [];

    for (const label in labels) {
      if (!labels.hasOwnProperty(label)) { continue; }

      const labelInfo = labels[label];
      const icon = this._computeLabelIcon(labelInfo);
      const style = this._computeLabelClass(labelInfo);
      const path = labelInfo.optional ? '_optionalLabels' : '_requiredLabels';

      this.push(path, {label, icon, style, labelInfo});
    }
  },

  /**
   * @param {Object} labelInfo
   * @return {string} The icon name, or undefined if no icon should
   *     be used.
   */
  _computeLabelIcon(labelInfo) {
    if (labelInfo.approved) { return 'gr-icons:check'; }
    if (labelInfo.rejected) { return 'gr-icons:close'; }
    return 'gr-icons:hourglass';
  },

  /**
   * @param {Object} labelInfo
   */
  _computeLabelClass(labelInfo) {
    if (labelInfo.approved) { return 'approved'; }
    if (labelInfo.rejected) { return 'rejected'; }
    return '';
  },

  _computeShowOptional(optionalFieldsRecord) {
    return optionalFieldsRecord.base.length ? '' : 'hidden';
  },

  _computeLabelValue(value) {
    return (value > 0 ? '+' : '') + value;
  },

  _computeShowHideIcon(showOptionalLabels) {
    return showOptionalLabels ?
        'gr-icons:expand-less' :
        'gr-icons:expand-more';
  },

  _computeSectionClass(show) {
    return show ? '' : 'hidden';
  },

  _handleShowHide(e) {
    this._showOptionalLabels = !this._showOptionalLabels;
  },
});
