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
  <style include="gr-change-list-styles">
    #changeList {
      border-collapse: collapse;
      width: 100%;
    }
    .section-count-label {
      color: var(--deemphasized-text-color);
      font-family: var(--font-family);
      font-size: var(--font-size-small);
      font-weight: var(--font-weight-normal);
      line-height: var(--line-height-small);
    }
    a.section-title:hover {
      text-decoration: none;
    }
    a.section-title:hover .section-count-label {
      text-decoration: none;
    }
    a.section-title:hover .section-name {
      text-decoration: underline;
    }
  </style>
  <table id="changeList">
    <template
      is="dom-repeat"
      items="[[sections]]"
      as="changeSection"
      index-as="sectionIndex"
    >
      <template is="dom-if" if="[[changeSection.name]]">
        <tbody>
          <tr class="groupHeader">
            <td aria-hidden="true" class="leftPadding"></td>
            <td
              aria-hidden="true"
              class="star"
              hidden$="[[!showStar]]"
              hidden=""
            ></td>
            <td
              class="cell"
              colspan$="[[_computeColspan(changeSection, visibleChangeTableColumns, labelNames)]]"
            >
              <h2 class="heading-3">
                <a
                  href$="[[_sectionHref(changeSection.query)]]"
                  class="section-title"
                >
                  <span class="section-name">[[changeSection.name]]</span>
                  <span class="section-count-label"
                    >[[changeSection.countLabel]]</span
                  >
                </a>
              </h2>
            </td>
          </tr>
        </tbody>
      </template>
      <tbody class="groupContent">
        <template is="dom-if" if="[[_isEmpty(changeSection)]]">
          <tr class="noChanges">
            <td aria-hidden="true" class="leftPadding"></td>
            <td
              aria-hidden="[[!showStar]]"
              class="star"
              hidden$="[[!showStar]]"
            ></td>
            <td
              class="cell"
              colspan$="[[_computeColspan(changeSection, visibleChangeTableColumns, labelNames)]]"
            >
              <template
                is="dom-if"
                if="[[_getSpecialEmptySlot(changeSection)]]"
              >
                <slot name="[[_getSpecialEmptySlot(changeSection)]]"></slot>
              </template>
              <template
                is="dom-if"
                if="[[!_getSpecialEmptySlot(changeSection)]]"
              >
                No changes
              </template>
            </td>
          </tr>
        </template>
        <template is="dom-if" if="[[!_isEmpty(changeSection)]]">
          <tr class="groupTitle">
            <td aria-hidden="true" class="leftPadding"></td>
            <td
              aria-label="Star status column"
              class="star"
              hidden$="[[!showStar]]"
              hidden=""
            ></td>
            <td class="number" hidden$="[[!showNumber]]" hidden="">#</td>
            <template
              is="dom-repeat"
              items="[[_computeColumns(changeSection, visibleChangeTableColumns)]]"
              as="item"
            >
              <td class$="[[_lowerCase(item)]]">[[item]]</td>
            </template>
            <template is="dom-repeat" items="[[labelNames]]" as="labelName">
              <td class="label" title$="[[labelName]]">
                [[_computeLabelShortcut(labelName)]]
              </td>
            </template>
            <template
              is="dom-repeat"
              items="[[_dynamicHeaderEndpoints]]"
              as="pluginHeader"
            >
              <td class="endpoint">
                <gr-endpoint-decorator name$="[[pluginHeader]]">
                </gr-endpoint-decorator>
              </td>
            </template>
          </tr>
        </template>
        <template is="dom-repeat" items="[[changeSection.results]]" as="change">
          <gr-change-list-item
            account="[[account]]"
            selected$="[[_computeItemSelected(sectionIndex, index, selectedIndex)]]"
            highlight$="[[_computeItemHighlight(account, change, changeSection.name)]]"
            change="[[change]]"
            config="[[_config]]"
            section-name="[[changeSection.name]]"
            visible-change-table-columns="[[_computeColumns(changeSection, visibleChangeTableColumns)]]"
            show-number="[[showNumber]]"
            show-star="[[showStar]]"
            tabindex$="[[_computeTabIndex(sectionIndex, index, selectedIndex, isCursorMoving)]]"
            label-names="[[labelNames]]"
            aria-label$="[[_computeAriaLabel(change, changeSection.name)]]"
          ></gr-change-list-item>
        </template>
      </tbody>
    </template>
  </table>
`;
