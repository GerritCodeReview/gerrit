/**
@license
Copyright (C) 2015 The Android Open Source Project

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
import '../../../behaviors/base-url-behavior/base-url-behavior.js';

import '../../../behaviors/gr-change-table-behavior/gr-change-table-behavior.js';
import '../../../behaviors/gr-path-list-behavior/gr-path-list-behavior.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../behaviors/rest-client-behavior/rest-client-behavior.js';
import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../../styles/gr-change-list-styles.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-account-link/gr-account-link.js';
import '../../shared/gr-change-star/gr-change-star.js';
import '../../shared/gr-change-status/gr-change-status.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-limited-text/gr-limited-text.js';
import '../../shared/gr-tooltip-content/gr-tooltip-content.js';
import '../../../styles/shared-styles.js';

const CHANGE_SIZE = {
  XS: 10,
  SMALL: 50,
  MEDIUM: 250,
  LARGE: 1000,
};

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: table-row;
      }
      :host(:focus) {
        outline: none;
      }
      :host(:hover) {
        background-color: var(--hover-background-color);
      }
      :host([needs-review]) {
        font-family: var(--font-family-bold);
      }
      :host([highlight]) {
        background-color: var(--assignee-highlight-color);
      }
      .container {
        position: relative;
      }
      .content {
        overflow: hidden;
        position: absolute;
        text-overflow: ellipsis;
        white-space: nowrap;
        width: 100%;
      }
      .content a {
        display: block;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        width: 100%;
      }
      .spacer {
        height: 0;
        overflow: hidden;
      }
      .status {
        align-items: center;
        display: inline-flex;
      }
      .status .comma {
        padding-right: .2rem;
      }
      /* Used to hide the leading separator comma for statuses. */
      .status .comma:first-of-type {
        display: none;
      }
      .size gr-tooltip-content {
        margin: -.4rem -.6rem;
        max-width: 2.5rem;
        padding: .4rem .6rem;
      }
      a {
        color: var(--primary-text-color);
        cursor: pointer;
        display: inline-block;
        text-decoration: none;
      }
      a:hover {
        text-decoration: underline;
      }
      .u-monospace {
        font-family: var(--monospace-font-family);
      }
      .u-green {
        color: var(--vote-text-color-recommended);
      }
      .u-red {
        color: var(--vote-text-color-disliked);
      }
      .label.u-green:not(.u-monospace),
      .label.u-red:not(.u-monospace) {
        font-size: 1.2rem;
      }
      .u-gray-background {
        background-color: var(--table-header-background-color);
      }
      .comma,
      .placeholder {
        color: var(--deemphasized-text-color);
      }
      @media only screen and (max-width: 50em) {
        :host {
          display: flex;
        }
      }
    </style>
    <style include="gr-change-list-styles"></style>
    <td class="cell leftPadding"></td>
    <td class="cell star" hidden\$="[[!showStar]]" hidden="">
      <gr-change-star change="{{change}}"></gr-change-star>
    </td>
    <td class="cell number" hidden\$="[[!showNumber]]" hidden="">
      <a href\$="[[changeURL]]">[[change._number]]</a>
    </td>
    <td class="cell subject" hidden\$="[[isColumnHidden('Subject', visibleChangeTableColumns)]]">
      <div class="container">
        <div class="content">
          <a title\$="[[change.subject]]" href\$="[[changeURL]]">
            [[change.subject]]
          </a>
        </div>
        <div class="spacer">
           [[change.subject]]
        </div>
        <span>&nbsp;</span>
      </div>
    </td>
    <td class="cell status" hidden\$="[[isColumnHidden('Status', visibleChangeTableColumns)]]">
      <template is="dom-repeat" items="[[statuses]]" as="status">
        <div class="comma">,</div>
        <gr-change-status flat="" status="[[status]]"></gr-change-status>
      </template>
      <template is="dom-if" if="[[!statuses.length]]">
        <span class="placeholder">--</span>
      </template>
    </td>
    <td class="cell owner" hidden\$="[[isColumnHidden('Owner', visibleChangeTableColumns)]]">
      <gr-account-link account="[[change.owner]]" additional-text="[[_computeAccountStatusString(change.owner)]]"></gr-account-link>
    </td>
    <td class="cell assignee" hidden\$="[[isColumnHidden('Assignee', visibleChangeTableColumns)]]">
      <template is="dom-if" if="[[change.assignee]]">
        <gr-account-link account="[[change.assignee]]" additional-text="[[_computeAccountStatusString(change.owner)]]"></gr-account-link>
      </template>
      <template is="dom-if" if="[[!change.assignee]]">
        <span class="placeholder">--</span>
      </template>
    </td>
    <td class="cell repo" hidden\$="[[isColumnHidden('Repo', visibleChangeTableColumns)]]">
      <a class="fullRepo" href\$="[[_computeRepoUrl(change)]]">
        [[_computeRepoDisplay(change)]]
      </a>
      <a class="truncatedRepo" href\$="[[_computeRepoUrl(change)]]" title\$="[[_computeRepoDisplay(change)]]">
        [[_computeRepoDisplay(change, 'true')]]
      </a>
    </td>
    <td class="cell branch" hidden\$="[[isColumnHidden('Branch', visibleChangeTableColumns)]]">
      <a href\$="[[_computeRepoBranchURL(change)]]">
        [[change.branch]]
      </a>
      <template is="dom-if" if="[[change.topic]]">
        (<a href\$="[[_computeTopicURL(change)]]"><!--
       --><gr-limited-text limit="50" text="[[change.topic]]">
          </gr-limited-text><!--
     --></a>)
      </template>
    </td>
    <td class="cell updated" hidden\$="[[isColumnHidden('Updated', visibleChangeTableColumns)]]">
      <gr-date-formatter has-tooltip="" date-str="[[change.updated]]"></gr-date-formatter>
    </td>
    <td class="cell size" hidden\$="[[isColumnHidden('Size', visibleChangeTableColumns)]]">
      <gr-tooltip-content has-tooltip="" title="[[_computeSizeTooltip(change)]]">
        <template is="dom-if" if="[[_changeSize]]">
            <span>[[_changeSize]]</span>
        </template>
        <template is="dom-if" if="[[!_changeSize]]">
            <span class="placeholder">--</span>
        </template>
      </gr-tooltip-content>
    </td>
    <template is="dom-repeat" items="[[labelNames]]" as="labelName">
      <td title\$="[[_computeLabelTitle(change, labelName)]]" class\$="[[_computeLabelClass(change, labelName)]]">
        [[_computeLabelValue(change, labelName)]]
      </td>
    </template>
`,

  is: 'gr-change-list-item',

  properties: {
    visibleChangeTableColumns: Array,
    labelNames: {
      type: Array,
    },

    /** @type {?} */
    change: Object,
    changeURL: {
      type: String,
      computed: '_computeChangeURL(change)',
    },
    needsReview: {
      type: Boolean,
      reflectToAttribute: true,
      computed: '_computeItemNeedsReview(change.reviewed)',
    },
    statuses: {
      type: Array,
      computed: 'changeStatuses(change)',
    },
    showStar: {
      type: Boolean,
      value: false,
    },
    showNumber: Boolean,
    _changeSize: {
      type: String,
      computed: '_computeChangeSize(change)',
    },
  },

  behaviors: [
    Gerrit.BaseUrlBehavior,
    Gerrit.ChangeTableBehavior,
    Gerrit.PathListBehavior,
    Gerrit.RESTClientBehavior,
    Gerrit.URLEncodingBehavior,
  ],

  _computeItemNeedsReview(reviewed) {
    return !reviewed;
  },

  _computeChangeURL(change) {
    return Gerrit.Nav.getUrlForChange(change);
  },

  _computeLabelTitle(change, labelName) {
    const label = change.labels[labelName];
    if (!label) { return 'Label not applicable'; }
    const significantLabel = label.rejected || label.approved ||
        label.disliked || label.recommended;
    if (significantLabel && significantLabel.name) {
      return labelName + '\nby ' + significantLabel.name;
    }
    return labelName;
  },

  _computeLabelClass(change, labelName) {
    const label = change.labels[labelName];
    // Mimic a Set.
    const classes = {
      cell: true,
      label: true,
    };
    if (label) {
      if (label.approved) {
        classes['u-green'] = true;
      }
      if (label.value == 1) {
        classes['u-monospace'] = true;
        classes['u-green'] = true;
      } else if (label.value == -1) {
        classes['u-monospace'] = true;
        classes['u-red'] = true;
      }
      if (label.rejected) {
        classes['u-red'] = true;
      }
    } else {
      classes['u-gray-background'] = true;
    }
    return Object.keys(classes).sort().join(' ');
  },

  _computeLabelValue(change, labelName) {
    const label = change.labels[labelName];
    if (!label) { return ''; }
    if (label.approved) {
      return '✓';
    }
    if (label.rejected) {
      return '✕';
    }
    if (label.value > 0) {
      return '+' + label.value;
    }
    if (label.value < 0) {
      return label.value;
    }
    return '';
  },

  _computeRepoUrl(change) {
    return Gerrit.Nav.getUrlForProjectChanges(change.project, true,
        change.internalHost);
  },

  _computeRepoBranchURL(change) {
    return Gerrit.Nav.getUrlForBranch(change.branch, change.project, null,
        change.internalHost);
  },

  _computeTopicURL(change) {
    if (!change.topic) { return ''; }
    return Gerrit.Nav.getUrlForTopic(change.topic, change.internalHost);
  },

  /**
   * Computes the display string for the project column. If there is a host
   * specified in the change detail, the string will be prefixed with it.
   *
   * @param {!Object} change
   * @param {string=} truncate whether or not the project name should be
   *     truncated. If this value is truthy, the name will be truncated.
   * @return {string}
   */
  _computeRepoDisplay(change, truncate) {
    if (!change || !change.project) { return ''; }
    let str = '';
    if (change.internalHost) { str += change.internalHost + '/'; }
    str += truncate ? this.truncatePath(change.project, 2) : change.project;
    return str;
  },

  _computeAccountStatusString(account) {
    return account && account.status ? `(${account.status})` : '';
  },

  _computeSizeTooltip(change) {
    if (change.insertions + change.deletions === 0 ||
        isNaN(change.insertions + change.deletions)) {
      return 'Size unknown';
    } else {
      return `+${change.insertions}, -${change.deletions}`;
    }
  },

  /**
   * TShirt sizing is based on the following paper:
   * http://dirkriehle.com/wp-content/uploads/2008/09/hicss-42-csdistr-final-web.pdf
   */
  _computeChangeSize(change) {
    const delta = change.insertions + change.deletions;
    if (isNaN(delta) || delta === 0) {
      return null; // Unknown
    }
    if (delta < CHANGE_SIZE.XS) {
      return 'XS';
    } else if (delta < CHANGE_SIZE.SMALL) {
      return 'S';
    } else if (delta < CHANGE_SIZE.MEDIUM) {
      return 'M';
    } else if (delta < CHANGE_SIZE.LARGE) {
      return 'L';
    } else {
      return 'XL';
    }
  },

  toggleReviewed() {
    const newVal = !this.change.reviewed;
    this.set('change.reviewed', newVal);
    this.dispatchEvent(new CustomEvent('toggle-reviewed', {
      bubbles: true,
      detail: {change: this.change, reviewed: newVal},
    }));
  }
});
