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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
  <style include="shared-styles">
    :host {
      display: block;
    }
    .row {
      align-items: center;
      border-top: 1px solid var(--border-color);
      display: flex;
      min-height: calc(var(--line-height-normal) + 2 * var(--spacing-s));
      padding: var(--spacing-xs) var(--spacing-l);
    }
    /* The class defines a content visible only to screen readers */
    .noCommentsScreenReaderText {
      opacity: 0;
      max-width: 1px;
      overflow: hidden;
      display: none;
    }
    div[role='gridcell']
      > div.comments
      > span:empty
      + span:empty
      + span.noCommentsScreenReaderText {
      display: inline;
    }
    :host(.loading) .row {
      opacity: 0.5;
    }
    :host(.editMode) .hideOnEdit {
      display: none;
    }
    .showOnEdit {
      display: none;
    }
    :host(.editMode) .showOnEdit {
      display: initial;
    }
    .invisible {
      visibility: hidden;
    }
    .header-row {
      background-color: var(--background-color-secondary);
    }
    .controlRow {
      align-items: center;
      display: flex;
      height: 2.25em;
      justify-content: center;
    }
    .controlRow.invisible,
    .show-hide.invisible {
      display: none;
    }
    .reviewed,
    .status {
      align-items: center;
      display: inline-flex;
    }
    .reviewed {
      display: inline-block;
      text-align: left;
      width: 1.5em;
    }
    .status {
      display: inline-block;
      border-radius: var(--border-radius);
      margin-left: var(--spacing-s);
      padding: 0 var(--spacing-m);
      color: var(--primary-text-color);
      font-size: var(--font-size-small);
      background-color: var(--dark-add-highlight-color);
    }
    .status.invisible,
    .status.M {
      display: none;
    }
    .status.D,
    .status.R,
    .status.W {
      background-color: var(--dark-remove-highlight-color);
    }
    .status.U {
      background-color: var(--comment-background-color);
    }
    .file-row {
      cursor: pointer;
    }
    .file-row.expanded {
      border-bottom: 1px solid var(--border-color);
      position: -webkit-sticky;
      position: sticky;
      top: 0;
      /* Has to visible above the diff view, and by default has a lower
         z-index. setting to 1 places it directly above. */
      z-index: 1;
    }
    .file-row:hover {
      background-color: var(--hover-background-color);
    }
    .file-row.selected {
      background-color: var(--selection-background-color);
    }
    .file-row.expanded,
    .file-row.expanded:hover {
      background-color: var(--expanded-background-color);
    }
    .path {
      cursor: pointer;
      flex: 1;
      /* Wrap it into multiple lines if too long. */
      white-space: normal;
      word-break: break-word;
    }
    .oldPath {
      color: var(--deemphasized-text-color);
    }
    .header-stats {
      text-align: center;
      min-width: 7.5em;
    }
    .stats {
      text-align: right;
      min-width: 7.5em;
    }
    .comments {
      padding-left: var(--spacing-l);
      min-width: 7.5em;
    }
    .row:not(.header-row) .stats,
    .total-stats {
      font-family: var(--monospace-font-family);
      font-size: var(--font-size-mono);
      line-height: var(--line-height-mono);
      display: flex;
    }
    .sizeBars {
      margin-left: var(--spacing-m);
      min-width: 7em;
      text-align: center;
    }
    .sizeBars.hide {
      display: none;
    }
    .added,
    .removed {
      display: inline-block;
      min-width: 3.5em;
    }
    .added {
      color: var(--positive-green-text-color);
    }
    .removed {
      color: var(--negative-red-text-color);
      text-align: left;
      min-width: 4em;
      padding-left: var(--spacing-s);
    }
    .drafts {
      color: #c62828;
      font-weight: var(--font-weight-bold);
    }
    .show-hide-icon:focus {
      outline: none;
    }
    .show-hide {
      margin-left: var(--spacing-s);
      width: 1.9em;
    }
    .fileListButton {
      margin: var(--spacing-m);
    }
    .totalChanges {
      justify-content: flex-end;
      text-align: right;
    }
    .warning {
      color: var(--deemphasized-text-color);
    }
    input.show-hide {
      display: none;
    }
    label.show-hide {
      cursor: pointer;
      display: block;
      min-width: 2em;
    }
    gr-diff {
      display: block;
      overflow-x: auto;
    }
    .truncatedFileName {
      display: none;
    }
    .mobile {
      display: none;
    }
    .reviewed {
      margin-left: var(--spacing-xxl);
      width: 15em;
    }
    .reviewedSwitch {
      color: var(--link-color);
      opacity: 0;
      justify-content: flex-end;
      width: 100%;
    }
    .reviewedSwitch:hover {
      cursor: pointer;
      opacity: 100;
    }
    .row:focus {
      outline: none;
    }
    .row:hover .reviewedSwitch,
    .row:focus-within .reviewedSwitch,
    .row.expanded .reviewedSwitch {
      opacity: 100;
    }
    .reviewedLabel {
      color: var(--deemphasized-text-color);
      margin-right: var(--spacing-l);
      opacity: 0;
    }
    .reviewedLabel.isReviewed {
      display: initial;
      opacity: 100;
    }
    .editFileControls {
      width: 7em;
    }
    .markReviewed:focus {
      outline: none;
    }
    .markReviewed,
    .pathLink {
      display: inline-block;
      margin: -2px 0;
      padding: var(--spacing-s) 0;
      text-decoration: none;
    }
    .pathLink:hover span.fullFileName,
    .pathLink:hover span.truncatedFileName {
      text-decoration: underline;
    }

    /** copy on file path **/
    .pathLink gr-copy-clipboard,
    .oldPath gr-copy-clipboard {
      display: inline-block;
      visibility: hidden;
      vertical-align: bottom;
      --gr-button: {
        padding: 0px;
      }
    }
    .row:focus-within gr-copy-clipboard,
    .row:hover gr-copy-clipboard {
      visibility: visible;
    }

    /** small screen breakpoint: 768px */
    @media screen and (max-width: 55em) {
      .desktop {
        display: none;
      }
      .mobile {
        display: block;
      }
      .row.selected {
        background-color: var(--view-background-color);
      }
      .stats {
        display: none;
      }
      .reviewed,
      .status {
        justify-content: flex-start;
      }
      .reviewed {
        display: none;
      }
      .comments {
        min-width: initial;
      }
      .expanded .fullFileName,
      .truncatedFileName {
        display: inline;
      }
      .expanded .truncatedFileName,
      .fullFileName {
        display: none;
      }
    }
    :host(.hideComments) {
      --gr-comment-thread-display: none;
    }
  </style>
  <div
    id="container"
    on-click="_handleFileListClick"
    role="grid"
    aria-label="Files list"
  >
    <div class="header-row row" role="row">
      <!-- endpoint: change-view-file-list-header-prepend -->
      <template is="dom-if" if="[[_showPrependedDynamicColumns]]">
        <template
          is="dom-repeat"
          items="[[_dynamicPrependedHeaderEndpoints]]"
          as="headerEndpoint"
        >
          <gr-endpoint-decorator name$="[[headerEndpoint]]" role="columnheader">
            <gr-endpoint-param
              name="change"
              value="[[change]]"
            ></gr-endpoint-param>
            <gr-endpoint-param name="patchRange" value="[[patchRange]]">
            </gr-endpoint-param>
          </gr-endpoint-decorator>
        </template>
      </template>
      <div class="path" role="columnheader">File</div>
      <div class="comments" role="columnheader">Comments</div>
      <div class="sizeBars" role="columnheader">Size</div>
      <div class="header-stats" role="columnheader">Delta</div>
      <!-- endpoint: change-view-file-list-header -->
      <template is="dom-if" if="[[_showDynamicColumns]]">
        <template
          is="dom-repeat"
          items="[[_dynamicHeaderEndpoints]]"
          as="headerEndpoint"
        >
          <gr-endpoint-decorator name$="[[headerEndpoint]]" role="columnheader">
          </gr-endpoint-decorator>
        </template>
      </template>
      <!-- Empty div here exists to keep spacing in sync with file rows. -->
      <div
        class="reviewed hideOnEdit"
        hidden$="[[!_loggedIn]]"
        aria-hidden="true"
      ></div>
      <div class="editFileControls showOnEdit" aria-hidden="true"></div>
      <div class="show-hide" aria-hidden="true"></div>
    </div>

    <template
      is="dom-repeat"
      items="[[_shownFiles]]"
      id="files"
      as="file"
      initial-count="[[fileListIncrement]]"
      target-framerate="1"
    >
      [[_reportRenderedRow(index)]]
      <div class="stickyArea">
        <div
          class$="file-row row [[_computePathClass(file.__path, _expandedFiles.*)]]"
          data-file$="[[_computeFileRange(file)]]"
          tabindex="-1"
          role="row"
        >
          <!-- endpoint: change-view-file-list-content-prepend -->
          <template is="dom-if" if="[[_showPrependedDynamicColumns]]">
            <template
              is="dom-repeat"
              items="[[_dynamicPrependedContentEndpoints]]"
              as="contentEndpoint"
            >
              <gr-endpoint-decorator name="[[contentEndpoint]]" role="gridcell">
                <gr-endpoint-param name="change" value="[[change]]">
                </gr-endpoint-param>
                <gr-endpoint-param name="changeNum" value="[[changeNum]]">
                </gr-endpoint-param>
                <gr-endpoint-param name="patchRange" value="[[patchRange]]">
                </gr-endpoint-param>
                <gr-endpoint-param name="path" value="[[file.__path]]">
                </gr-endpoint-param>
              </gr-endpoint-decorator>
            </template>
          </template>
          <!-- TODO: Remove data-url as it appears its not used -->
          <span
            data-url="[[_computeDiffURL(change, patchRange, file.__path, editMode)]]"
            class="path"
            role="gridcell"
          >
            <a
              class="pathLink"
              href$="[[_computeDiffURL(change, patchRange, file.__path, editMode)]]"
            >
              <span
                title$="[[computeDisplayPath(file.__path)]]"
                class="fullFileName"
              >
                [[computeDisplayPath(file.__path)]]
              </span>
              <span
                title$="[[computeDisplayPath(file.__path)]]"
                class="truncatedFileName"
              >
                [[computeTruncatedPath(file.__path)]]
              </span>
              <span
                class$="[[_computeStatusClass(file)]]"
                tabindex="0"
                title$="[[_computeFileStatusLabel(file.status)]]"
                aria-label$="[[_computeFileStatusLabel(file.status)]]"
              >
                [[_computeFileStatusLabel(file.status)]]
              </span>
              <gr-copy-clipboard
                hide-input=""
                text="[[file.__path]]"
              ></gr-copy-clipboard>
            </a>
            <template is="dom-if" if="[[file.old_path]]">
              <div class="oldPath" title$="[[file.old_path]]">
                [[file.old_path]]
                <gr-copy-clipboard
                  hide-input=""
                  text="[[file.old_path]]"
                ></gr-copy-clipboard>
              </div>
            </template>
          </span>
          <div role="gridcell">
            <div class="comments desktop">
              <span class="drafts"
                ><!-- This comments ensure that span is empty when the function
                returns empty string.
              -->[[_computeDraftsString(changeComments, patchRange,
                file.__path)]]<!-- This comments ensure that span is empty when
                the function returns empty string.
           --></span
              >
              <span
                ><!--
              -->[[_computeCommentsString(changeComments, patchRange,
                file.__path)]]<!--
           --></span
              >
              <span class="noCommentsScreenReaderText">
                <!-- Screen readers read the following content only if 2 other
              spans in the parent div is empty. The content is not visible on
              the page.
              Without this span, screen readers don't navigate correctly inside
              table, because empty div doesn't rendered. For example, VoiceOver
              jumps back to the whole table.
              We can use &nbsp instead, but it sounds worse.
              -->
                No comments
              </span>
            </div>
            <div class="comments mobile">
              <span class="drafts"
                ><!-- This comments ensure that span is empty when the function
                returns empty string.
              -->[[_computeDraftsStringMobile(changeComments, patchRange,
                file.__path)]]<!-- This comments ensure that span is empty when
                the function returns empty string.
           --></span
              >
              <span
                ><!--
             -->[[_computeCommentsStringMobile(changeComments, patchRange,
                file.__path)]]<!--
           --></span
              >
              <span class="noCommentsScreenReaderText">
                <!-- The same as for desktop comments -->
                No comments
              </span>
            </div>
          </div>
          <div role="gridcell">
            <!-- The content must be in a separate div. It guarantees, that
              gridcell always visible for screen readers.
              For example, without a nested div screen readers pronounce the
              "Commit message" row content with incorrect column headers.
            -->
            <div
              class$="[[_computeSizeBarsClass(_showSizeBars, file.__path)]]"
              aria-label="A bar that represents the addition and deletion ratio for the current file"
            >
              <svg width="61" height="8">
                <rect
                  x$="[[_computeBarAdditionX(file, _sizeBarLayout)]]"
                  y="0"
                  height="8"
                  fill="var(--positive-green-text-color)"
                  width$="[[_computeBarAdditionWidth(file, _sizeBarLayout)]]"
                ></rect>
                <rect
                  x$="[[_computeBarDeletionX(_sizeBarLayout)]]"
                  y="0"
                  height="8"
                  fill="var(--negative-red-text-color)"
                  width$="[[_computeBarDeletionWidth(file, _sizeBarLayout)]]"
                ></rect>
              </svg>
            </div>
          </div>
          <div class="stats" role="gridcell">
            <!-- The content must be in a separate div. It guarantees, that
            gridcell always visible for screen readers.
            For example, without a nested div screen readers pronounce the
            "Commit message" row content with incorrect column headers.
            -->
            <div class$="[[_computeClass('', file.__path)]]">
              <span
                class="added"
                tabindex="0"
                aria-label$="[[file.lines_inserted]] lines added"
                hidden$="[[file.binary]]"
              >
                +[[file.lines_inserted]]
              </span>
              <span
                class="removed"
                tabindex="0"
                aria-label$="[[file.lines_deleted]] lines removed"
                hidden$="[[file.binary]]"
              >
                -[[file.lines_deleted]]
              </span>
              <span
                class$="[[_computeBinaryClass(file.size_delta)]]"
                hidden$="[[!file.binary]]"
              >
                [[_formatBytes(file.size_delta)]] [[_formatPercentage(file.size,
                file.size_delta)]]
              </span>
            </div>
          </div>
          <!-- endpoint: change-view-file-list-content -->
          <template is="dom-if" if="[[_showDynamicColumns]]">
            <template
              is="dom-repeat"
              items="[[_dynamicContentEndpoints]]"
              as="contentEndpoint"
            >
              <div class$="[[_computeClass('', file.__path)]]" role="gridcell">
                <gr-endpoint-decorator name="[[contentEndpoint]]">
                  <gr-endpoint-param name="change" value="[[change]]">
                  </gr-endpoint-param>
                  <gr-endpoint-param name="changeNum" value="[[changeNum]]">
                  </gr-endpoint-param>
                  <gr-endpoint-param name="patchRange" value="[[patchRange]]">
                  </gr-endpoint-param>
                  <gr-endpoint-param name="path" value="[[file.__path]]">
                  </gr-endpoint-param>
                </gr-endpoint-decorator>
              </div>
            </template>
          </template>
          <div
            class="reviewed hideOnEdit"
            role="gridcell"
            hidden$="[[!_loggedIn]]"
          >
            <span
              class$="reviewedLabel [[_computeReviewedClass(file.isReviewed)]]"
              aria-hidden$="[[!file.isReviewed]]"
              >Reviewed</span
            >
            <!-- Do not use input type="checkbox" with hidden input and
                  visible label here. Screen readers don't read/interract
                  correctly with such input.
              -->
            <span
              class="reviewedSwitch"
              role="switch"
              tabindex="0"
              on-click="_reviewedClick"
              on-keydown="_reviewedClick"
              aria-label="Reviewed"
              aria-checked$="[[_booleanToString(file.isReviewed)]]"
            >
              <!-- Trick with tabindex to avoid outline on mouse focus, but
                preserve focus outline for keyboard navigation -->
              <span
                tabindex="-1"
                class="markReviewed"
                title$="[[_reviewedTitle(file.isReviewed)]]"
                >[[_computeReviewedText(file.isReviewed)]]</span
              >
            </span>
          </div>
          <div
            class="editFileControls showOnEdit"
            role="gridcell"
            aria-hidden$="[[!editMode]]"
          >
            <template is="dom-if" if="[[editMode]]">
              <gr-edit-file-controls
                class$="[[_computeClass('', file.__path)]]"
                file-path="[[file.__path]]"
              ></gr-edit-file-controls>
            </template>
          </div>
          <div class="show-hide" role="gridcell">
            <!-- Do not use input type="checkbox" with hidden input and
                visible label here. Screen readers don't read/interract
                correctly with such input.
            -->
            <span
              class="show-hide"
              data-path$="[[file.__path]]"
              data-expand="true"
              role="switch"
              tabindex="0"
              aria-checked$="[[_isFileExpandedStr(file.__path, _expandedFiles.*)]]"
              aria-label="Expand file"
              on-click="_expandedClick"
              on-keydown="_expandedClick"
            >
              <!-- Trick with tabindex to avoid outline on mouse focus, but
              preserve focus outline for keyboard navigation -->
              <iron-icon
                class="show-hide-icon"
                tabindex="-1"
                id="icon"
                icon="[[_computeShowHideIcon(file.__path, _expandedFiles.*)]]"
              >
              </iron-icon>
            </span>
          </div>
        </div>
        <template
          is="dom-if"
          if="[[_isFileExpanded(file.__path, _expandedFiles.*)]]"
        >
          <gr-diff-host
            no-auto-render=""
            show-load-failure=""
            display-line="[[_displayLine]]"
            hidden="[[!_isFileExpanded(file.__path, _expandedFiles.*)]]"
            change-num="[[changeNum]]"
            patch-range="[[patchRange]]"
            file="[[_computeFileRange(file)]]"
            path="[[file.__path]]"
            prefs="[[diffPrefs]]"
            project-name="[[change.project]]"
            no-render-on-prefs-change=""
            view-mode="[[diffViewMode]]"
          ></gr-diff-host>
        </template>
      </div>
    </template>
  </div>
  <div class="row totalChanges" hidden$="[[_hideChangeTotals]]">
    <div class="total-stats">
      <span
        class="added"
        tabindex="0"
        aria-label$="Total [[_patchChange.inserted]] lines added"
      >
        +[[_patchChange.inserted]]
      </span>
      <span
        class="removed"
        tabindex="0"
        aria-label$="Total [[_patchChange.deleted]] lines removed"
      >
        -[[_patchChange.deleted]]
      </span>
    </div>
    <!-- endpoint: change-view-file-list-summary -->
    <template is="dom-if" if="[[_showDynamicColumns]]">
      <template
        is="dom-repeat"
        items="[[_dynamicSummaryEndpoints]]"
        as="summaryEndpoint"
      >
        <gr-endpoint-decorator name="[[summaryEndpoint]]">
          <gr-endpoint-param
            name="change"
            value="[[change]]"
          ></gr-endpoint-param>
          <gr-endpoint-param name="patchRange" value="[[patchRange]]">
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      </template>
    </template>
    <!-- Empty div here exists to keep spacing in sync with file rows. -->
    <div class="reviewed hideOnEdit" hidden$="[[!_loggedIn]]"></div>
    <div class="editFileControls showOnEdit"></div>
    <div class="show-hide"></div>
  </div>
  <div class="row totalChanges" hidden$="[[_hideBinaryChangeTotals]]">
    <div class="total-stats">
      <span
        class="added"
        aria-label$="Total bytes inserted: [[_formatBytes(_patchChange.size_delta_inserted)]] "
      >
        [[_formatBytes(_patchChange.size_delta_inserted)]]
        [[_formatPercentage(_patchChange.total_size,
        _patchChange.size_delta_inserted)]]
      </span>
      <span
        class="removed"
        aria-label$="Total bytes removed: [[_formatBytes(_patchChange.size_delta_deleted)]]"
      >
        [[_formatBytes(_patchChange.size_delta_deleted)]]
        [[_formatPercentage(_patchChange.total_size,
        _patchChange.size_delta_deleted)]]
      </span>
    </div>
  </div>
  <div
    class$="row controlRow [[_computeFileListControlClass(numFilesShown, _files)]]"
  >
    <gr-button
      class="fileListButton"
      id="incrementButton"
      link=""
      on-click="_incrementNumFilesShown"
    >
      [[_computeIncrementText(numFilesShown, _files)]]
    </gr-button>
    <gr-tooltip-content
      has-tooltip="[[_computeWarnShowAll(_files)]]"
      show-icon="[[_computeWarnShowAll(_files)]]"
      title$="[[_computeShowAllWarning(_files)]]"
    >
      <gr-button
        class="fileListButton"
        id="showAllButton"
        link=""
        on-click="_showAllFiles"
      >
        [[_computeShowAllText(_files)]] </gr-button
      ><!--
  --></gr-tooltip-content>
  </div>
  <gr-diff-preferences-dialog
    id="diffPreferencesDialog"
    diff-prefs="{{diffPrefs}}"
    on-reload-diff-preference="_handleReloadingDiffPreference"
  >
  </gr-diff-preferences-dialog>
  <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
  <gr-storage id="storage"></gr-storage>
  <gr-diff-cursor id="diffCursor"></gr-diff-cursor>
  <gr-cursor-manager
    id="fileCursor"
    scroll-mode="keep-visible"
    focus-on-move=""
    cursor-target-class="selected"
  ></gr-cursor-manager>
`;
