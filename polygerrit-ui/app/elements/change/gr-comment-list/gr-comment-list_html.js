import { html } from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      :host {
        display: block;
        word-wrap: break-word;
      }
      .file {
        padding: var(--spacing-s) 0;
      }
      .container {
        display: flex;
        padding: var(--spacing-s) 0;
      }
      .lineNum {
        margin-right: var(--spacing-s);
        min-width: 135px;
        text-align: right;
      }
      .message {
        flex: 1;
        --gr-formatted-text-prose-max-width: 80ch;
      }
      @media screen and (max-width: 50em) {
        .container {
          flex-direction: column;
        }
        .lineNum {
          margin-right: 0;
          min-width: initial;
          text-align: left;
        }
      }
    </style>
    <template is="dom-repeat" items="[[_computeFilesFromComments(comments)]]" as="file">
      <div class="file"><a class="fileLink" href="[[_computeDiffURL(file, changeNum, comments)]]">[[computeDisplayPath(file)]]</a></div>
      <template is="dom-repeat" items="[[_computeCommentsForFile(comments, file)]]" as="comment">
        <div class="container">
          <a class="lineNum" href\$="[[_computeDiffLineURL(file, changeNum, comment.patch_set, comment)]]">
             <span hidden\$="[[!comment.line]]">
               <span>[[_computePatchDisplayName(comment)]]</span>
               Line <span>[[comment.line]]</span>
             </span>
             <span hidden\$="[[comment.line]]">
               File comment:
             </span>
          </a>
          <gr-formatted-text class="message" no-trailing-margin="" content="[[comment.message]]" config="[[projectConfig.commentlinks]]"></gr-formatted-text>
        </div>
      </template>
    </template>
`;
