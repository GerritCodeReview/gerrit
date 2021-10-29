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
      font-family: var(--font-family);
      font-size: var(--font-size-normal);
      font-weight: var(--font-weight-normal);
      line-height: var(--line-height-normal);
      /* Explicitly set the background color of the diff. We
       * cannot use the diff content type ab because of the skip chunk preceding
       * it, diff processor assumes the chunk of type skip/ab can be collapsed
       * and hides our diff behind context control buttons.
       *  */
      --dark-add-highlight-color: var(--background-color-primary);
    }
    gr-button {
      margin-left: var(--spacing-m);
    }
    gr-comment {
      border-bottom: 1px solid var(--comment-separator-color);
    }
    #actions {
      margin-left: auto;
      padding: var(--spacing-s) var(--spacing-m);
    }
    .comment-box {
      width: 80ch;
      max-width: 100%;
      background-color: var(--comment-background-color);
      color: var(--comment-text-color);
      box-shadow: var(--elevation-level-2);
      border-radius: var(--border-radius);
      flex-shrink: 0;
    }
    #container {
      display: var(--gr-comment-thread-display, flex);
      align-items: flex-start;
      margin: 0 var(--spacing-s) var(--spacing-s);
      white-space: normal;
      /** This is required for firefox to continue the inheritance */
      -webkit-user-select: inherit;
      -moz-user-select: inherit;
      -ms-user-select: inherit;
      user-select: inherit;
    }
    .comment-box.unresolved {
      background-color: var(--unresolved-comment-background-color);
    }
    .comment-box.robotComment {
      background-color: var(--robot-comment-background-color);
    }
    #commentInfoContainer {
      display: flex;
    }
    #unresolvedLabel {
      font-family: var(--font-family);
      margin: auto 0;
      padding: var(--spacing-m);
    }
    .pathInfo {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      padding: 0 var(--spacing-s) var(--spacing-s);
    }
    .fileName {
      padding: var(--spacing-m) var(--spacing-s) var(--spacing-m);
    }
    @media only screen and (max-width: 1200px) {
      .diff-container {
        display: none;
      }
    }
    .diff-container {
      margin-left: var(--spacing-l);
      border: 1px solid var(--border-color);
      flex-grow: 1;
      flex-shrink: 1;
      max-width: 1200px;
    }
    .view-diff-button {
      margin: var(--spacing-s) var(--spacing-m);
    }
    .view-diff-container {
      border-top: 1px solid var(--border-color);
      background-color: var(--background-color-primary);
    }

    /* In saved state the "reply" and "quote" buttons are 28px height.
     * top:4px  positions the 20px icon vertically centered.
     * Currently in draft state the "save" and "cancel" buttons are 20px
     * height, so the link icon does not need a top:4px in gr-comment_html.
     */
    .link-icon {
      position: relative;
      top: 4px;
      cursor: pointer;
    }
    .fileName gr-copy-clipboard {
      display: inline-block;
      visibility: hidden;
      vertical-align: top;
      --gr-button-padding: 0px;
    }
    .fileName:focus-within gr-copy-clipboard,
    .fileName:hover gr-copy-clipboard {
      visibility: visible;
    }
  </style>

  <template is="dom-if" if="[[showFilePath]]">
    <template is="dom-if" if="[[showFileName]]">
      <div class="fileName">
        <template is="dom-if" if="[[_isPatchsetLevelComment(path)]]">
          <span> [[_computeDisplayPath(path)]] </span>
        </template>
        <template is="dom-if" if="[[!_isPatchsetLevelComment(path)]]">
          <a
            href$="[[_getDiffUrlForPath(projectName, changeNum, path, patchNum)]]"
          >
            [[_computeDisplayPath(path)]]
          </a>
          <gr-copy-clipboard
            hideInput=""
            text="[[_computeDisplayPath(path)]]"
          ></gr-copy-clipboard>
        </template>
      </div>
    </template>
    <div class="pathInfo">
      <template is="dom-if" if="[[!_isPatchsetLevelComment(path)]]">
        <a
          href$="[[_getDiffUrlForComment(projectName, changeNum, path, patchNum)]]"
          >[[_computeDisplayLine(lineNum, range)]]</a
        >
      </template>
    </div>
  </template>
  <div id="container">
    <h3 class="assistive-tech-only">
      [[_computeAriaHeading(_orderedComments)]]
    </h3>
    <div
      class$="[[_computeHostClass(unresolved, isRobotComment)]] comment-box"
      tabindex="0"
    >
      <template
        id="commentList"
        is="dom-repeat"
        items="[[_orderedComments]]"
        as="comment"
      >
        <gr-comment
          comment="{{comment}}"
          comments="{{comments}}"
          robot-button-disabled="[[_shouldDisableAction(_showActions, _lastComment)]]"
          change-num="[[changeNum]]"
          project-name="[[projectName]]"
          patch-num="[[patchNum]]"
          draft="[[_isDraft(comment)]]"
          show-actions="[[_showActions]]"
          show-patchset="[[showPatchset]]"
          show-ported-comment="[[_computeShowPortedComment(comment)]]"
          side="[[comment.side]]"
          on-create-fix-comment="_handleCommentFix"
          on-copy-comment-link="handleCopyLink"
        ></gr-comment>
      </template>
      <div
        id="commentInfoContainer"
        hidden$="[[_hideActions(_showActions, _lastComment)]]"
      >
        <span id="unresolvedLabel">[[_getUnresolvedLabel(unresolved)]]</span>
        <div id="actions">
          <iron-icon
            class="link-icon"
            on-click="handleCopyLink"
            class="copy"
            title="Copy link to this comment"
            icon="gr-icons:link"
            role="button"
            tabindex="0"
          >
          </iron-icon>
          <gr-button
            id="replyBtn"
            link=""
            class="action reply"
            on-click="_handleCommentReply"
            >Reply</gr-button
          >
          <gr-button
            id="quoteBtn"
            link=""
            class="action quote"
            on-click="_handleCommentQuote"
            >Quote</gr-button
          >
          <template is="dom-if" if="[[unresolved]]">
            <gr-button
              id="ackBtn"
              link=""
              class="action ack"
              on-click="_handleCommentAck"
              >Ack</gr-button
            >
            <gr-button
              id="doneBtn"
              link=""
              class="action done"
              on-click="_handleCommentDone"
              >Done</gr-button
            >
          </template>
        </div>
      </div>
    </div>
    <template
      is="dom-if"
      if="[[_shouldShowCommentContext(changeNum, showCommentContext, _diff)]]"
    >
      <div class="diff-container">
        <gr-diff
          id="diff"
          change-num="[[changeNum]]"
          diff="[[_diff]]"
          layers="[[layers]]"
          path="[[path]]"
          prefs="[[_prefs]]"
          render-prefs="[[_renderPrefs]]"
          highlight-range="[[getHighlightRange(comments)]]"
        >
        </gr-diff>
        <div class="view-diff-container">
          <a href="[[_getUrlForViewDiff(comments, changeNum, projectName)]]">
            <gr-button link class="view-diff-button">View Diff</gr-button>
          </a>
        </div>
      </div>
    </template>
  </div>
`;
