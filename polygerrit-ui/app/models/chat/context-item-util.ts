/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {ContextItem, ContextItemType} from '../../api/ai-code-review';

/** Parses all potential context items from a give text string. */
export function searchForContextLinks(
  text: string,
  contextItemTypes: readonly ContextItemType[]
): ContextItem[] {
  const contextItems: ContextItem[] = [];
  for (const contextItemType of contextItemTypes) {
    const matches = text.matchAll(new RegExp(contextItemType.regex, 'g'));
    for (const match of matches) {
      const url = match[0];
      const contextItem = contextItemType.parse(url);
      if (
        contextItem &&
        !contextItems.some(item => contextItemEquals(item, contextItem))
      ) {
        contextItems.push(contextItem);
      }
    }
  }
  return contextItems;
}

/**
 * Parses a link as a context item. Returns undefined if the link is not a
 * supported context item.
 */
export function parseLink(
  url: string,
  contextItemTypes: readonly ContextItemType[]
): ContextItem | undefined {
  url = url.replace(/\s+/g, ''); // Remove all whitespaces.
  for (const contextItemType of contextItemTypes) {
    const contextItem = contextItemType.parse(url);
    if (contextItem) return contextItem;
  }
  return undefined;
}

/** Implementation of the equals method for ContextItem. */
export function contextItemEquals(a: ContextItem, b: ContextItem): boolean {
  return a.type_id === b.type_id && a.identifier === b.identifier;
}

/**
 * Searches for bug numbers in a commit message that are not linked.
 * It searches for keywords like "bug:", "fixes=", etc. followed by a
 * long number. It ignores existing b/ links and URLs.
 */
export function searchForBugsInCommitMessage(
  commitMessage: string,
  contextItemTypes: readonly ContextItemType[]
): ContextItem[] {
  const buganizerType = contextItemTypes.find(t => t.id === 'buganizer');
  if (!buganizerType) return [];

  const lines = commitMessage.toLowerCase().split('\n');
  // This regex is to identify lines that might contain bug numbers.
  // TODO(b/484367705) support commentlinks defined by each host.
  const keywordRegex =
    // prettier-ignore
    /\b(?:bug|fix|issue|fixed|fixes|bugfix|google-bug-id|cts-coverage-bug|crs-fixed)\b/i;

  const bugIds = new Set<string>();
  for (const line of lines) {
    if (!keywordRegex.test(line)) continue;

    // Remove URLs and b/ links before searching for numbers, because they are
    // handled by searchForContextLinks.
    const cleanedLine = line
      .replace(/https?:\/\/[^\s]+/g, '')
      .replace(/b\/\d+/g, '');

    // This regex finds nums with at least 8 digits, which are likely bug ids.
    const numberRegex = /\b\d{8,}\b/g;
    const numbers = cleanedLine.match(numberRegex) ?? [];
    for (const num of numbers) {
      bugIds.add(num);
    }
  }

  // Convert bugIds to ContextItems.
  const contextItems: ContextItem[] = [];
  for (const num of bugIds) {
    const contextItem = buganizerType.parse(`b/${num}`);
    if (contextItem) contextItems.push(contextItem);
  }
  return contextItems;
}
