/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

// Styles related to the top-level <gr-diff> component.
export const grDiffStyles = css`
  :host {
    font-family: var(--monospace-font-family, ''), 'Roboto Mono';
    font-size: var(--font-size, var(--font-size-code, 12px));
    /* usually 16px = 12px + 4px */
    line-height: calc(
      var(--font-size, var(--font-size-code, 12px)) + var(--spacing-s, 4px)
    );
  }

  gr-diff-image-new,
  gr-diff-image-old,
  gr-diff-section,
  gr-context-controls-section,
  gr-diff-row {
    display: contents;
  }
`;

// Styles related to the <gr-diff-element> component.
export const grDiffElementStyles = css`
  gr-diff-element div.diffContainer {
    max-width: var(--diff-max-width, none);
    font-family: var(--monospace-font-family);
  }
  gr-diff-element table {
    border-collapse: collapse;
    table-layout: fixed;
  }
  gr-diff-element table.responsive {
    width: 100%;
  }
  gr-diff-element div#diffHeader {
    background-color: var(--table-header-background-color);
    border-bottom: 1px solid var(--border-color);
    color: var(--link-color);
    padding: var(--spacing-m) 0 var(--spacing-m) 48px;
  }
  gr-diff-element table#diffTable:focus {
    outline: none;
  }
  gr-diff-element div#loadingError,
  gr-diff-element div#sizeWarning {
    display: block;
    margin: var(--spacing-l) auto;
    max-width: 60em;
    text-align: center;
  }
  gr-diff-element div#loadingError {
    color: var(--error-text-color);
  }
  gr-diff-element div#sizeWarning gr-button {
    margin: var(--spacing-l);
  }
  gr-diff-element div.newlineWarning {
    color: var(--deemphasized-text-color);
    text-align: center;
  }
  gr-diff-element div.newlineWarning.hidden {
    display: none;
  }
  gr-diff-element div.whitespace-change-only-message {
    background-color: var(--diff-context-control-background-color);
    border: 1px solid var(--diff-context-control-border-color);
    text-align: center;
  }
  gr-diff-element {
    /* for gr-selection-action-box positioning */
    position: relative;
  }
  gr-diff-element gr-selection-action-box {
    /* Needs z-index to appear above wrapped content, since it's inserted
       into DOM before it. */
    z-index: 120;
  }
`;

// Styles related to the <gr-diff-section> component.
export const grDiffSectionStyles = css`
  :host(.disable-context-control-buttons) gr-diff-section tbody.section {
    border-right: none;
  }
  /* Context controls break up the table visually, so we set the right
     border on individual sections to leave a gap for the divider.

     Also taken into account for max-width calculations in SHRINK_ONLY mode
     (check GrDiff.updatePreferenceStyles). */
  gr-diff-section tbody.section {
    border-right: 1px solid var(--border-color);
  }
  gr-diff-section tbody.section.contextControl {
    /* Divider inside this section must not have border; we set borders on
       the padding rows below. */
    border-right-width: 0;
  }
  gr-diff-section tr.moveControls td.moveHeader a {
    color: inherit;
  }
  gr-diff-section tbody.contextControl {
    display: table-row-group;
    background-color: transparent;
    border: none;
    --divider-height: var(--spacing-s);
    --divider-border: 1px;
  }
  /* TODO: Is this still used? */
  gr-diff-section tbody.contextControl gr-button gr-icon {
    /* should match line-height of gr-button */
    font-size: var(--line-height-mono, 18px);
  }
  gr-diff-section tbody.contextControl td:not(.lineNumButton) {
    text-align: center;
  }
  /* Option to add side borders (left and right) to the line number column. */
  gr-diff-section tr.moveControls td.moveControlsLineNumCol {
    box-shadow: var(--line-number-box-shadow, unset);
  }
`;

// Styles related to the <gr-diff-row> component.
export const grDiffContextControlsSectionStyles = css`
  /* Hide the actual context control buttons */
  :host(.disable-context-control-buttons)
    gr-diff-section
    tbody.contextControl
    gr-context-controls-section
    gr-context-controls {
    display: none;
  }
  /* Maintain a small amount of padding at the edges of diff chunks */
  :host(.disable-context-control-buttons)
    gr-diff-section
    tbody.contextControl
    gr-context-controls-section
    tr.contextBackground {
    height: var(--spacing-s);
    border-right: none;
  }
  gr-context-controls-section tr.contextBackground {
    /* One line of background behind the context expanders which they can
       render on top of, plus some padding. */
    height: calc(var(--line-height-normal) + var(--spacing-s));
  }
  /* Padding rows behind context controls. The diff is styled to be cut
     into two halves by the negative space of the divider on which the
     context control buttons are anchored. */
  gr-context-controls-section tr.contextBackground {
    border-right: 1px solid var(--border-color);
  }
  gr-context-controls-section tr.contextBackground.above {
    border-bottom: 1px solid var(--border-color);
  }
  gr-context-controls-section tr.contextBackground.below {
    border-top: 1px solid var(--border-color);
  }
  gr-context-controls-section tr.contextBackground td.contextLineNum {
    color: var(--deemphasized-text-color);
    padding: 0 var(--spacing-m);
    text-align: right;
  }
  /* Option to add side borders (left and right) to the line number column. */
  gr-context-controls-section tr.contextBackground td.contextLineNum {
    box-shadow: var(--line-number-box-shadow, unset);
  }
  /* Padding rows behind context controls. Styled as a continuation of the
     line gutters and code area. */
  gr-context-controls-section tr.contextBackground td.contextLineNum {
    background-color: var(--diff-blank-background-color);
  }
  gr-context-controls-section tr.contextBackground > td:not(.contextLineNum) {
    background-color: var(--view-background-color);
  }
  gr-context-controls-section td.dividerCell {
    vertical-align: top;
  }
  gr-context-controls-section tr.dividerRow.show-both td.dividerCell {
    height: var(--divider-height);
  }
  gr-context-controls-section tr.dividerRow.show-above td.dividerCell {
    height: 0;
  }
`;

// Styles related to the <gr-diff-row> component.
export const grDiffRowStyles = css`
  gr-diff-row td.content div.thread-group {
    display: block;
    max-width: var(--content-width, 80ch);
    white-space: normal;
    background-color: var(--diff-blank-background-color);
  }
  gr-diff-row tr.target-row td.blame {
    background: var(--diff-selection-background-color);
  }
  gr-diff-row td.content.lost div {
    background-color: var(--info-background);
  }
  gr-diff-row td.content.lost div.lost-message {
    font-family: var(--font-family, 'Roboto');
    font-size: var(--font-size-normal, 14px);
    line-height: var(--line-height-normal);
    padding: var(--spacing-s) 0;
  }
  gr-diff-row td.content.lost div.lost-message gr-icon {
    padding: 0 var(--spacing-s) 0 var(--spacing-m);
    color: var(--blue-700);
  }
  gr-diff-row td.blame {
    padding: 0 var(--spacing-m);
    white-space: pre;
  }
  gr-diff-row td.blame > span {
    opacity: 0.6;
  }
  gr-diff-row td.blame > span.startOfRange {
    opacity: 1;
  }
  gr-diff-row td.blame .blameDate {
    font-family: var(--monospace-font-family);
    color: var(--link-color);
    text-decoration: none;
  }
  gr-diff-row td.content div.contentText gr-diff-text:empty:after,
  gr-diff-row td.content div.contentText:empty:after {
    /* Newline, to ensure empty lines are one line-height tall. */
    content: '\\A';
  }
  /* Option to add side borders (left and right) to the line number column. */
  gr-diff-row td.lineNum,
  gr-diff-row td.blankLineNum {
    box-shadow: var(--line-number-box-shadow, unset);
  }
  gr-diff-row td.lineNum,
  gr-diff-row td.blankLineNum {
    /* Enforces background whenever lines wrap */
    background-color: var(--diff-blank-background-color);
  }
  gr-diff-row td.lineNum button.lineNumButton {
    display: block;
    width: 100%;
    height: 100%;
    background-color: var(--diff-blank-background-color);
    box-shadow: var(--line-number-box-shadow, unset);
  }
  gr-diff-row td.lineNum {
    vertical-align: top;
  }
  /* The only way to focus this (clicking) will apply our own focus
     styling, so this default styling is not needed and distracting. */
  gr-diff-row td.lineNum button.lineNumButton:focus {
    outline: none;
  }
  gr-diff-row tr.diff-row {
    outline: none;
  }
  gr-diff-row
    tr.diff-row.target-row.target-side-left
    td.lineNum
    button.lineNumButton.left,
  gr-diff-row
    tr.diff-row.target-row.target-side-right
    td.lineNum
    button.lineNumButton.right,
  gr-diff-row tr.diff-row.target-row.unified td.lineNum button.lineNumButton {
    color: var(--primary-text-color);
  }
  /* Preparing selected line cells with position relative so it allows a
     positioned overlay with 'position: absolute'. */
  gr-diff-row tr.target-row td {
    position: relative;
  }
  /* Defines an overlay to the selected line for drawing an outline without
     blocking user interaction (e.g. text selection). */
  gr-diff-row tr.target-row td::before {
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
  /* The outline for the selected content cell should be the same in all
     cases. */
  gr-diff-row tr.target-row.target-side-left td.left.content::before,
  gr-diff-row tr.target-row.target-side-right td.right.content::before,
  gr-diff-row tr.unified.target-row td.content::before {
    border-width: 1px 1px 1px 0;
  }
  /* The outline for the sign cell should be always be contiguous
     top/bottom. */
  gr-diff-row tr.target-row.target-side-left td.left.sign::before,
  gr-diff-row tr.target-row.target-side-right td.right.sign::before {
    border-width: 1px 0;
  }
  /* For side-by-side we need to select the correct line number to
     "visually close" the outline. */
  gr-diff-row
    tr.side-by-side.target-row.target-side-left
    td.left.lineNum::before,
  gr-diff-row
    tr.side-by-side.target-row.target-side-right
    td.right.lineNum::before {
    border-width: 1px 0 1px 1px;
  }
  /* For unified diff we always start the overlay from the left cell. */
  gr-diff-row tr.unified.target-row td.left:not(.content)::before {
    border-width: 1px 0 1px 1px;
  }
  /* For unified diff we should continue the top/bottom border in right
     line number column. */
  gr-diff-row tr.unified.target-row td.right:not(.content)::before {
    border-width: 1px 0;
  }
  gr-diff-row td.content {
    background-color: var(--diff-blank-background-color);
  }
  /* The file line, which has no contentText, add some margin before the
     first comment. We cannot add padding the container because we only
     want it if there is at least one comment thread, and the slotting
     makes :empty not work as expected. */
  gr-diff-row td.content.file slot:first-child::slotted(.comment-thread) {
    display: block;
    margin-top: var(--spacing-xs);
  }
  gr-diff-row td.content div.contentText {
    background-color: var(--view-background-color);
  }
  gr-diff-row td.blank {
    background-color: var(--diff-blank-background-color);
  }
  gr-diff-element div.canComment gr-diff-row td.lineNum button.lineNumButton {
    cursor: pointer;
  }
  gr-diff-row td.sign {
    min-width: 1ch;
    width: 1ch;
    background-color: var(--view-background-color);
  }
  gr-diff-row td.sign.blank {
    background-color: var(--diff-blank-background-color);
  }
  gr-diff-row td.content {
    /* Set min width since setting width on table cells still allows them
       to shrink. Do not set max width because CJK
       (Chinese-Japanese-Korean) glyphs have variable width. */
    min-width: var(--content-width, 80ch);
    width: var(--content-width, 80ch);
  }
  /* If there are no intraline info, consider everything changed */
  gr-diff-row td.content.add div.contentText .intraline,
  gr-diff-row td.content.add.no-intraline-info div.contentText,
  gr-diff-row td.sign.add.no-intraline-info,
  gr-diff-section tbody.delta.total gr-diff-row td.content.add div.contentText {
    background-color: var(--dark-add-highlight-color);
  }
  gr-diff-row td.content.add div.contentText,
  gr-diff-row td.sign.add {
    background-color: var(--light-add-highlight-color);
  }
  /* If there are no intraline info, consider everything changed */
  gr-diff-row td.content.remove div.contentText .intraline,
  gr-diff-row td.content.remove.no-intraline-info div.contentText,
  gr-diff-section
    tbody.delta.total
    gr-diff-row
    td.content.remove
    div.contentText,
  gr-diff-row td.sign.remove.no-intraline-info {
    background-color: var(--dark-remove-highlight-color);
  }
  gr-diff-row td.content.remove div.contentText,
  gr-diff-row td.sign.remove {
    background-color: var(--light-remove-highlight-color);
  }
  gr-diff-element table.responsive gr-diff-row td.content div.contentText {
    white-space: break-spaces;
    word-break: break-all;
  }
  gr-diff-row td.lineNum button.lineNumButton,
  td.content {
    vertical-align: top;
    white-space: pre;
  }
  gr-diff-row td.lineNum button.lineNumButton {
    color: var(--deemphasized-text-color);
    padding: 0 var(--spacing-m);
    text-align: right;
  }
  gr-diff-element table.responsive gr-diff-row td.blame {
    overflow: hidden;
    width: 200px;
  }
  /** Support the line length indicator **/
  gr-diff-element table.responsive gr-diff-row td.content div.contentText {
    /* Same strategy as in
       https://stackoverflow.com/questions/1179928/how-can-i-put-a-vertical-line-down-the-center-of-a-div
       */
    background-image: linear-gradient(
      var(--line-length-indicator-color),
      var(--line-length-indicator-color)
    );
    background-size: 1px 100%;
    background-position: var(--line-limit-marker) 0;
    background-repeat: no-repeat;
  }
  gr-diff-row td.lineNum.COVERED button.lineNumButton {
    color: var(
      --coverage-covered-line-num-color,
      var(--deemphasized-text-color)
    );
    background-color: var(--coverage-covered, #e0f2f1);
  }
  gr-diff-row td.lineNum.NOT_COVERED button.lineNumButton {
    color: var(
      --coverage-covered-line-num-color,
      var(--deemphasized-text-color)
    );
    background-color: var(--coverage-not-covered, #ffd1a4);
  }
  gr-diff-row td.lineNum.PARTIALLY_COVERED button.lineNumButton {
    color: var(
      --coverage-covered-line-num-color,
      var(--deemphasized-text-color)
    );
    background: linear-gradient(
      to right bottom,
      var(--coverage-not-covered, #ffd1a4) 0%,
      var(--coverage-not-covered, #ffd1a4) 50%,
      var(--coverage-covered, #e0f2f1) 50%,
      var(--coverage-covered, #e0f2f1) 100%
    );
  }
`;

// Styles related to (not) highlighting ignored whitespace.
export const grDiffIgnoredWhitespaceStyles = css`
  gr-diff-section
    tbody.ignoredWhitespaceOnly
    gr-diff-row
    td.sign.no-intraline-info,
  gr-diff-section
    tbody.ignoredWhitespaceOnly
    gr-diff-row
    td.content.add
    div.contentText
    .intraline,
  gr-diff-section
    tbody.delta.total.ignoredWhitespaceOnly
    gr-diff-row
    td.content.add
    div.contentText,
  gr-diff-section
    tbody.ignoredWhitespaceOnly
    gr-diff-row
    td.content.add
    div.contentText,
  gr-diff-section
    tbody.ignoredWhitespaceOnly
    gr-diff-row
    td.content.remove
    div.contentText
    .intraline,
  gr-diff-section
    tbody.delta.total.ignoredWhitespaceOnly
    gr-diff-row
    td.content.remove
    div.contentText,
  gr-diff-section
    tbody.ignoredWhitespaceOnly
    gr-diff-row
    td.content.remove
    div.contentText {
    background-color: var(--view-background-color);
  }
`;

// Styles related to highlighting moved code sections.
export const grDiffMoveStyles = css`
  gr-diff-section tbody.dueToMove gr-diff-row .sign.add,
  gr-diff-section tbody.dueToMove gr-diff-row td.content.add div.contentText,
  gr-diff-section tbody.dueToMove tr.moveControls.movedIn .sign.right,
  gr-diff-section tbody.dueToMove tr.moveControls.movedIn td.moveHeader,
  gr-diff-section tbody.delta.total.dueToMove td.content.add div.contentText {
    background-color: var(--diff-moved-in-background);
  }

  gr-diff-section tbody.dueToMove.changed gr-diff-row .sign.add,
  gr-diff-section
    tbody.dueToMove.changed
    gr-diff-row
    td.content.add
    div.contentText,
  gr-diff-section tbody.dueToMove.changed tr.moveControls.movedIn .sign.right,
  gr-diff-section tbody.dueToMove.changed tr.moveControls.movedIn td.moveHeader,
  gr-diff-section
    tbody.delta.total.dueToMove.changed
    td.content.add
    div.contentText {
    background-color: var(--diff-moved-in-changed-background);
  }

  gr-diff-section tbody.dueToMove .sign.remove,
  gr-diff-section tbody.dueToMove td.content.remove div.contentText,
  gr-diff-section tbody.dueToMove tr.moveControls.movedOut td.moveHeader,
  gr-diff-section tbody.dueToMove tr.moveControls.movedOut .sign.left,
  gr-diff-section
    tbody.delta.total.dueToMove
    td.content.remove
    div.contentText {
    background-color: var(--diff-moved-out-background);
  }

  gr-diff-section tbody.delta.dueToMove tr.movedIn td.moveHeader {
    --gr-range-header-color: var(--diff-moved-in-label-color);
  }
  gr-diff-section tbody.delta.dueToMove.changed tr.movedIn td.moveHeader {
    --gr-range-header-color: var(--diff-moved-in-changed-label-color);
  }
  gr-diff-section tbody.delta.dueToMove tr.movedOut td.moveHeader {
    --gr-range-header-color: var(--diff-moved-out-label-color);
  }
`;

// Styles related to highlighting rebased code sections.
export const grDiffRebaseStyles = css`
  gr-diff-section
    tbody.dueToRebase
    gr-diff-row
    td.content.add
    div.contentText
    .intraline,
  gr-diff-section
    tbody.delta.total.dueToRebase
    gr-diff-row
    td.content.add
    div.contentText {
    background-color: var(--dark-rebased-add-highlight-color);
  }
  gr-diff-section tbody.dueToRebase gr-diff-row td.content.add div.contentText {
    background-color: var(--light-rebased-add-highlight-color);
  }
  gr-diff-section
    tbody.dueToRebase
    gr-diff-row
    td.content.remove
    div.contentText
    .intraline,
  gr-diff-section
    tbody.delta.total.dueToRebase
    gr-diff-row
    td.content.remove
    div.contentText {
    background-color: var(--dark-rebased-remove-highlight-color);
  }
  gr-diff-section
    tbody.dueToRebase
    gr-diff-row
    td.content.remove
    div.contentText {
    background-color: var(--light-rebased-remove-highlight-color);
  }
`;

// Styles related to selecting code in gr-diff.
export const grDiffSelectionStyles = css`
  /* by default do not allow selecting anything */
  gr-context-controls-section .contextLineNum,
  gr-diff-row td.lineNum button.lineNumButton,
  gr-diff-row tr.diff-row,
  gr-diff-row td.content,
  gr-diff-section tbody.contextControl,
  gr-diff-row td.blame,
  #diffHeader {
    -webkit-user-select: none;
    -moz-user-select: none;
    -ms-user-select: none;
    user-select: none;
  }
  /* selected-left */
  gr-diff-element.selected-left:not(.selected-comment)
    gr-diff-row
    tr.side-by-side
    td.left
    + td.content
    div.contentText,
  gr-diff-element.selected-left:not(.selected-comment)
    gr-diff-row
    tr.unified
    td.left.lineNum
    ~ td.content:not(.both)
    div.contentText {
    -webkit-user-select: text;
    -moz-user-select: text;
    -ms-user-select: text;
    user-select: text;
  }
  /* selected-right */
  gr-diff-element.selected-right:not(.selected-comment)
    gr-diff-row
    tr.side-by-side
    td.right
    + td.content
    div.contentText,
  gr-diff-element.selected-right:not(.selected-comment)
    gr-diff-row
    tr.unified
    td.right.lineNum
    ~ td.content
    div.contentText {
    -webkit-user-select: text;
    -moz-user-select: text;
    -ms-user-select: text;
    user-select: text;
  }
  /* selected-comment */
  gr-diff-element.selected-left.selected-comment
    gr-diff-row
    tr.side-by-side
    td.left
    + td.content
    div.thread-group
    .message,
  gr-diff-element.selected-left.selected-comment
    ::slotted(.comment-thread[diff-side='left']),
  gr-diff-element.selected-right.selected-comment
    gr-diff-row
    tr.side-by-side
    td.right
    + td.content
    div.thread-group
    .message
    :not(.collapsedContent),
  gr-diff-element.selected-right.selected-comment
    ::slotted(.comment-thread[diff-side='right']),
  gr-diff-element.selected-comment
    gr-diff-row
    tr.unified
    td.content
    div.thread-group
    .message
    :not(.collapsedContent) {
    -webkit-user-select: text;
    -moz-user-select: text;
    -ms-user-select: text;
    user-select: text;
  }
  /* selected-blame */
  gr-diff-element.selected-blame gr-diff-row td.blame {
    -webkit-user-select: text;
    -moz-user-select: text;
    -ms-user-select: text;
    user-select: text;
  }
`;

// Styles related to the <gr-diff-text> component.
export const grDiffTextStyles = css`
  gr-diff-text .token-highlight {
    background-color: var(--token-highlighting-color, #fffd54);
  }
  /* Describes two states of semantic tokens: whenever a token has a
     definition that can be navigated to (navigable) and whenever
     the token is actually clickable to perform this navigation. */
  gr-diff-text .semantic-token.navigable {
    text-decoration-style: dotted;
    text-decoration-line: underline;
  }
  gr-diff-text .semantic-token.navigable.clickable {
    text-decoration-style: solid;
    cursor: pointer;
  }
  gr-diff-text .br:after {
    /* Line feed */
    content: '\\A';
  }
  gr-diff-text .tab {
    display: inline-block;
  }
  gr-diff-text .tab-indicator:before {
    color: var(--diff-tab-indicator-color);
    /* >> character */
    content: '\\00BB';
    position: absolute;
  }
  gr-diff-text .special-char-indicator {
    /* spacing so elements don't collide */
    padding-right: var(--spacing-m);
  }
  gr-diff-text .special-char-indicator:before {
    color: var(--diff-tab-indicator-color);
    content: '•';
    position: absolute;
  }
  gr-diff-text .special-char-warning {
    /* spacing so elements don't collide */
    padding-right: var(--spacing-m);
  }
  gr-diff-text .special-char-warning:before {
    color: var(--warning-foreground);
    content: '!';
    position: absolute;
  }
  /* Is defined after other background-colors, such that this
     rule wins in case of same specificity. */
  td.content div.contentText gr-diff-text .trailing-whitespace,
  td.content div.contentText gr-diff-text .trailing-whitespace .intraline {
    border-radius: var(--border-radius, 4px);
    background-color: var(--diff-trailing-whitespace-indicator);
  }
`;

// Styles related to the image diffs.
export const grDiffImageStyles = css`
  gr-image-viewer {
    width: 100%;
    height: 100%;
    max-width: var(--image-viewer-max-width, 95vw);
    max-height: var(--image-viewer-max-height, 90vh);
    /* Defined by paper-styles default-theme and used in various
       components. background-color-secondary is a compromise between
       fairly light in light theme (where we ideally would want
       background-color-primary) yet slightly offset against the app
       background in dark mode, where drop shadows e.g. around paper-card
       are almost invisible. */
    --primary-background-color: var(--background-color-secondary);
  }
  tbody.image-diff .gr-diff {
    text-align: center;
  }
  tbody.image-diff img {
    box-shadow: var(--elevation-level-1);
    max-width: 50em;
  }
  tbody.image-diff button.right.lineNumButton {
    border-left: 1px solid var(--border-color);
  }
  tbody.image-diff label {
    font-family: var(--font-family);
    font-style: italic;
  }
  .image-diff td.content {
    background-color: var(--diff-blank-background-color);
  }
`;

// Styles related to the binary diffs.
export const grDiffBinaryStyles = css`
  gr-diff-element tbody.binary-diff td {
    font-family: var(--font-family);
    font-style: italic;
    text-align: center;
    padding: var(--spacing-s) 0;
  }
<<<<<<< HEAD   (cb763a Unmark updateCheckTimerHandle as state())
=======
  .diff-row {
    outline: none;
    user-select: none;
  }
  .diff-row.target-row.target-side-left .lineNumButton.left,
  .diff-row.target-row.target-side-right .lineNumButton.right,
  .diff-row.target-row.unified .lineNumButton {
    color: var(--primary-text-color);
  }

  /* Preparing selected line cells with position relative so it allows a
     positioned overlay with 'position: absolute'. */
  .target-row td {
    position: relative;
  }

  /* Defines an overlay to the selected line for drawing an outline without
     blocking user interaction (e.g. text selection). */
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

  /* The outline for the selected content cell should be the same in all
     cases. */
  .target-row.target-side-left td.left.content::before,
  .target-row.target-side-right td.right.content::before,
  .unified.target-row td.content::before {
    border-width: 1px 1px 1px 0;
  }

  /* The outline for the sign cell should be always be contiguous
     top/bottom. */
  .target-row.target-side-left td.left.sign::before,
  .target-row.target-side-right td.right.sign::before {
    border-width: 1px 0;
  }

  /* For side-by-side we need to select the correct line number to
     "visually close" the outline. */
  .side-by-side.target-row.target-side-left td.left.lineNum::before,
  .side-by-side.target-row.target-side-right td.right.lineNum::before {
    border-width: 1px 0 1px 1px;
  }

  /* For unified diff we always start the overlay from the left cell. */
  .unified.target-row td.left:not(.content)::before {
    border-width: 1px 0 1px 1px;
  }

  /* For unified diff we should continue the top/bottom border in right
     line number column. */
  .unified.target-row td.right:not(.content)::before {
    border-width: 1px 0;
  }

  .content {
    background-color: var(--diff-blank-background-color);
  }

  /* Describes two states of semantic tokens: whenever a token has a
     definition that can be navigated to (navigable) and whenever
     the token is actually clickable to perform this navigation. */
  .semantic-token.navigable {
    text-decoration-style: dotted;
    text-decoration-line: underline;
  }
  .semantic-token.navigable.clickable {
    text-decoration-style: solid;
    cursor: pointer;
  }

  /* The file line, which has no contentText, add some margin before the
     first comment. We cannot add padding the container because we only
     want it if there is at least one comment thread, and the slotting
     makes :empty not work as expected. */
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
    /* Set min width since setting width on table cells still allows them
       to shrink. Do not set max width because CJK
       (Chinese-Japanese-Korean) glyphs have variable width. */
    min-width: var(--content-width, 80ch);
    width: var(--content-width, 80ch);
  }
  /* If there are no intraline info, consider everything changed */
  .content.add .contentText .intraline,
  .content.add.no-intraline-info .contentText,
  .sign.add.no-intraline-info,
  .delta.total .content.add .contentText {
    background-color: var(--dark-add-highlight-color);
  }
  .content.add .contentText,
  .sign.add {
    background-color: var(--light-add-highlight-color);
  }
  /* If there are no intraline info, consider everything changed */
  .content.remove .contentText .intraline,
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
    background-color: var(--light-rebased-remove-highlight-color);
  }

  /* dueToMove */
  .dueToMove .sign.add,
  .dueToMove .content.add .contentText,
  .dueToMove .moveControls.movedIn .sign.right,
  .dueToMove .moveControls.movedIn .moveHeader,
  .delta.total.dueToMove .content.add .contentText {
    background-color: var(--diff-moved-in-background);
  }

  .dueToMove.changed .sign.add,
  .dueToMove.changed .content.add .contentText,
  .dueToMove.changed .moveControls.movedIn .sign.right,
  .dueToMove.changed .moveControls.movedIn .moveHeader,
  .delta.total.dueToMove.changed .content.add .contentText {
    background-color: var(--diff-moved-in-changed-background);
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
  .delta.dueToMove.changed .movedIn .moveHeader {
    --gr-range-header-color: var(--diff-moved-in-changed-label-color);
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

  .content .contentText gr-diff-text:empty:after,
  .content .contentText gr-legacy-text:empty:after,
  .content .contentText:empty:after {
    /* Newline, to ensure empty lines are one line-height tall. */
    content: '\\A';
  }

  /* Context controls */
  .contextControl {
    display: table-row-group;
    background-color: transparent;
    border: none;
    --divider-height: var(--spacing-s);
    --divider-border: 1px;
  }
  /* TODO: Is this still used? */
  .contextControl gr-button gr-icon {
    /* should match line-height of gr-button */
    font-size: var(--line-height-mono, 18px);
  }
  .contextControl td:not(.lineNumButton) {
    text-align: center;
  }

  /* Padding rows behind context controls. Styled as a continuation of the
     line gutters and code area. */
  .contextBackground > .contextLineNum {
    background-color: var(--diff-blank-background-color);
  }
  .contextBackground > td:not(.contextLineNum) {
    background-color: var(--view-background-color);
  }
  .contextBackground {
    /* One line of background behind the context expanders which they can
       render on top of, plus some padding. */
    height: calc(var(--line-height-normal) + var(--spacing-s));
  }

  /* Hide the actual context control buttons */
  :host(.disable-context-control-buttons) .contextControl gr-context-controls {
    display: none;
  }
  /* Maintain a small amount of padding at the edges of diff chunks */
  :host(.disable-context-control-buttons) .contextControl .contextBackground {
    height: var(--spacing-s);
    border-right: none;
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
  .content .contentText .trailing-whitespace,
  .trailing-whitespace .intraline,
  .content .contentText .trailing-whitespace .intraline {
    border-radius: var(--border-radius, 4px);
    background-color: var(--diff-trailing-whitespace-indicator);
  }
  #diffHeader {
    background-color: var(--table-header-background-color);
    border-bottom: 1px solid var(--border-color);
    color: var(--link-color);
    padding: var(--spacing-m) 0 var(--spacing-m) 48px;
  }
  gr-diff-element {
    /* for gr-selection-action-box positioning */
    position: relative;
    /* Firefox requires a block to position child elements absolutely */
    display: block;
  }
  #diffTable:focus {
    outline: none;
  }
  #loadingError,
  #sizeWarning {
    display: block;
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
  .target-row td.blame {
    background: var(--diff-selection-background-color);
  }
  td.lost div {
    background-color: var(--info-background);
  }
  td.lost div.lost-message {
    font-family: var(--font-family, 'Roboto');
    font-size: var(--font-size-normal, 14px);
    line-height: var(--line-height-normal);
    padding: var(--spacing-s) 0;
  }
  td.lost div.lost-message gr-icon {
    padding: 0 var(--spacing-s) 0 var(--spacing-m);
    color: var(--blue-700);
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
    /* Same strategy as in
       https://stackoverflow.com/questions/1179928/how-can-i-put-a-vertical-line-down-the-center-of-a-div
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
    color: var(
      --coverage-covered-line-num-color,
      var(--deemphasized-text-color)
    );
    background-color: var(--coverage-covered, #e0f2f1);
  }
  .lineNum.NOT_COVERED .lineNumButton {
    color: var(
      --coverage-covered-line-num-color,
      var(--deemphasized-text-color)
    );
    background-color: var(--coverage-not-covered, #ffd1a4);
  }
  .lineNum.PARTIALLY_COVERED .lineNumButton {
    color: var(
      --coverage-covered-line-num-color,
      var(--deemphasized-text-color)
    );
    background: linear-gradient(
      to right bottom,
      var(--coverage-not-covered, #ffd1a4) 0%,
      var(--coverage-not-covered, #ffd1a4) 50%,
      var(--coverage-covered, #e0f2f1) 50%,
      var(--coverage-covered, #e0f2f1) 100%
    );
  }

  // TODO: Investigate whether this CSS is still necessary.
  /* BEGIN: Select and copy for Polymer 2 */
  /* Below was copied and modified from the original css in gr-diff-selection.html. */
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

  /* Make comments and check results selectable when selected */
  .selected-left.selected-comment ::slotted(.comment-thread[diff-side='left']),
  .selected-right.selected-comment
    ::slotted(.comment-thread[diff-side='right']) {
    -webkit-user-select: text;
    -moz-user-select: text;
    -ms-user-select: text;
    user-select: text;
  }
  /* END: Select and copy for Polymer 2 */

  .whitespace-change-only-message {
    background-color: var(--diff-context-control-background-color);
    border: 1px solid var(--diff-context-control-border-color);
    text-align: center;
  }

  .token-highlight {
    background-color: var(--token-highlighting-color, #fffd54);
  }

  gr-selection-action-box {
    /* Needs z-index to appear above wrapped content, since it's inserted
       into DOM before it. */
    z-index: 120;
  }

  gr-diff-image-new,
  gr-diff-image-old,
  gr-diff-section,
  gr-context-controls-section,
  gr-diff-row {
    display: contents;
  }
>>>>>>> CHANGE (64b80e Fix code comment position with Firefox)
`;
