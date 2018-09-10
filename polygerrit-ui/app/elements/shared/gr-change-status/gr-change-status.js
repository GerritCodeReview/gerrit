/**
@license
Copyright (C) 2017 The Android Open Source Project

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

import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-tooltip-content/gr-tooltip-content.js';
import '../../../styles/shared-styles.js';

const ChangeStates = {
  MERGED: 'Merged',
  ABANDONED: 'Abandoned',
  MERGE_CONFLICT: 'Merge Conflict',
  WIP: 'WIP',
  PRIVATE: 'Private',
};

const WIP_TOOLTIP = 'This change isn\'t ready to be reviewed or submitted. ' +
    'It will not appear in dashboards, and email notifications will be ' +
    'silenced until the review is started.';

const PRIVATE_TOOLTIP = 'This change is only visible to its owner and ' +
    'current reviewers (or anyone with "View Private Changes" permission).';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      .chip {
        border-radius: 4px;
        background-color: var(--chip-background-color);
        font-family: var(--font-family);
        font-size: var(--font-size-normal);
        padding: .1em .5em;
        white-space: nowrap;
      }
      :host(.merged) .chip {
        background-color: #5b9d52;
        color: #5b9d52;
      }
      :host(.abandoned) .chip {
        background-color: #afafaf;
        color: #afafaf;
      }
      :host(.wip) .chip {
        background-color: #8f756c;
        color: #8f756c;
      }
      :host(.private) .chip {
        background-color: #c17ccf;
        color: #c17ccf;
      }
      :host(.merge-conflict) .chip {
        background-color: #dc5c60;
        color: #dc5c60;
      }
      :host(.active) .chip {
        background-color: #29b6f6;
        color: #29b6f6;
      }
      :host(.ready-to-submit) .chip {
        background-color: #e10ca3;
        color: #e10ca3;
      }
      :host(.custom) .chip {
        background-color: #825cc2;
        color: #825cc2;
      }
      :host([flat]) .chip {
        background-color: transparent;
        padding: .1em;
      }
      :host:not([flat]) .chip {
        color: white;
      }
    </style>
    <gr-tooltip-content has-tooltip="" position-below="" title="[[tooltipText]]" max-width="40em">
      <div class="chip" aria-label\$="Label: [[status]]">
        [[_computeStatusString(status)]]
      </div>
    </gr-tooltip-content>
`,

  is: 'gr-change-status',

  properties: {
    flat: {
      type: Boolean,
      value: false,
      reflectToAttribute: true,
    },
    status: {
      type: String,
      observer: '_updateChipDetails',
    },
    tooltipText: {
      type: String,
      value: '',
    },
  },

  _computeStatusString(status) {
    if (status === ChangeStates.WIP && !this.flat) {
      return 'Work in Progress';
    }
    return status;
  },

  _toClassName(str) {
    return str.toLowerCase().replace(/\s/g, '-');
  },

  _updateChipDetails(status, previousStatus) {
    if (previousStatus) {
      this.classList.remove(this._toClassName(previousStatus));
    }
    this.classList.add(this._toClassName(status));

    switch (status) {
      case ChangeStates.WIP:
        this.tooltipText = WIP_TOOLTIP;
        break;
      case ChangeStates.PRIVATE:
        this.tooltipText = PRIVATE_TOOLTIP;
        break;
      default:
        this.tooltipText = '';
        break;
    }
  }
});
