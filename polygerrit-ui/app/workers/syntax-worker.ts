/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {HighlightJS} from '../types/types';
import {
  isInit,
  isRequest,
  SyntaxWorkerMessage,
  SyntaxWorkerResult,
} from '../types/syntax-worker-api';
import {highlightedStringToRanges} from '../utils/syntax-util';

export class SyntaxWorker {
  private highlightJsLib?: HighlightJS;

  constructor(
    private readonly loadLibrary: (url: string) => HighlightJS | undefined
  ) {}

  handleMessage(message: SyntaxWorkerMessage): SyntaxWorkerResult | undefined {
    try {
      if (isInit(message)) {
        this.highlightJsLib = this.loadLibrary(message.url);
        if (!this.highlightJsLib)
          throw new Error('HighlightJS lib not available after import');
        this.highlightJsLib.configure({classPrefix: ''});
        return {ranges: []};
      }
      if (isRequest(message)) {
        if (
          message.ignoreUnknownLanguage &&
          this.highlightJsLib &&
          !this.highlightJsLib.getLanguage(message.language)
        ) {
          return {ranges: []};
        }
        return {ranges: this.highlightCode(message.language, message.code)};
      }
    } catch (err) {
      return {
        error: err instanceof Error ? err.message : 'syntax worker error',
        ranges: [],
      };
    }
    return undefined;
  }

  private highlightCode(language: string, code: string) {
    if (!this.highlightJsLib) throw new Error('worker not initialized');
    const highlighted = this.highlightJsLib.highlight(language, code, true);
    return highlightedStringToRanges(highlighted.value);
  }
}
