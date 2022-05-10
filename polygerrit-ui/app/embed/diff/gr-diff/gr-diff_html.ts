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
    /**
     * This is used to hide all left side of the diff (e.g. diffs besides comments
     * in the change log). Since we want to remove the first 4 cells consistently
     * in all rows except context buttons (.dividerRow).
     */
    :host(.no-left) .sideBySide colgroup col:nth-child(-n + 4),
    :host(.no-left) .sideBySide tr:not(.dividerRow) td:nth-child(-n + 4) {
      display: none;
    }
    :host(.disable-context-control-buttons) {
      --context-control-display: none;
    }
    :host(.disable-context-control-buttons) .section {
      border-right: none;
    }
    :host(.hide-line-length-indicator) .full-width td.content .contentText {
      background-image: none;
    }

    :host {
      font-family: var(--monospace-font-family, ''), 'Roboto Mono';
      font-size: var(--font-size, var(--font-size-code, 12px));
      /* usually 16px = 12px + 4px */
      line-height: calc(
        var(--font-size, var(--font-size-code, 12px)) + var(--spacing-s, 4px)
      );
    }

    .thread-group {
      display: block;
      max-width: var(--content-width, 80ch);
      white-space: normal;
      background-color: var(--diff-blank-background-color);
    }
    .diffContainer {
      max-width: var(--diff-max-width, none);
      display: flex;
      font-family: var(--monospace-font-family);
    }
    .diffContainer.hiddenscroll {
      margin-bottom: var(--spacing-m);
    }
    table {
      border-collapse: collapse;
      table-layout: fixed;
    }
    td.lineNum {
      /* Enforces background whenever lines wrap */
      background-color: var(--diff-blank-background-color);
    }

    /* Provides the option to add side borders (left and right) to the line number column. */
    td.lineNum,
    td.blankLineNum,
    td.moveControlsLineNumCol,
    td.contextLineNum {
      box-shadow: var(--line-number-box-shadow, unset);
    }

    /*
      Context controls break up the table visually, so we set the right border
      on individual sections to leave a gap for the divider.

      Also taken into account for max-width calculations in SHRINK_ONLY
      mode (check GrDiff._updatePreferenceStyles).
      */
    .section {
      border-right: 1px solid var(--border-color);
    }
    .section.contextControl {
      /*
       * Divider inside this section must not have border; we set borders on
       * the padding rows below.
       */
      border-right-width: 0;
    }
    /*
     * Padding rows behind context controls. The diff is styled to be cut into
     * two halves by the negative space of the divider on which the context
     * control buttons are anchored.
     */
    .contextBackground {
      border-right: 1px solid var(--border-color);
    }
    .contextBackground.above {
      border-bottom: 1px solid var(--border-color);
    }
    .contextBackground.below {
      border-top: 1px solid var(--border-color);
    }

    .lineNumButton {
      display: block;
      width: 100%;
      height: 100%;
      background-color: var(--diff-blank-background-color);
      box-shadow: var(--line-number-box-shadow, unset);
    }
    td.lineNum {
      vertical-align: top;
    }

    /*
      The only way to focus this (clicking) will apply our own focus styling,
      so this default styling is not needed and distracting.
      */
    .lineNumButton:focus {
      outline: none;
    }
    gr-image-viewer {
      width: 100%;
      height: 100%;
      max-width: var(--image-viewer-max-width, 95vw);
      max-height: var(--image-viewer-max-height, 90vh);
      /*
        Defined by paper-styles default-theme and used in various components.
        background-color-secondary is a compromise between fairly light in
        light theme (where we ideally would want background-color-primary) yet
        slightly offset against the app background in dark mode, where drop
        shadows e.g. around paper-card are almost invisible.
        */
      --primary-background-color: var(--background-color-secondary);
    }
    .image-diff .gr-diff {
      text-align: center;
    }
    .image-diff img {
      box-shadow: var(--elevation-level-1);
      max-width: 50em;
    }
    .image-diff .right.lineNumButton {
      border-left: 1px solid var(--border-color);
    }
    .image-diff label,
    .binary-diff label {
      font-family: var(--font-family);
      font-style: italic;
    }
    .diff-row {
      outline: none;
      user-select: none;
    }
    .diff-row.target-row.target-side-left .lineNumButton.left,
    .diff-row.target-row.target-side-right .lineNumButton.right,
    .diff-row.target-row.unified .lineNumButton {
      color: var(--primary-text-color);
    }

    /**
     * Preparing selected line cells with position relative so it allows a positioned overlay with 'position: absolute'.
     */
    .target-row td {
      position: relative;
    }

    /**
     * Defines an overlay to the selected line for drawing an outline without blocking user interaction (e.g. text selection).
     */
    .target-row td::before {
      border-width: 0;
      border-style: solid;
      border-color: var(--focused-line-outline-color);
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      user-select: none;
      content: ' ';
    }

    /**
     * the outline for the selected content cell should be the same in all cases.
     */
    .target-row.target-side-left td.left.content::before,
    .target-row.target-side-right td.right.content::before,
    .unified.target-row td.content::before {
      border-width: 1px 1px 1px 0;
    }

    /**
     * the outline for the sign cell should be always be contiguous top/bottom.
     */
    .target-row.target-side-left td.left.sign::before,
    .target-row.target-side-right td.right.sign::before {
      border-width: 1px 0;
    }

    /**
     * For side-by-side we need to select the correct line number to "visually close"
     * the outline.
     */
    .side-by-side.target-row.target-side-left td.left.lineNum::before,
    .side-by-side.target-row.target-side-right td.right.lineNum::before {
      border-width: 1px 0 1px 1px;
    }

    /**
     * For unified diff we always start the overlay from the left cell
     */
    .unified.target-row td.left:not(.content)::before {
      border-width: 1px 0 1px 1px;
    }

    /**
     * For unified diff we should continue the top/bottom border in right
     * line number column.
     */
    .unified.target-row td.right:not(.content)::before {
      border-width: 1px 0;
    }

    .content {
      background-color: var(--diff-blank-background-color);
    }

    /*
      Describes two states of semantic tokens: whenever a token has a
      definition that can be navigated to (navigable) and whenever
      the token is actually clickable to perform this navigation.
    */
    .semantic-token.navigable {
      text-decoration-style: dotted;
      text-decoration-line: underline;
    }
    .semantic-token.navigable.clickable {
      text-decoration-style: solid;
      cursor: pointer;
    }

    /*
      The file line, which has no contentText, add some margin before the first
      comment. We cannot add padding the container because we only want it if
      there is at least one comment thread, and the slotting makes :empty not
      work as expected.
     */
    .content.file slot:first-child::slotted(.comment-thread) {
      display: block;
      margin-top: var(--spacing-xs);
    }
    .contentText {
      background-color: var(--view-background-color);
    }
    .blank {
      background-color: var(--diff-blank-background-color);
    }
    .image-diff .content {
      background-color: var(--diff-blank-background-color);
    }
    .responsive {
      width: 100%;
    }
    .responsive .contentText {
      white-space: break-spaces;
      word-break: break-all;
    }
    .lineNumButton,
    .content {
      vertical-align: top;
      white-space: pre;
    }
    .contextLineNum,
    .lineNumButton {
      -webkit-user-select: none;
      -moz-user-select: none;
      -ms-user-select: none;
      user-select: none;

      color: var(--deemphasized-text-color);
      padding: 0 var(--spacing-m);
      text-align: right;
    }
    .canComment .lineNumButton {
      cursor: pointer;
    }
    .sign {
      min-width: 1ch;
      width: 1ch;
      background-color: var(--view-background-color);
    }
    .sign.blank {
      background-color: var(--diff-blank-background-color);
    }
    .content {
      /* Set min width since setting width on table cells still
           allows them to shrink. Do not set max width because
           CJK (Chinese-Japanese-Korean) glyphs have variable width */
      min-width: var(--content-width, 80ch);
      width: var(--content-width, 80ch);
    }
    .content.add .contentText .intraline,
      /* If there are no intraline info, consider everything changed */
      .content.add.no-intraline-info .contentText,
      .sign.add.no-intraline-info,
      .delta.total .content.add .contentText {
      background-color: var(--dark-add-highlight-color);
    }
    .content.add .contentText,
    .sign.add {
      background-color: var(--light-add-highlight-color);
    }
    .content.remove .contentText .intraline,
      /* If there are no intraline info, consider everything changed */
      .content.remove.no-intraline-info .contentText,
      .delta.total .content.remove .contentText,
      .sign.remove.no-intraline-info {
      background-color: var(--dark-remove-highlight-color);
    }
    .content.remove .contentText,
    .sign.remove {
      background-color: var(--light-remove-highlight-color);
    }

    .ignoredWhitespaceOnly .sign.no-intraline-info {
      background-color: var(--view-background-color);
    }

    /* dueToRebase */
    .dueToRebase .content.add .contentText .intraline,
    .delta.total.dueToRebase .content.add .contentText {
      background-color: var(--dark-rebased-add-highlight-color);
    }
    .dueToRebase .content.add .contentText {
      background-color: var(--light-rebased-add-highlight-color);
    }
    .dueToRebase .content.remove .contentText .intraline,
    .delta.total.dueToRebase .content.remove .contentText {
      background-color: var(--dark-rebased-remove-highlight-color);
    }
    .dueToRebase .content.remove .contentText {
      background-color: var(--light-remove-add-highlight-color);
    }

    /* dueToMove */
    .dueToMove .sign.add,
    .dueToMove .content.add .contentText,
    .dueToMove .moveControls.movedIn .sign.right,
    .dueToMove .moveControls.movedIn .moveHeader,
    .delta.total.dueToMove .content.add .contentText {
      background-color: var(--diff-moved-in-background);
    }

    .dueToMove .sign.remove,
    .dueToMove .content.remove .contentText,
    .dueToMove .moveControls.movedOut .moveHeader,
    .dueToMove .moveControls.movedOut .sign.left,
    .delta.total.dueToMove .content.remove .contentText {
      background-color: var(--diff-moved-out-background);
    }

    .delta.dueToMove .movedIn .moveHeader {
      --gr-range-header-color: var(--diff-moved-in-label-color);
    }
    .delta.dueToMove .movedOut .moveHeader {
      --gr-range-header-color: var(--diff-moved-out-label-color);
    }

    .moveHeader a {
      color: inherit;
    }

    /* ignoredWhitespaceOnly */
    .ignoredWhitespaceOnly .content.add .contentText .intraline,
    .delta.total.ignoredWhitespaceOnly .content.add .contentText,
    .ignoredWhitespaceOnly .content.add .contentText,
    .ignoredWhitespaceOnly .content.remove .contentText .intraline,
    .delta.total.ignoredWhitespaceOnly .content.remove .contentText,
    .ignoredWhitespaceOnly .content.remove .contentText {
      background-color: var(--view-background-color);
    }

    .content .contentText:empty:after {
      /* Newline, to ensure empty lines are one line-height tall. */
      content: '\\A';
    }

    /* Context controls */
    .contextControl {
      display: var(--context-control-display, table-row-group);
      background-color: transparent;
      border: none;
      --divider-height: var(--spacing-s);
      --divider-border: 1px;
    }
    .contextControl gr-button iron-icon {
      /* should match line-height of gr-button */
      width: var(--line-height-mono, 18px);
      height: var(--line-height-mono, 18px);
    }
    .contextControl td:not(.lineNumButton) {
      text-align: center;
    }

    /*
     * Padding rows behind context controls. Styled as a continuation of the
     * line gutters and code area.
     */
    .contextBackground > .contextLineNum {
      background-color: var(--diff-blank-background-color);
    }
    .contextBackground > td:not(.contextLineNum) {
      background-color: var(--view-background-color);
    }
    .contextBackground {
      /*
       * One line of background behind the context expanders which they can
       * render on top of, plus some padding.
       */
      height: calc(var(--line-height-normal) + var(--spacing-s));
    }

    .dividerCell {
      vertical-align: top;
    }
    .dividerRow.show-both .dividerCell {
      height: var(--divider-height);
    }
    .dividerRow.show-above .dividerCell,
    .dividerRow.show-above .dividerCell {
      height: 0;
    }

    .br:after {
      /* Line feed */
      content: '\\A';
    }
    .tab {
      display: inline-block;
    }
    .tab-indicator:before {
      color: var(--diff-tab-indicator-color);
      /* >> character */
      content: '\\00BB';
      position: absolute;
    }
    .special-char-indicator {
      /* spacing so elements don't collide */
      padding-right: var(--spacing-m);
    }
    .special-char-indicator:before {
      color: var(--diff-tab-indicator-color);
      content: '•';
      position: absolute;
    }
    .special-char-warning {
      /* spacing so elements don't collide */
      padding-right: var(--spacing-m);
    }
    .special-char-warning:before {
      color: var(--warning-foreground);
      content: '!';
      position: absolute;
    }
    /* Is defined after other background-colors, such that this
         rule wins in case of same specificity. */
    .trailing-whitespace,
    .content .trailing-whitespace,
    .trailing-whitespace .intraline,
    .content .trailing-whitespace .intraline {
      border-radius: var(--border-radius, 4px);
      background-color: var(--diff-trailing-whitespace-indicator);
    }
    #diffHeader {
      background-color: var(--table-header-background-color);
      border-bottom: 1px solid var(--border-color);
      color: var(--link-color);
      padding: var(--spacing-m) 0 var(--spacing-m) 48px;
    }
    #diffTable {
      /* for gr-selection-action-box positioning */
      position: relative;
    }
    #diffTable:focus {
      outline: none;
    }
    #loadingError,
    #sizeWarning {
      display: none;
      margin: var(--spacing-l) auto;
      max-width: 60em;
      text-align: center;
    }
    #loadingError {
      color: var(--error-text-color);
    }
    #sizeWarning gr-button {
      margin: var(--spacing-l);
    }
    #loadingError.showError,
    #sizeWarning.warn {
      display: block;
    }
    .target-row td.blame {
      background: var(--diff-selection-background-color);
    }
    td.lost div {
      background-color: var(--info-background);
      padding: var(--spacing-s) 0 0 0;
    }
    td.lost div:first-of-type {
      font-family: var(--font-family, 'Roboto');
      font-size: var(--font-size-normal, 14px);
      line-height: var(--line-height-normal);
    }
    td.lost iron-icon {
      padding: 0 var(--spacing-s) 0 var(--spacing-m);
      color: var(--blue-700);
    }

    col.sign,
    td.sign {
      display: none;
    }

    /**
     * Sign column should only be shown in high-contrast mode.
     */
    :host(.with-sign-col) col.sign {
      display: table-column;
    }
    :host(.with-sign-col) td.sign {
      display: table-cell;
    }
    col.blame {
      display: none;
    }
    td.blame {
      display: none;
      padding: 0 var(--spacing-m);
      white-space: pre;
    }
    :host(.showBlame) col.blame {
      display: table-column;
    }
    :host(.showBlame) td.blame {
      display: table-cell;
    }
    td.blame > span {
      opacity: 0.6;
    }
    td.blame > span.startOfRange {
      opacity: 1;
    }
    td.blame .blameDate {
      font-family: var(--monospace-font-family);
      color: var(--link-color);
      text-decoration: none;
    }
    .responsive td.blame {
      overflow: hidden;
      width: 200px;
    }
    /** Support the line length indicator **/
    .responsive td.content .contentText {
      /*
      Same strategy as in https://stackoverflow.com/questions/1179928/how-can-i-put-a-vertical-line-down-the-center-of-a-div
      */
      background-image: linear-gradient(
        var(--line-length-indicator-color),
        var(--line-length-indicator-color)
      );
      background-size: 1px 100%;
      background-position: var(--line-limit-marker) 0;
      background-repeat: no-repeat;
    }
    .newlineWarning {
      color: var(--deemphasized-text-color);
      text-align: center;
    }
    .newlineWarning.hidden {
      display: none;
    }
    .lineNum.COVERED .lineNumButton {
      background-color: var(--coverage-covered, #e0f2f1);
    }
    .lineNum.NOT_COVERED .lineNumButton {
      background-color: var(--coverage-not-covered, #ffd1a4);
    }
    .lineNum.PARTIALLY_COVERED .lineNumButton {
      background: linear-gradient(
        to right bottom,
        var(--coverage-not-covered, #ffd1a4) 0%,
        var(--coverage-not-covered, #ffd1a4) 50%,
        var(--coverage-covered, #e0f2f1) 50%,
        var(--coverage-covered, #e0f2f1) 100%
      );
    }

    /** BEGIN: Select and copy for Polymer 2 */
    /** Below was copied and modified from the original css in gr-diff-selection.html */
    .content,
    .contextControl,
    .blame {
      -webkit-user-select: none;
      -moz-user-select: none;
      -ms-user-select: none;
      user-select: none;
    }

    .selected-left:not(.selected-comment)
      .side-by-side
      .left
      + .content
      .contentText,
    .selected-right:not(.selected-comment)
      .side-by-side
      .right
      + .content
      .contentText,
    .selected-left:not(.selected-comment)
      .unified
      .left.lineNum
      ~ .content:not(.both)
      .contentText,
    .selected-right:not(.selected-comment)
      .unified
      .right.lineNum
      ~ .content
      .contentText,
    .selected-left.selected-comment .side-by-side .left + .content .message,
    .selected-right.selected-comment
      .side-by-side
      .right
      + .content
      .message
      :not(.collapsedContent),
    .selected-comment .unified .message :not(.collapsedContent),
    .selected-blame .blame {
      -webkit-user-select: text;
      -moz-user-select: text;
      -ms-user-select: text;
      user-select: text;
    }

    /** Make comments and check results selectable when selected */
    .selected-left.selected-comment
      ::slotted(.comment-thread[diff-side='left']),
    .selected-right.selected-comment
      ::slotted(.comment-thread[diff-side='right']) {
      -webkit-user-select: text;
      -moz-user-select: text;
      -ms-user-select: text;
      user-select: text;
    }
    /** END: Select and copy for Polymer 2 */

    .whitespace-change-only-message {
      background-color: var(--diff-context-control-background-color);
      border: 1px solid var(--diff-context-control-border-color);
      text-align: center;
    }

    .token-highlight {
      background-color: var(--token-highlighting-color, #fffd54);
    }

    gr-selection-action-box {
      /**
       * Needs z-index to appear above wrapped content, since it's inserted
       * into DOM before it.
       */
      z-index: 10;
    }
  </style>
  <style include="gr-syntax-theme">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <style include="gr-ranged-comment-theme">
    /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
  </style>
  <div id="diffHeader" hidden$="[[_computeDiffHeaderHidden(_diffHeaderItems)]]">
    <template is="dom-repeat" items="[[_diffHeaderItems]]">
      <div>[[item]]</div>
    </template>
  </div>
  <div
    class$="[[_computeContainerClass(loggedIn, viewMode, displayLine)]]"
    on-click="_handleTap"
  >
    <table
      id="diffTable"
      class$="[[_diffTableClass]]"
      role="presentation"
      contenteditable$="[[isContentEditable]]"
    ></table>

    <template
      is="dom-if"
      if="[[showNoChangeMessage(_loading, prefs, _diffLength, diff)]]"
    >
      <div class="whitespace-change-only-message">
        This file only contains whitespace changes. Modify the whitespace
        setting to see the changes.
      </div>
    </template>
  </div>
  <div class$="[[_computeNewlineWarningClass(_newlineWarning, _loading)]]">
    [[_newlineWarning]]
  </div>
  <div id="loadingError" class$="[[_computeErrorClass(errorMessage)]]">
    [[errorMessage]]
  </div>
  <div id="sizeWarning" class$="[[_computeWarningClass(_showWarning)]]">
    <p>
      Prevented render because "Whole file" is enabled and this diff is very
      large (about [[_diffLength]] lines).
    </p>
    <gr-button on-click="_collapseContext">
      Render with limited context
    </gr-button>
    <gr-button on-click="_handleFullBypass">
      Render anyway (may be slow)
    </gr-button>
  </div>
`;
