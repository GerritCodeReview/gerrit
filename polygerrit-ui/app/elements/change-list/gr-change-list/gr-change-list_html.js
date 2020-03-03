import { html } from '@polymer/polymer/lib/utils/html-tag.js';

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
      <template is="dom-repeat" items="[[sections]]" as="changeSection" index-as="sectionIndex">
        <template is="dom-if" if="[[changeSection.name]]">
          <tbody>
            <tr class="groupHeader">
              <td class="leftPadding"></td>
              <td class="star" hidden\$="[[!showStar]]" hidden=""></td>
              <td class="cell" colspan\$="[[_computeColspan(changeTableColumns, labelNames)]]">
                <a href\$="[[_sectionHref(changeSection.query)]]" class="section-title">
                  <span class="section-name">[[changeSection.name]]</span>
                  <span class="section-count-label">[[changeSection.countLabel]]</span>
                </a>
              </td>
            </tr>
          </tbody>
        </template>
        <tbody class="groupContent">
          <template is="dom-if" if="[[_isEmpty(changeSection)]]">
            <tr class="noChanges">
              <td class="leftPadding"></td>
              <td class="star" hidden\$="[[!showStar]]" hidden=""></td>
              <td class="cell" colspan\$="[[_computeColspan(changeTableColumns, labelNames)]]">
                <template is="dom-if" if="[[_isOutgoing(changeSection)]]">
                  <slot name="empty-outgoing"></slot>
                </template>
                <template is="dom-if" if="[[!_isOutgoing(changeSection)]]">
                  No changes
                </template>
              </td>
            </tr>
          </template>
          <template is="dom-if" if="[[!_isEmpty(changeSection)]]">
            <tr class="groupTitle">
              <td class="leftPadding"></td>
              <td class="star" hidden\$="[[!showStar]]" hidden=""></td>
              <td class="number" hidden\$="[[!showNumber]]" hidden="">#</td>
              <template is="dom-repeat" items="[[changeTableColumns]]" as="item">
                <td class\$="[[_lowerCase(item)]]" hidden\$="[[isColumnHidden(item, visibleChangeTableColumns)]]">
                  [[item]]
                </td>
              </template>
              <template is="dom-repeat" items="[[labelNames]]" as="labelName">
                <td class="label" title\$="[[labelName]]">
                  [[_computeLabelShortcut(labelName)]]
                </td>
              </template>
              <template is="dom-repeat" items="[[_dynamicHeaderEndpoints]]" as="pluginHeader">
                <td class="endpoint">
                  <gr-endpoint-decorator name\$="[[pluginHeader]]">
                  </gr-endpoint-decorator>
                </td>
              </template>
            </tr>
          </template>
          <template is="dom-repeat" items="[[changeSection.results]]" as="change">
            <gr-change-list-item selected\$="[[_computeItemSelected(sectionIndex, index, selectedIndex)]]" highlight\$="[[_computeItemHighlight(account, change)]]" needs-review\$="[[_computeItemNeedsReview(account, change, showReviewedState)]]" change="[[change]]" visible-change-table-columns="[[visibleChangeTableColumns]]" show-number="[[showNumber]]" show-star="[[showStar]]" tabindex="0" label-names="[[labelNames]]"></gr-change-list-item>
          </template>
        </tbody>
      </template>
    </table>
    <gr-cursor-manager id="cursor" index="{{selectedIndex}}" scroll-behavior="keep-visible" focus-on-move=""></gr-cursor-manager>
`;
