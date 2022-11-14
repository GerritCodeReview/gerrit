/**
 * @fileoverview The API of class exported globally in embed/gr-diff.ts
 *
 * This is a mechanism to make classes accessible to separately compiled
 * bundles, which cannot directly import the classes from their modules.
 *
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {
  DiffLayer,
  GrAnnotation,
  GrDiffCursor,
  TokenHighlightListener,
} from './diff';

declare global {
  interface Window {
    grdiff: {
      GrAnnotation: GrAnnotation;
      GrDiffCursor: {new (): GrDiffCursor};
      TokenHighlightLayer: {
        new (
          container: HTMLElement,
          listener?: TokenHighlightListener,
          getTokenQueryContainer?: () => HTMLElement
        ): DiffLayer;
      };
    };
  }
}
