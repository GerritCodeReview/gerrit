/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeInfo, RelatedChangeAndCommitInfo} from '../types/common';

export interface ThreadNode<T> {
  id: string;
  value: T;
  parents: string[];
  topic?: string;
}

export interface ThreadRow<T> {
  node: ThreadNode<T>;
  /** Original result index. Rendering and keyboard navigation use display order. */
  index: number;
  /** Whether each ancestor has another sibling after this subtree. */
  branches: boolean[];
  last: boolean;
  /** All visible dependencies, including edges outside the display tree. */
  parents: string[];
  topicStart: boolean;
  topicEnd: boolean;
}

/**
 * Project a dependency graph onto a stable display forest. A change appears once,
 * under its first visible parent. Additional parents remain available to callers.
 * Missing parents never introduce rows or connect separate result pages/sections.
 * Cycles are broken only in the display tree, without discarding graph edges.
 */
export function threadChanges<T>(nodes: ThreadNode<T>[]): ThreadRow<T>[] {
  const byId = new Map(nodes.map((node, index) => [node.id, index]));
  const parents: (number | undefined)[] = nodes.map(() => undefined);
  const visibleParents = nodes.map(node =>
    [...new Set(node.parents)].filter(id => byId.has(id))
  );
  nodes.forEach((_, index) => {
    for (const id of visibleParents[index]) {
      const parent = byId.get(id)!;
      let ancestor: number | undefined = parent;
      while (ancestor !== undefined && ancestor !== index) {
        ancestor = parents[ancestor];
      }
      if (ancestor === index) continue;
      parents[index] = parent;
      break;
    }
  });
  const children = nodes.map(() => [] as number[]);
  const roots: number[] = [];
  parents.forEach((parent, index) => {
    if (parent === undefined) roots.push(index);
    else children[parent].push(index);
  });
  // Keep a stack near its earliest result, rather than moving a recently updated
  // child to wherever its older base happened to sort in the search response.
  const rank = nodes.map((_, index) => index);
  const rankTree = (index: number): number => {
    for (const child of children[index]) {
      rank[index] = Math.min(rank[index], rankTree(child));
    }
    return rank[index];
  };
  roots.forEach(rankTree);
  const orderSiblings = (indices: number[]) => {
    indices.sort((a, b) => rank[a] - rank[b]);
    const groups = new Map<string, number[]>();
    for (const index of indices) {
      const key = nodes[index].topic
        ? `topic:${nodes[index].topic}`
        : `row:${index}`;
      const group = groups.get(key) ?? [];
      group.push(index);
      groups.set(key, group);
    }
    return [...groups.values()].flat();
  };
  const rows: ThreadRow<T>[] = [];
  const visit = (index: number, branches: boolean[], last: boolean) => {
    rows.push({
      node: nodes[index],
      index,
      branches,
      last,
      parents: visibleParents[index],
      topicStart: false,
      topicEnd: false,
    });
    const ordered = orderSiblings(children[index]);
    ordered.forEach((child, position) =>
      visit(child, [...branches, !last], position === ordered.length - 1)
    );
  };
  orderSiblings(roots).forEach(index => visit(index, [], true));
  rows.forEach((row, index) => {
    const topic = row.node.topic;
    row.topicStart = !!topic && rows[index - 1]?.node.topic !== topic;
    row.topicEnd = !!topic && rows[index + 1]?.node.topic !== topic;
  });
  return rows;
}

/** Optional typed edges, compatible with change-network's dependent -> dependency direction. */
export interface ChangeThreadEdge {
  from: number;
  to: number;
  type: 'git' | 'depends-on';
}

export function threadChangeList(
  changes: ChangeInfo[],
  edges: ChangeThreadEdge[] = [],
  threaded = true
) {
  const key = (change: ChangeInfo) =>
    `${change.project}~${change.branch}~${change._number}`;
  const byNumber = new Map(
    changes.map(change => [change._number as number, key(change)])
  );
  return threadChanges(
    changes.map(change => {
      const revision =
        change.current_revision && change.revisions?.[change.current_revision];
      const parents = revision ? revision.parents_data ?? [] : [];
      const extra = edges.filter(edge => edge.from === change._number);
      // Prefer Git ancestry as the display parent when both kinds of edge exist.
      const numbers = [
        ...parents.map(parent => parent.change_number),
        ...extra.filter(edge => edge.type === 'git').map(edge => edge.to),
        ...extra
          .filter(edge => edge.type === 'depends-on')
          .map(edge => edge.to),
      ];
      return {
        id: key(change),
        value: change,
        topic: threaded ? change.topic : undefined,
        parents: (threaded ? numbers : []).flatMap(number => {
          const id = number === undefined ? undefined : byNumber.get(number);
          return id ? [id] : [];
        }),
      };
    })
  );
}

export function threadRelatedChanges(
  changes: RelatedChangeAndCommitInfo[],
  edges: ChangeThreadEdge[] = [],
  threaded = true
) {
  const current = new Map(
    changes
      .filter(
        change =>
          change._change_number !== undefined &&
          change._revision_number === change._current_revision_number
      )
      .map(change => [
        change._change_number as number,
        `${change.project}~${change.commit.commit}`,
      ])
  );
  return threadChanges(
    changes.map(change => {
      return {
        id: `${change.project}~${change.commit.commit}`,
        value: change,
        parents: threaded
          ? [
              ...change.commit.parents.map(
                parent => `${change.project}~${parent.commit}`
              ),
              ...edges
                .filter(
                  edge =>
                    current.get(edge.from) ===
                    `${change.project}~${change.commit.commit}`
                )
                .flatMap(edge => {
                  const id = current.get(edge.to);
                  return id ? [id] : [];
                }),
            ]
          : [],
      };
    })
  );
}
