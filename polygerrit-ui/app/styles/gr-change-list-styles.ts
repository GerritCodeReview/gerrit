/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const changeListStyles = css`
  td {
    border-top: 1px solid var(--border-color);
  }
  gr-change-list-item[selected],
  gr-change-list-item:focus {
    background-color: var(--selection-background-color);
  }
  gr-change-list-item[highlight] {
    background-color: var(--line-item-highlight-color);
  }
  gr-change-list-item[highlight][selected],
  gr-change-list-item[highlight]:focus {
    background-color: var(--line-item-highlight-selection-color);
  }
  gr-change-list-item:last-child,
  tr.noChanges {
    --last-border-bottom: 1px solid var(--border-color);
    --last-border-radius: 4px;
  }
  td {
    border-bottom: var(--last-border-bottom);
  }
  td:first-child {
    border-bottom-left-radius: var(--last-border-radius);
  }
  td:last-child {
    border-bottom-right-radius: var(--last-border-radius);
  }
  .groupTitle td,
  .cell {
    vertical-align: middle;
  }
  .groupTitle td:not(.label):not(.endpoint):not(.star),
  .cell:not(.label):not(.endpoint):not(.star) {
    padding-right: 8px;
  }
  .groupTitle td {
    color: var(--deemphasized-text-color);
    font-weight: var(--font-weight-bold);
    font-family: var(--header-font-family);
    text-align: left;
  }
  .groupGap {
    height: 10px;
  }
  .groupHeader td {
    background-color: var(--section-header-background-color);
    border-top: 1px solid var(--border-color);
  }
  .groupHeader td:first-child {
    border-top-left-radius: 4px;
  }
  .groupHeader td:last-child {
    border-top-right-radius: 4px;
  }
  .groupHeader {
    font-size: var(--font-size-h2);
    font-weight: var(--font-weight-h2);
    line-height: var(--line-height-h2);
  }
  .groupContent {
    background-color: var(--background-color-primary);
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
    padding: var(--spacing-xs) 0;
  }
  .star {
    padding: 0 var(--spacing-s) 0 0;
  }
  .owner {
    --account-max-length: 100px;
  }
  td:first-child {
    border-left: 1px solid var(--border-color);
  }
  td:last-child {
    border-right: 1px solid var(--border-color);
  }
  .branch,
  .star,
  .label,
  .number,
  .owner,
  .updated,
  .submitted,
  .waiting,
  .size,
  .status,
  .repo {
    white-space: nowrap;
  }
  .leftPadding {
    width: var(--spacing-l);
  }
  .reviewers div {
    overflow: hidden;
  }
  .label,
  .endpoint {
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
    .branch {
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
    .branch {
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
      border-top: 1px solid var(--border-color);
    }
    gr-change-list-item[selected],
    gr-change-list-item:focus td {
      background-color: var(--view-background-color);
      border: none;
    }
    gr-change-list-item:hover {
      background-color: var(--view-background-color);
    }
    td.cell,
    .groupHeader td.cell {
      border-left: none;
      border-right: none;
      border-radius: 0px;
    }
    .cell {
      align-items: center;
      display: flex;
      border: none;
    }
    .groupTitle,
    .leftPadding,
    .selection,
    .status,
    .repo,
    .branch,
    .updated,
    .submitted,
    .waiting,
    .label,
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
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-change-list-styles">
  <template>
    <style>
    ${changeListStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
