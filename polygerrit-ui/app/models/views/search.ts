/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RepoName, BranchName, TopicName} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import {addQuotesWhen} from '../../utils/string-util';
import {encodeURL, getBaseUrl} from '../../utils/url-util';
import {define} from '../dependency';
import {Model} from '../model';
import {ViewState} from './base';

export interface SearchViewState extends ViewState {
  view: GerritView.SEARCH;
  query?: string;
  offset?: string;
}

export interface SearchUrlOptions {
  query?: string;
  offset?: number;
  project?: RepoName;
  branch?: BranchName;
  topic?: TopicName;
  statuses?: string[];
  hashtag?: string;
  owner?: string;
}

export function createSearchUrl(params: SearchUrlOptions): string {
  let offsetExpr = '';
  if (params.offset && params.offset > 0) {
    offsetExpr = `,${params.offset}`;
  }

  if (params.query) {
    return `${getBaseUrl()}/q/${encodeURL(params.query, true)}${offsetExpr}`;
  }

  const operators: string[] = [];
  if (params.owner) {
    operators.push('owner:' + encodeURL(params.owner, false));
  }
  if (params.project) {
    operators.push('project:' + encodeURL(params.project, false));
  }
  if (params.branch) {
    operators.push('branch:' + encodeURL(params.branch, false));
  }
  if (params.topic) {
    operators.push(
      'topic:' +
        addQuotesWhen(
          encodeURL(params.topic, false),
          /[\s:]/.test(params.topic)
        )
    );
  }
  if (params.hashtag) {
    operators.push(
      'hashtag:' +
        addQuotesWhen(
          encodeURL(params.hashtag.toLowerCase(), false),
          /[\s:]/.test(params.hashtag)
        )
    );
  }
  if (params.statuses) {
    if (params.statuses.length === 1) {
      operators.push('status:' + encodeURL(params.statuses[0], false));
    } else if (params.statuses.length > 1) {
      operators.push(
        '(' +
          params.statuses
            .map(s => `status:${encodeURL(s, false)}`)
            .join(' OR ') +
          ')'
      );
    }
  }

  return `${getBaseUrl()}/q/${operators.join('+')}${offsetExpr}`;
}

export const searchViewModelToken =
  define<SearchViewModel>('search-view-model');

export class SearchViewModel extends Model<SearchViewState | undefined> {
  constructor() {
    super(undefined);
  }
}
