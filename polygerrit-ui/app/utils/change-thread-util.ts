/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeInfo, RelatedChangeAndCommitInfo} from '../types/common';
import {ChangeStatus} from '../constants/constants';

export interface ThreadNode<T> {
  id: string;
  value: T;
  parents: string[];
}

export interface ThreadRow<T> {
  node: ThreadNode<T>;
  /** Original result index. Rendering and keyboard navigation use display order. */
  index: number;
  /** Whether each ancestor has another sibling after this subtree. */
  branches: boolean[];
  /** Shared depth limit keeps compressed rail columns aligned across rows. */
  maxDepth?: number;
  last: boolean;
  /** All visible dependencies, including edges outside the display tree. */
  parents: string[];
}

/**
 * Project dependencies onto a stable base-first forest without adding rows.
 * Missing parents do not connect separate result pages or dashboard sections.
 * Cycles are broken only for display, retaining all original graph edges.
 */
export function threadChanges<T>(nodes: ThreadNode<T>[]): ThreadRow<T>[] {
  const rows = threadForest(nodes);
  const maxDepth = Math.max(0, ...rows.map(row => row.branches.length));
  return rows.map(row => {
    return {...row, maxDepth};
  });
}

function threadForest<T>(nodes: ThreadNode<T>[]): ThreadRow<T>[] {
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
    return indices;
  };
  const rows: ThreadRow<T>[] = [];
  const visit = (index: number, branches: boolean[], last: boolean) => {
    rows.push({
      node: nodes[index],
      index,
      branches,
      last,
      parents: visibleParents[index],
    });
    const ordered = orderSiblings(children[index]);
    ordered.forEach((child, position) =>
      visit(child, [...branches, !last], position === ordered.length - 1)
    );
  };
  orderSiblings(roots).forEach(index => visit(index, [], true));
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
    changes
      .filter(change => change.status !== ChangeStatus.MERGED)
      .map(change => [change._number as number, key(change)])
  );
  return threadChanges(
    changes.map(change => {
      const indexed = change.indexed_dependencies;
      const parents =
        indexed &&
        (!change.current_revision ||
          indexed.revision === change.current_revision)
          ? indexed.parents
          : [];
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
        parents: (threaded && change.status !== ChangeStatus.MERGED
          ? numbers
          : []
        ).flatMap(number => {
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
  const merged = new Set(
    changes
      .filter(change => change.status === ChangeStatus.MERGED)
      .map(change => `${change.project}~${change.commit.commit}`)
  );
  const current = new Map(
    changes
      .filter(
        change =>
          change.status !== ChangeStatus.MERGED &&
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
        parents: threaded && change.status !== ChangeStatus.MERGED
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
            ].filter(id => !merged.has(id))
          : [],
      };
    })
  );
}
