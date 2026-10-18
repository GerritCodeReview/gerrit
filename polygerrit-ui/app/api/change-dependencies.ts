/**
 * @license
 * Copyright 2026 Monty Taylor
 * SPDX-License-Identifier: Apache-2.0
 */
import {ChangeInfo, NumericChangeId} from './rest-api';

export declare interface ChangeDependenciesPluginApi {
  /** Register once per plugin. Native Git dependencies are always retained. */
  register(provider: ChangeDependenciesProvider): void;
}

/** A displayed change's identity and revision, not a complete ChangeInfo. */
export type ChangeDependencyChange = Pick<
  ChangeInfo,
  '_number' | 'project' | 'branch'
> &
  Partial<Pick<ChangeInfo, 'current_revision' | 'current_revision_number'>>;

export declare interface ChangeDependenciesRequest {
  readonly context: 'change-list' | 'related-changes';
  readonly changes: ReadonlyArray<Readonly<ChangeDependencyChange>>;
  /** Aborted on navigation, changed inputs, disabled threading, or timeout. */
  readonly signal: AbortSignal;
}

export declare interface ChangeDependencyEdge {
  /** Edges point from the dependent change to its dependency. */
  from: NumericChangeId;
  to: NumericChangeId;
  type: 'git' | 'depends-on';
}

export declare interface ChangeDependenciesResponse {
  /** Only edges between requested changes are rendered. No rows are added. */
  edges: ChangeDependencyEdge[];
}

export declare interface ChangeDependenciesProvider {
  /** May be called again after reload even for the same change identities. */
  fetch(
    request: ChangeDependenciesRequest
  ): Promise<ChangeDependenciesResponse>;
}
