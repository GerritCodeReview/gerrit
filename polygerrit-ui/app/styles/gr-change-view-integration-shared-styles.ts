/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Mark the file as a module. Otherwise typescript assumes this is a script
// and $_documentContainer is a global variable.
// See: https://www.typescriptlang.org/docs/handbook/modules.html
export {};

const $_documentContainer = document.createElement('template');

/*
  These are shared styles for change-view-integration endpoints.
  All plugins that registered that endpoint should include this in
  the component to have a consistent UX:

  <style include="gr-change-view-integration-shared-styles"></style>

  And use those defined class to apply these styles.
*/
$_documentContainer.innerHTML = `<dom-module id="gr-change-view-integration-shared-styles">
  <template>
    <style include="shared-styles">
      /* Workaround for empty style block - see https://github.com/Polymer/tools/issues/408 */
    </style>
    <style>
      :host {
        border-top: 1px solid var(--border-color);
        display: block;
      }
      .header {
        color: var(--primary-text-color);
        background-color: var(--table-header-background-color);
        justify-content: space-between;
        padding: var(--spacing-m) var(--spacing-l);
        border-bottom: 1px solid var(--border-color);
      }
      .header .label {
        font-family: var(--header-font-family);
        font-size: var(--font-size-h3);
        font-weight: var(--font-weight-h3);
        line-height: var(--line-height-h3);
        margin: 0 var(--spacing-l) 0 0;
      }
      .header .note {
        color: var(--deemphasized-text-color);
      }
      .content {
        background-color: var(--view-background-color);
      }
      .header a,
      .content a {
        color: var(--link-color);
      }
    </style>
  </template>
</dom-module>`;

document.head.appendChild($_documentContainer.content);
