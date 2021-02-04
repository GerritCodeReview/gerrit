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
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-menu-page-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-page-nav-styles">
    gr-dropdown-list {
      --trigger-style: {
        text-transform: none;
      }
    }
    .breadcrumbText {
      /* Same as dropdown trigger so chevron spacing is consistent. */
      padding: 5px 4px;
    }
    iron-icon {
      margin: 0 var(--spacing-xs);
    }
    .breadcrumb {
      align-items: center;
      display: flex;
    }
    .mainHeader {
      align-items: baseline;
      border-bottom: 1px solid var(--border-color);
      display: flex;
    }
    .selectText {
      display: none;
    }
    .selectText.show {
      display: inline-block;
    }
    .main.breadcrumbs:not(.table) {
      margin-top: var(--spacing-l);
    }
  </style>
  <gr-page-nav class="navStyles">
    <ul class="sectionContent">
      <template id="adminNav" is="dom-repeat" items="[[_filteredLinks]]">
        <li class$="sectionTitle [[_computeSelectedClass(item.view, params)]]">
          <a class="title" href="[[_computeLinkURL(item)]]" rel="noopener"
            >[[item.name]]</a
          >
        </li>
        <template is="dom-repeat" items="[[item.children]]" as="child">
          <li class$="[[_computeSelectedClass(child.view, params)]]">
            <a href$="[[_computeLinkURL(child)]]" rel="noopener"
              >[[child.name]]</a
            >
          </li>
        </template>
        <template is="dom-if" if="[[item.subsection]]">
          <!--If a section has a subsection, render that.-->
          <li class$="[[_computeSelectedClass(item.subsection.view, params)]]">
            <a
              class="title"
              href$="[[_computeLinkURL(item.subsection)]]"
              rel="noopener"
            >
              [[item.subsection.name]]</a
            >
          </li>
          <!--Loop through the links in the sub-section.-->
          <template
            is="dom-repeat"
            items="[[item.subsection.children]]"
            as="child"
          >
            <li
              class$="subsectionItem [[_computeSelectedClass(child.view, params, child.detailType)]]"
            >
              <a href$="[[_computeLinkURL(child)]]">[[child.name]]</a>
            </li>
          </template>
        </template>
      </template>
    </ul>
  </gr-page-nav>
  <template is="dom-if" if="[[_subsectionLinks.length]]">
    <section class="mainHeader">
      <span class="breadcrumb">
        <span class="breadcrumbText">[[_breadcrumbParentName]]</span>
        <iron-icon icon="gr-icons:chevron-right"></iron-icon>
      </span>
      <gr-dropdown-list
        lowercase=""
        id="pageSelect"
        value="[[_computeSelectValue(params)]]"
        items="[[_subsectionLinks]]"
        on-value-change="_handleSubsectionChange"
      >
      </gr-dropdown-list>
    </section>
  </template>
  <template is="dom-if" if="[[_showRepoList]]" restamp="true">
    <div class="main table">
      <gr-repo-list class="table" params="[[params]]"></gr-repo-list>
    </div>
  </template>
  <template is="dom-if" if="[[_showGroupList]]" restamp="true">
    <div class="main table">
      <gr-admin-group-list class="table" params="[[params]]">
      </gr-admin-group-list>
    </div>
  </template>
  <template is="dom-if" if="[[_showPluginList]]" restamp="true">
    <div class="main table">
      <gr-plugin-list class="table" params="[[params]]"></gr-plugin-list>
    </div>
  </template>
  <template is="dom-if" if="[[_showRepoMain]]" restamp="true">
    <div class="main breadcrumbs">
      <gr-repo repo="[[params.repo]]"></gr-repo>
    </div>
  </template>
  <template is="dom-if" if="[[_showGroup]]" restamp="true">
    <div class="main breadcrumbs">
      <gr-group
        group-id="[[params.groupId]]"
        on-name-changed="_updateGroupName"
      ></gr-group>
    </div>
  </template>
  <template is="dom-if" if="[[_showGroupMembers]]" restamp="true">
    <div class="main breadcrumbs">
      <gr-group-members group-id="[[params.groupId]]"></gr-group-members>
    </div>
  </template>
  <template is="dom-if" if="[[_showRepoDetailList]]" restamp="true">
    <div class="main table breadcrumbs">
      <gr-repo-detail-list
        params="[[params]]"
        class="table"
      ></gr-repo-detail-list>
    </div>
  </template>
  <template is="dom-if" if="[[_showGroupAuditLog]]" restamp="true">
    <div class="main table breadcrumbs">
      <gr-group-audit-log
        group-id="[[params.groupId]]"
        class="table"
      ></gr-group-audit-log>
    </div>
  </template>
  <template is="dom-if" if="[[_showRepoCommands]]" restamp="true">
    <div class="main breadcrumbs">
      <gr-repo-commands repo="[[params.repo]]"></gr-repo-commands>
    </div>
  </template>
  <template is="dom-if" if="[[_showRepoAccess]]" restamp="true">
    <div class="main breadcrumbs">
      <gr-repo-access path="[[path]]" repo="[[params.repo]]"></gr-repo-access>
    </div>
  </template>
  <template is="dom-if" if="[[_showRepoDashboards]]" restamp="true">
    <div class="main table breadcrumbs">
      <gr-repo-dashboards repo="[[params.repo]]"></gr-repo-dashboards>
    </div>
  </template>
`;
