/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
export {};

declare global {
  // The current version of lib.dom.d.ts doesn't contains AriaMixin definition,
  // so we have to add some of them ourself.
  // https://developer.mozilla.org/en-US/docs/Web/API/Element#properties_included_from_aria
  interface AriaMixin {
    ariaLabel?: string;
  }

  interface Element extends AriaMixin {
    ariaLabel: string;
  }
}
