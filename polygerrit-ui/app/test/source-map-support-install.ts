/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

// Mark the file as a module. Otherwise typescript assumes this is a script
// and doesn't allow "declare global".
// See: https://www.typescriptlang.org/docs/handbook/modules.html
export {};

declare global {
  interface Window {
    sourceMapSupport: {
      install(): void;
    };
  }
}

// The karma.conf.js file loads required module before any other modules
// The source-map-support.js can't be imported with import ... statement
window.sourceMapSupport.install();
