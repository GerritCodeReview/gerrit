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

/** <gr-textarea> input event */
export declare interface InputEventDetail {
  value: string;
}

/** <gr-textarea> event for current cursor position */
export declare interface CursorPositionChangeEventDetail {
  position: number;
}

/** <gr-textarea> event when showing a hint */
export declare interface HintShownEventDetail {
  hint: string;
  oldValue: string;
}

/** <gr-textarea> event when a hint was dismissed */
export declare interface HintDismissedEventDetail {
  hint: string;
}

/** <gr-textarea> event when a hint was applied */
export declare interface HintAppliedEventDetail {
  hint: string;
  oldValue: string;
}

/** <gr-textarea> interface that external users can rely on */
export declare interface GrTextarea extends HTMLElement {
  value?: string;
  nativeElement?: HTMLElement;
  placeholder?: string;
  placeholderHint?: string;
  hint?: string;
  setRangeText: (replacement: string, start: number, end: number) => void;
}

/** <gr-autogrow-textarea> interface that external users can rely on */
export declare interface GrAutogrowTextarea extends HTMLElement {
  value?: string;
  nativeElement?: HTMLElement;
  placeholder?: string;
  setRangeText: (replacement: string, start: number, end: number) => void;
}
