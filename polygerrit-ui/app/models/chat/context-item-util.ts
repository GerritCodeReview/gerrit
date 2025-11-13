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
