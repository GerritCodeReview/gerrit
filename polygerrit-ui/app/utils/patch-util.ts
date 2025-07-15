/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {FixReplacementInfo} from '../api/rest-api';

const REPLACEMENT_DELIMITER = '---';

export function replacementsToString(replacements: FixReplacementInfo[]): string {
  return replacements
    .map(r => {
      const range = `${r.range.start_line}-${r.range.end_line}`;
      return `${r.path}\n${range}\n${r.replacement}`;
    })
    .join(`\n${REPLACEMENT_DELIMITER}\n`);
}

export function stringToReplacements(text: string): FixReplacementInfo[] {
  const replacementStrings = text.split(`\n${REPLACEMENT_DELIMITER}\n`);
  return replacementStrings.map(s => {
    const lines = s.split('\n');
    const path = lines[0];
    const rangeParts = lines[1].split('-');
    const start_line = Number(rangeParts[0]);
    const end_line = Number(rangeParts[1]);
    const replacement = lines.slice(2).join('\n');
    return {
      path,
      range: {start_line, end_line, start_character: 0, end_character: 0},
      replacement,
    };
  });
}
