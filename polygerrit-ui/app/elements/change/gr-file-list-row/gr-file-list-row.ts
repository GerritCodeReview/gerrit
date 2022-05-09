/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property} from 'lit/decorators';
import {classMap} from 'lit/directives/class-map';
import {ifDefined} from 'lit/directives/if-defined';
import {when} from 'lit/directives/when';
import {NumericChangeId, PatchRange} from '../../../types/common';
import {DiffPreferencesInfo} from '../../../types/diff';
import {ParsedChangeInfo, PatchSetFile} from '../../../types/types';
import {assertIsDefined} from '../../../utils/common-util';
import {NormalizedFileInfo} from '../gr-file-list/gr-file-list';

@customElement('gr-file-list-row')
export class GrFileListRow extends LitElement {
  @property({type: Object})
  change?: ParsedChangeInfo;

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Object})
  patchRange?: PatchRange;

  @property({type: Object})
  file?: NormalizedFileInfo;

  @property({type: Boolean})
  // TODO: Update when in _expandedFiles
  isExpanded = false;

  @property({type: Object})
  diffPrefs?: DiffPreferencesInfo;

  @property({type: Boolean})
  displayLine?: boolean;

  @property({type: Boolean})
  showPrependedDynamicColumns = false;

  static override styles = [css``];

  override render() {
    if (!this.file) return;
    return html`<div class="stickyArea">
      <div
        class=${classMap({
          'classfile-row': true,
          row: true,
          expanded: this.isExpanded,
        })}
        .data-file=${this.computePatchSetFile()}
        tabindex="-1"
        role="row"
      >
        <!-- endpoint: change-view-file-list-content-prepend -->
        ${when(this.showPrependedDynamicColumns, () => )}
        <!-- TODO: Remove data-url as it appears its not used -->
        <span
          data-url="[[_computeDiffURL(change, patchRange.basePatchNum, patchRange.patchNum, file.__path, editMode)]]"
          class="path"
          role="gridcell"
        >
          <a
            class="pathLink"
            href$="[[_computeDiffURL(change, patchRange.basePatchNum, patchRange.patchNum, file.__path, editMode)]]"
          >
            <span
              title$="[[_computeDisplayPath(file.__path)]]"
              class="fullFileName"
            >
              [[_computeDisplayPath(file.__path)]]
            </span>
            <span
              title$="[[_computeDisplayPath(file.__path)]]"
              class="truncatedFileName"
            >
              [[_computeTruncatedPath(file.__path)]]
            </span>
            <gr-file-status-chip .file=${this.file}></gr-file-status-chip>
            <gr-copy-clipboard
              hideInput=""
              text="[[file.__path]]"
            ></gr-copy-clipboard>
          </a>
          ${when(
            this.file.old_path,
            () => html`
              <div class="oldPath" title=${ifDefined(this.file!.old_path)}>
                ${this.file!.old_path}
                <gr-copy-clipboard
                  hideInput
                  .text=${this.file!.old_path}
                ></gr-copy-clipboard>
              </div>
            `
          )}
        </span>
        <div role="gridcell">
          <div class="comments desktop">
            <span class="drafts"
              ><!-- This comments ensure that span is empty when the function
            returns empty string.
          -->[[_computeDraftsString(changeComments, patchRange, file)]]<!-- This comments ensure that span is empty when
            the function returns empty string.
       --></span
            >
            <span
              ><!--
          -->[[_computeCommentsString(changeComments, patchRange, file)]]<!--
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
          -->[[_computeDraftsStringMobile(changeComments, patchRange, file)]]<!-- This comments ensure that span is empty when
            the function returns empty string.
       --></span
            >
            <span
              ><!--
         -->[[_computeCommentsStringMobile(changeComments, patchRange, file)]]<!--
       --></span
            >
            <span class="noCommentsScreenReaderText">
              <!-- The same as for desktop comments -->
              No comments
            </span>
          </div>
        </div>
        <div class="desktop" role="gridcell">
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
              <gr-endpoint-decorator
                class="extra-col"
                name="[[contentEndpoint]]"
              >
                <gr-endpoint-param name="change" value=${this.change}>
                </gr-endpoint-param>
                <gr-endpoint-param name="changeNum" value=${this.changeNum}>
                </gr-endpoint-param>
                <gr-endpoint-param name="patchRange" value=${this.patchRange}>
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

      ${when(
        this.isExpanded,
        () => html`
          <gr-diff-host
            no-auto-render
            show-load-failure
            ?displayLine=${this.displayLine}
            ?hidden=${!this.isExpanded}
            .changeNum=${this.changeNum}
            .change=${this.change}
            .patchRange=${this.patchRange}
            .file=${this.computePatchSetFile()}
            .path=${this.file!.__path}
            .prefs=${this.diffPrefs}
            .projectName=${this.change?.project}
            no-render-on-prefs-change
          ></gr-diff-host>
        `
      )}
    </div>`;
  }

  private renderEndpoint() {
    if (!this.showPrependedDynamicColumns) return;
    return html`
    <template
      is="dom-repeat"
      items="[[_dynamicPrependedContentEndpoints]]"
      as="contentEndpoint"
    >
      <gr-endpoint-decorator
        class="prepended-col"
        name="[[contentEndpoint]]"
        role="gridcell"
      >
        <gr-endpoint-param name="change" value=${this.change}>
        </gr-endpoint-param>
        <gr-endpoint-param name="changeNum" value=${this.changeNum}>
        </gr-endpoint-param>
        <gr-endpoint-param name="patchRange" value=${this.patchRange}>
        </gr-endpoint-param>
        <gr-endpoint-param name="path" value=${this.file.__path}>
        </gr-endpoint-param>
        <gr-endpoint-param
          name="oldPath"
          value=${this.file.old_path ?? null}
        >
        </gr-endpoint-param>
      </gr-endpoint-decorator>
    </template>
  `;
  }

  /**
   * Generates file range from file info object.
   */
  private computePatchSetFile(): PatchSetFile {
    assertIsDefined(this.file, 'file');
    const fileData: PatchSetFile = {
      path: this.file.__path,
    };
    if (this.file.old_path) {
      fileData.basePath = this.file.old_path;
    }
    return fileData;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-file-list-row': GrFileListRow;
  }
}
