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
const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `<dom-module id="gr-change-list-styles">
  <template>
    <style>
      gr-change-list-item {
        border-top: 1px solid var(--border-color);
      }
      gr-change-list-item[selected],
      gr-change-list-item:focus {
        background-color: var(--selection-background-color);
      }
      .groupTitle td,
      .cell {
        vertical-align: middle;
      }
      .groupTitle td:not(.label):not(.endpoint),
      .cell:not(.label):not(.endpoint) {
        padding-right: 8px;
      }
      .groupTitle td {
        color: var(--deemphasized-text-color);
        text-align: left;
      }
      .groupHeader {
        background-color: transparent;
        font-size: var(--font-size-h3);
        font-weight: var(--font-weight-h3);
        line-height: var(--line-height-h3);
      }
      .groupContent {
        background-color: var(--background-color-primary);
        box-shadow: var(--elevation-level-1);
      }
      .groupHeader a {
        color: var(--primary-text-color);
        text-decoration: none;
      }
      .groupHeader a:hover {
        text-decoration: underline;
      }
      .groupTitle td,
      .cell {
        padding: var(--spacing-s) 0;
      }
      .groupHeader .cell {
        padding-top: var(--spacing-l);
      }
      .star {
        padding: 0;
      }
      gr-change-star {
        vertical-align: middle;
      }
      .branch,
      .star,
      .label,
      .number,
      .owner,
      .assignee,
      .updated,
      .size,
      .status,
      .repo {
        white-space: nowrap;
      }
      .star {
        vertical-align: middle;
      }
      .leftPadding {
        width: var(--spacing-l);
      }
      .star {
        width: 30px;
      }
      .label, .endpoint {
        border-left: 1px solid var(--border-color);
      }
      .groupTitle td.label,
      .label {
        text-align: center;
        width: 3rem;
      }
      .truncatedRepo {
        display: none;
      }
      @media only screen and (max-width: 150em) {
        .assignee,
        .branch,
        .owner,
        .reviewers {
          overflow: hidden;
          max-width: 18rem;
          text-overflow: ellipsis;
        }
        .truncatedRepo {
          display: inline-block;
        }
        .fullRepo {
          display: none;
        }
      }
      @media only screen and (max-width: 100em) {
        .assignee,
        .branch,
        .owner,
        .reviewers {
          max-width: 10rem;
        }
      }
      @media only screen and (max-width: 50em) {
        :host {
          font-family: var(--header-font-family);
          font-size: var(--font-size-h3);
          font-weight: var(--font-weight-h3);
          line-height: var(--line-height-h3);
        }
        gr-change-list-item {
          flex-wrap: wrap;
          justify-content: space-between;
          padding: var(--spacing-xs) var(--spacing-m);
        }
        gr-change-list-item[selected],
        gr-change-list-item:focus {
          background-color: var(--view-background-color);
          border: none;
          border-top: 1px solid var(--border-color);
        }
        gr-change-list-item:hover {
          background-color: var(--view-background-color);
        }
        .cell {
          align-items: center;
          display: flex;
        }
        .groupTitle,
        .leftPadding,
        .status,
        .repo,
        .branch,
        .updated,
        .label,
        .assignee,
        .groupHeader .star,
        .noChanges .star {
          display: none;
        }
        .groupHeader .cell,
        .noChanges .cell {
          padding-left: var(--spacing-m);
        }
        .subject {
          margin-bottom: var(--spacing-xs);
          width: calc(100% - 2em);
        }
        .owner,
        .size {
          max-width: none;
        }
        .noChanges .cell {
          display: block;
          height: auto;
        }
      }
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);

/*
  FIXME(polymer-modulizer): the above comments were extracted
  from HTML and may be out of place here. Review them and
  then delete this comment!
*/

