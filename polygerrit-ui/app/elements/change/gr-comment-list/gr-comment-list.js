/**
@license
Copyright (C) 2015 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
/*
  The custom CSS property `--gr-formatted-text-prose-max-width` controls the max
  width of formatted text blocks that are not code.
*/
/*
  FIXME(polymer-modulizer): the above comments were extracted
  from HTML and may be out of place here. Review them and
  then delete this comment!
*/
import '../../../behaviors/base-url-behavior/base-url-behavior.js';

import '../../../behaviors/gr-path-list-behavior/gr-path-list-behavior.js';
import '../../../behaviors/gr-url-encoding-behavior/gr-url-encoding-behavior.js';
import '../../../../@polymer/polymer/polymer-legacy.js';
import '../../core/gr-navigation/gr-navigation.js';
import '../../shared/gr-formatted-text/gr-formatted-text.js';
import '../../../styles/shared-styles.js';
Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: block;
        word-wrap: break-word;
      }
      .file {
        border-top: 1px solid var(--border-color);
        font-family: var(--font-family-bold);
        margin: 10px 0 3px;
        padding: 10px 0 5px;
      }
      .container {
        display: flex;
        margin: .5em 0;
      }
      .lineNum {
        margin-right: .5em;
        min-width: 10em;
        text-align: right;
      }
      .message {
        flex: 1;
        --gr-formatted-text-prose-max-width: 80ch;
      }
      @media screen and (max-width: 50em) {
        .container {
          flex-direction: column;
          margin: 0 0 .5em .5em;
        }
        .lineNum {
          min-width: initial;
          text-align: left;
        }
      }
    </style>
    <template is="dom-repeat" items="[[_computeFilesFromComments(comments)]]" as="file">
      <div class="file">[[computeDisplayPath(file)]]:</div>
      <template is="dom-repeat" items="[[_computeCommentsForFile(comments, file)]]" as="comment">
        <div class="container">
          <a class="lineNum" href\$="[[_computeDiffLineURL(file, changeNum, comment.patch_set, comment)]]">
             <span hidden\$="[[!comment.line]]">
               <span>[[_computePatchDisplayName(comment)]]</span>
               Line <span>[[comment.line]]</span>:
             </span>
             <span hidden\$="[[comment.line]]">
               File comment:
             </span>
          </a>
          <gr-formatted-text class="message" no-trailing-margin="" content="[[comment.message]]" config="[[projectConfig.commentlinks]]"></gr-formatted-text>
        </div>
      </template>
    </template>
`,

  is: 'gr-comment-list',

  behaviors: [
    Gerrit.BaseUrlBehavior,
    Gerrit.PathListBehavior,
    Gerrit.URLEncodingBehavior,
  ],

  properties: {
    changeNum: Number,
    comments: Object,
    patchNum: Number,
    projectName: String,
    /** @type {?} */
    projectConfig: Object,
  },

  _computeFilesFromComments(comments) {
    const arr = Object.keys(comments || {});
    return arr.sort(this.specialFilePathCompare);
  },

  _isOnParent(comment) {
    return comment.side === 'PARENT';
  },

  _computeDiffLineURL(file, changeNum, patchNum, comment) {
    const basePatchNum = comment.hasOwnProperty('parent') ?
        -comment.parent : null;
    return Gerrit.Nav.getUrlForDiffById(this.changeNum, this.projectName,
        file, patchNum, basePatchNum, comment.line,
        this._isOnParent(comment));
  },

  _computeCommentsForFile(comments, file) {
    // Changes are not picked up by the dom-repeat due to the array instance
    // identity not changing even when it has elements added/removed from it.
    return (comments[file] || []).slice();
  },

  _computePatchDisplayName(comment) {
    if (this._isOnParent(comment)) {
      return 'Base, ';
    }
    if (comment.patch_set != this.patchNum) {
      return `PS${comment.patch_set}, `;
    }
    return '';
  }
});
