/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {HighlightJS} from '../types/types';

interface HljsWorker {
  hljs: HighlightJS;
}

const ctx: Worker & HljsWorker = self as any;

ctx.onmessage = function (e: MessageEvent) {
  console.log(`hljs-worker: message received ${e.data.length}`);
  importScripts(e.data.url);
  ctx.hljs.configure({classPrefix: 'gr-diff gr-syntax gr-syntax-'});
  const result = ctx.hljs.highlight('typescript', e.data.code, true);
  const lines = postProcess(result.value);
  ctx.postMessage(lines);
};

const openingSpan = new RegExp('<span .*?>', 'g');
const closingSpan = new RegExp('</span>', 'g');

/**
 * HighlightJS produces one long HTML string with HTML elements spanning
 * multiple lines. <gr-diff> is line based and needs all elements closed
 * at the end of line, which is what this method does.
 *
 * We are looking for opening <span...>s and closing </span>s. Unclosed
 * spans will be carried over to the next line.
 */
function postProcess(highlightedCode: string): string[] {
  const lines = highlightedCode.split('\n');
  const processedLines: string[] = [];
  let carryOverSpans: string[] = [];
  for (let line of lines) {
    for (const carryOver of carryOverSpans.reverse()) {
      line = carryOver + line;
    }
    carryOverSpans = [];
    const openingMatches = line.match(openingSpan) ?? [];
    const closingMatches = line.match(closingSpan) ?? [];
    const openingCount = openingMatches.length;
    const closingCount = closingMatches.length;
    if (openingCount < closingCount) {
      console.warn('more closing </span>s than opening <span>s found');
    }
    if (openingCount > closingCount) {
      carryOverSpans = openingMatches.slice(0, openingCount - closingCount);
      line += '</span>'.repeat(openingCount - closingCount);
    }
    processedLines.push(line);
  }
  return processedLines;
}
