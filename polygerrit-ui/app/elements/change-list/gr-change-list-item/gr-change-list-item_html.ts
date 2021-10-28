/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    :host {
      display: table-row;
      color: var(--primary-text-color);
    }
    :host(:focus) {
      outline: none;
    }
    :host(:hover) {
      background-color: var(--hover-background-color);
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
    .comments,
    .reviewers,
    .requirements {
      white-space: nowrap;
    }
    .reviewers {
      --account-max-length: 70px;
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
      padding-right: var(--spacing-xs);
    }
    /* Used to hide the leading separator comma for statuses. */
    .status .comma:first-of-type {
      display: none;
    }
    .size gr-tooltip-content {
      margin: -0.4rem -0.6rem;
      max-width: 2.5rem;
      padding: var(--spacing-m) var(--spacing-l);
    }
    a {
      color: inherit;
      cursor: pointer;
      text-decoration: none;
    }
    a:hover {
      text-decoration: underline;
    }
    .subject:hover .content {
      text-decoration: underline;
    }
    .u-monospace {
      font-family: var(--monospace-font-family);
      font-size: var(--font-size-mono);
      line-height: var(--line-height-mono);
    }
    .u-green,
    .u-green iron-icon {
      color: var(--positive-green-text-color);
    }
    .u-red,
    .u-red iron-icon {
      color: var(--negative-red-text-color);
    }
    .u-gray-background {
      background-color: var(--table-header-background-color);
    }
    .comma,
    .placeholder {
      color: var(--deemphasized-text-color);
    }
    .cell.label {
      font-weight: var(--font-weight-normal);
    }
    .cell.label iron-icon {
      vertical-align: top;
    }
    @media only screen and (max-width: 50em) {
      :host {
        display: flex;
      }
    }
  </style>
  <style include="gr-change-list-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <td aria-hidden="true" class="cell leftPadding"></td>
  <td class="cell star" hidden$="[[!showStar]]" hidden="">
    <gr-change-star change="{{change}}"></gr-change-star>
  </td>
  <td class="cell number" hidden$="[[!showNumber]]" hidden="">
    <a href$="[[changeURL]]">[[change._number]]</a>
  </td>
  <td
    class="cell subject"
    hidden$="[[_computeIsColumnHidden('Subject', visibleChangeTableColumns)]]"
  >
    <a
      title$="[[change.subject]]"
      href$="[[changeURL]]"
      on-click="_handleChangeClick"
    >
      <div class="container">
        <div class="content">[[change.subject]]</div>
        <div class="spacer">[[change.subject]]</div>
        <span>&nbsp;</span>
      </div>
    </a>
  </td>
  <td
    class="cell status"
    hidden$="[[_computeIsColumnHidden('Status', visibleChangeTableColumns)]]"
  >
    <template is="dom-repeat" items="[[statuses]]" as="status">
      <div class="comma">,</div>
      <gr-change-status flat="" status="[[status]]"></gr-change-status>
    </template>
    <template is="dom-if" if="[[!statuses.length]]">
      <span class="placeholder">--</span>
    </template>
  </td>
  <td
    class="cell owner"
    hidden$="[[_computeIsColumnHidden('Owner', visibleChangeTableColumns)]]"
  >
    <gr-account-link
      highlightAttention
      change="[[change]]"
      account="[[change.owner]]"
    ></gr-account-link>
  </td>
  <td
    class="cell assignee"
    hidden$="[[_computeIsColumnHidden('Assignee', visibleChangeTableColumns)]]"
  >
    <template is="dom-if" if="[[change.assignee]]">
      <gr-account-link
        id="assigneeAccountLink"
        account="[[change.assignee]]"
      ></gr-account-link>
    </template>
    <template is="dom-if" if="[[!change.assignee]]">
      <span class="placeholder">--</span>
    </template>
  </td>
  <td
    class="cell reviewers"
    hidden$="[[_computeIsColumnHidden('Reviewers', visibleChangeTableColumns)]]"
  >
    <div>
      <template
        is="dom-repeat"
        items="[[_computePrimaryReviewers(change)]]"
        as="reviewer"
        indexAs="index"
      >
        <gr-account-link
          hideAvatar=""
          hideStatus=""
          firstName
          highlightAttention
          change="[[change]]"
          account="[[reviewer]]"
        ></gr-account-link
        ><span
          hidden$="[[_computeCommaHidden(index, change)]]"
          aria-hidden="true"
          >,
        </span>
      </template>
      <template is="dom-if" if="[[_computeAdditionalReviewersCount(change)]]">
        <span title="[[_computeAdditionalReviewersTitle(change, config)]]">
          +[[_computeAdditionalReviewersCount(change)]]
        </span>
      </template>
    </div>
  </td>
  <td
    class="cell comments"
    hidden$="[[_computeIsColumnHidden('Comments', visibleChangeTableColumns)]]"
  >
    <iron-icon
      hidden$="[[!change.unresolved_comment_count]]"
      icon="gr-icons:comment"
    ></iron-icon>
    <span>[[_computeComments(change.unresolved_comment_count)]]</span>
  </td>
  <td
    class="cell repo"
    hidden$="[[_computeIsColumnHidden('Repo', visibleChangeTableColumns)]]"
  >
    <a class="fullRepo" href$="[[_computeRepoUrl(change)]]">
      [[_computeRepoDisplay(change)]]
    </a>
    <a
      class="truncatedRepo"
      href$="[[_computeRepoUrl(change)]]"
      title$="[[_computeRepoDisplay(change)]]"
    >
      [[_computeTruncatedRepoDisplay(change)]]
    </a>
  </td>
  <td
    class="cell branch"
    hidden$="[[_computeIsColumnHidden('Branch', visibleChangeTableColumns)]]"
  >
    <a href$="[[_computeRepoBranchURL(change)]]"> [[change.branch]] </a>
    <template is="dom-if" if="[[change.topic]]">
      (<a href$="[[_computeTopicURL(change)]]"
        ><!--
       --><gr-limited-text limit="50" text="[[change.topic]]"> </gr-limited-text
        ><!--
     --></a
      >)
    </template>
  </td>
  <td
    class="cell updated"
    hidden$="[[_computeIsColumnHidden('Updated', visibleChangeTableColumns)]]"
  >
    <gr-date-formatter
      withTooltip
      date-str="[[_formatDate(change.updated)]]"
    ></gr-date-formatter>
  </td>
  <td
    class="cell submitted"
    hidden$="[[_computeIsColumnHidden('Submitted', visibleChangeTableColumns)]]"
  >
    <gr-date-formatter
      withTooltip
      date-str="[[_formatDate(change.submitted)]]"
    ></gr-date-formatter>
  </td>
  <td
    class="cell waiting"
    hidden$="[[_computeIsColumnHidden('Waiting', visibleChangeTableColumns)]]"
  >
    <gr-date-formatter
      withTooltip
      forceRelative
      relativeOptionNoAgo
      date-str="[[_computeWaiting(account, change)]]"
    ></gr-date-formatter>
  </td>
  <td
    class="cell size"
    hidden$="[[_computeIsColumnHidden('Size', visibleChangeTableColumns)]]"
  >
    <gr-tooltip-content has-tooltip title="[[_computeSizeTooltip(change)]]">
      <template is="dom-if" if="[[_changeSize]]">
        <span>[[_changeSize]]</span>
      </template>
      <template is="dom-if" if="[[!_changeSize]]">
        <span class="placeholder">--</span>
      </template>
    </gr-tooltip-content>
  </td>
  <td
    class="cell requirements"
    hidden$="[[_computeIsColumnHidden('Requirements', visibleChangeTableColumns)]]"
  >
    <gr-change-list-column-requirements change="[[change]]">
    </gr-change-list-column-requirements>
  </td>
  <template is="dom-repeat" items="[[labelNames]]" as="labelName">
    <td
      title$="[[_computeLabelTitle(change, labelName)]]"
      class$="[[_computeLabelClass(change, labelName)]]"
    >
      <template is="dom-if" if="[[_computeHasLabelIcon(change, labelName)]]">
        <iron-icon icon="[[_computeLabelIcon(change, labelName)]]"></iron-icon>
      </template>
      <template is="dom-if" if="[[!_computeHasLabelIcon(change, labelName)]]">
        <span>[[_computeLabelValue(change, labelName)]]</span>
      </template>
    </td>
  </template>
  <template
    is="dom-repeat"
    items="[[_dynamicCellEndpoints]]"
    as="pluginEndpointName"
  >
    <td class="cell endpoint">
      <gr-endpoint-decorator name$="[[pluginEndpointName]]">
        <gr-endpoint-param name="change" value="[[change]]">
        </gr-endpoint-param>
      </gr-endpoint-decorator>
    </td>
  </template>
`;
