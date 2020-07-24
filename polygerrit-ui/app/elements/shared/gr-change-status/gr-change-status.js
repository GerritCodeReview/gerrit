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
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-tooltip-content/gr-tooltip-content.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {setFavicon} from '../../../utils/dom-util.js';
import {htmlTemplate} from './gr-change-status_html.js';

const ChangeStates = {
  MERGED: 'Merged',
  ABANDONED: 'Abandoned',
  MERGE_CONFLICT: 'Merge Conflict',
  WIP: 'WIP',
  PRIVATE: 'Private',
  ACTIVE: 'Active',
};

const FaviconPaths = {
  MERGED: '/favicons/merged.ico',
  ABANDONED: '/favicons/abandoned.ico',
  MERGE_CONFLICT: '/favicons/merge_conflict.ico',
  WIP: '/favicons/wip.ico',
  PRIVATE: '/favicons/private.ico',
  ACTIVE: '/favicons/active.ico',
};

const WIP_TOOLTIP = 'This change isn\'t ready to be reviewed or submitted. ' +
    'It will not appear on dashboards unless you are CC\'ed or assigned, ' +
    'and email notifications will be silenced until the review is started.';

const MERGE_CONFLICT_TOOLTIP = 'This change has merge conflicts. ' +
    'Download the patch and run "git rebase master". ' +
    'Upload a new patchset after resolving all merge conflicts.';

const PRIVATE_TOOLTIP = 'This change is only visible to its owner and ' +
    'current reviewers (or anyone with "View Private Changes" permission).';

/** @extends PolymerElement */
class GrChangeStatus extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

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
        observer: '_updateUIDetails',
      },
      tooltipText: {
        type: String,
        value: '',
      },
      faviconPath: {
        type: String,
        value: '',
      }
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

  _updateUIDetails(status, previousStatus) {
    if (previousStatus) {
      this.classList.remove(this._toClassName(previousStatus));
    }
    this.classList.add(this._toClassName(status));

    this.tooltipText = '';
    switch (status) {
      case ChangeStates.WIP:
        this.tooltipText = WIP_TOOLTIP;
        this.faviconPath = FaviconPaths.WIP;
        break;
      case ChangeStates.PRIVATE:
        this.tooltipText = PRIVATE_TOOLTIP;
        this.faviconPath = FaviconPaths.PRIVATE;
        break;
      case ChangeStates.MERGE_CONFLICT:
        this.tooltipText = MERGE_CONFLICT_TOOLTIP;
        this.faviconPath = FaviconPaths.MERGE_CONFLICT;
        break;
      case ChangeStates.MERGED:
        this.faviconPath = FaviconPaths.MERGED;
        break;
      case ChangeStates.ABANDONED:
        this.faviconPath = FaviconPaths.ABANDONED;
        break;
      case ChangeStates.ACTIVE:
        this.faviconPath = FaviconPaths.ACTIVE;
        break;
    }

    setFavicon(this.faviconPath);
  }
}

customElements.define(GrChangeStatus.is, GrChangeStatus);
