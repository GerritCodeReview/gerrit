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
  <style include="gr-a11y-styles">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
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
      vertical-align: top;
    }
    div[role='gridcell']
      > div.comments
      > span:empty
      + span:empty
      + span.noCommentsScreenReaderText {
      /* inline-block instead of block, such that it can control width */
      display: inline-block;
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
      white-space: nowrap;
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
      color: var(--error-foreground);
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
    .showParentButton {
      line-height: var(--line-height-normal);
      margin-bottom: calc(var(--spacing-s) * -1);
      margin-left: var(--spacing-m);
      margin-top: calc(var(--spacing-s) * -1);
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
      --gr-button-padding: 0px;
    }
    .row:focus-within gr-copy-clipboard,
    .row:hover gr-copy-clipboard {
      visibility: visible;
    }

    @media screen and (max-width: 1200px) {
      gr-endpoint-decorator.extra-col {
        display: none;
      }
    }

    @media screen and (max-width: 1000px) {
      .reviewed {
        display: none;
      }
    }

    @media screen and (max-width: 800px) {
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
`;
