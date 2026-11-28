/**
 * @license
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
import {getSyntaxClassName} from '../../../embed/diff/gr-syntax-themes/gr-syntax-theme';
import {
  SyntaxLayerLine,
  SyntaxLayerRange,
} from '../../../types/syntax-worker-api';

// Replacing children removes selection endpoints. Wait for an active range
// to clear and preserve a collapsed caret at its UTF-16 text offset.
export function updateCodePreservingSelection(
  code: HTMLElement,
  root: ShadowRoot,
  update: () => void
): boolean {
  const selection = code.ownerDocument.getSelection();
  let caret: AbstractRange | undefined;
  if (selection?.rangeCount) {
    let ranges: readonly AbstractRange[] | undefined;
    try {
      ranges = selection.getComposedRanges?.({shadowRoots: [root]});
    } catch {
      // Older WebKit expects the shadow root as a positional argument.
      const legacyRanges = selection.getComposedRanges as unknown as (
        ...roots: ShadowRoot[]
      ) => StaticRange[];
      ranges = legacyRanges.call(selection, root);
    }
    if (!ranges) {
      const local = root.getSelection?.();
      if (!local && !selection.isCollapsed) return false;
      const visible = local ?? selection;
      ranges = Array.from({length: visible.rangeCount}, (_, i) =>
        visible.getRangeAt(i)
      );
    }
    for (const range of ranges) {
      if (
        !code.contains(range.startContainer) &&
        !code.contains(range.endContainer)
      )
        continue;
      if (!range.collapsed) return false;
      caret = range;
    }
  }
  if (!caret || !selection) {
    update();
    return true;
  }

  const prefix = code.ownerDocument.createRange();
  prefix.selectNodeContents(code);
  prefix.setEnd(caret.startContainer, caret.startOffset);
  let offset = prefix.toString().length;
  update();
  const walker = code.ownerDocument.createTreeWalker(
    code,
    NodeFilter.SHOW_TEXT
  );
  for (
    let node = walker.nextNode() as Text | null;
    node;
    node = walker.nextNode() as Text | null
  ) {
    if (offset <= node.length) {
      selection.collapse(node, offset);
      break;
    }
    offset -= node.length;
  }
  return true;
}

export function highlightedCodeFragment(
  text: string,
  lines: SyntaxLayerLine[]
) {
  const fragment = document.createDocumentFragment();
  text.split('\n').forEach((line, index) => {
    if (index) fragment.append('\n');
    appendSyntaxLine(fragment, line, lines[index]?.ranges ?? []);
  });
  return fragment;
}

// Worker ranges arrive in opening-tag order. Append text once in UTF-16
// offsets and close nested spans as their ranges end.
function appendSyntaxLine(
  fragment: DocumentFragment,
  text: string,
  ranges: SyntaxLayerRange[]
) {
  if (!ranges.length) {
    fragment.append(text);
    return;
  }
  const scopes: {node: ParentNode; end: number}[] = [
    {node: fragment, end: text.length},
  ];
  let position = 0;
  function appendTextUntil(offset: number) {
    while (scopes.length) {
      const scope = scopes[scopes.length - 1];
      const end = Math.min(offset, scope.end);
      if (position < end) scope.node.append(text.slice(position, end));
      position = end;
      if (offset < scope.end) break;
      scopes.pop();
    }
  }

  for (const {start, length, className} of ranges) {
    const syntaxClass = getSyntaxClassName(className);
    if (!syntaxClass || !length || start >= text.length) continue;
    appendTextUntil(start);
    const span = document.createElement('span');
    span.className = syntaxClass;
    scopes[scopes.length - 1].node.append(span);
    scopes.push({node: span, end: Math.min(start + length, text.length)});
  }
  appendTextUntil(text.length);
}
