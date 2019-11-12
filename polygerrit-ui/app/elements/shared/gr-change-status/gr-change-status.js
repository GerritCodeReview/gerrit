/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
(function() {
  'use strict';

  const ChangeStates = {
    MERGED: 'Merged',
    ABANDONED: 'Abandoned',
    MERGE_CONFLICT: 'Merge Conflict',
    WIP: 'WIP',
    PRIVATE: 'Private',
  };

  const WIP_TOOLTIP = 'This change isn\'t ready to be reviewed or submitted. ' +
      'It will not appear on dashboards unless you are CC\'ed or assigned, ' +
      'and email notifications will be silenced until the review is started.';

  const MERGE_CONFLICT_TOOLTIP = 'This change has merge conflicts. ' +
      'Download the patch and run "git rebase master". ' +
      'Upload a new patchset after resolving all merge conflicts.';

  const PRIVATE_TOOLTIP = 'This change is only visible to its owner and ' +
      'current reviewers (or anyone with "View Private Changes" permission).';

  class GrChangeStatus extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-change-status'; }

    static get properties() {
      return {
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
      };
    }

    _computeStatusString(status) {
      if (status === ChangeStates.WIP && !this.flat) {
        return 'Work in Progress';
      }
      return status;
    }

    _toClassName(str) {
      return str.toLowerCase().replace(/\s/g, '-');
    }

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
        case ChangeStates.MERGE_CONFLICT:
          this.tooltipText = MERGE_CONFLICT_TOOLTIP;
          break;
        default:
          this.tooltipText = '';
          break;
      }
    }
  }

  customElements.define(GrChangeStatus.is, GrChangeStatus);
})();
