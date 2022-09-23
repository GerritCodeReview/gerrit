/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {combineLatest, fromEvent, Observable} from 'rxjs';
import {
  debounceTime,
  filter,
  map,
  startWith,
  switchMap,
  tap,
} from 'rxjs/operators';
import {RepoName, BranchName, TopicName, ChangeInfo} from '../../api/rest-api';
import {NavigationService} from '../../elements/core/gr-navigation/gr-navigation';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {GerritView} from '../../services/router/router-model';
import {select} from '../../utils/observable-util';
import {addQuotesWhen} from '../../utils/string-util';
import {encodeURL} from '../../utils/url-util';
import {define} from '../dependency';
import {Model} from '../model';
import {UserModel} from '../user/user-model';
import {ViewState} from './base';
import {createChangeUrl} from './change';

const LOOKUP_QUERY_PATTERNS: RegExp[] = [
  /^\s*i?[0-9a-f]{7,40}\s*$/i, // CHANGE_ID
  /^\s*[1-9][0-9]*\s*$/g, // CHANGE_NUM
  /[0-9a-f]{40}/, // COMMIT
];

export interface SearchViewState extends ViewState {
  view: GerritView.SEARCH;
  query: string;
  offset?: string;

  loading: boolean;
  changes: ChangeInfo[];
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
    return '/q/' + encodeURL(params.query, true) + offsetExpr;
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

  return '/q/' + operators.join('+') + offsetExpr;
}

export const searchViewModelToken =
  define<SearchViewModel>('search-view-model');

export class SearchViewModel extends Model<SearchViewState | undefined> {
  public readonly query$ = select(this.state$, s => s?.query ?? '');

  private readonly offset$ = select(this.state$, s => s?.offset ?? '0');

  public readonly offsetNumber$ = select(this.offset$, offset => {
    const offsetNumber = Number(offset);
    return Number.isFinite(offsetNumber) ? offsetNumber : 0;
  });

  public readonly changes$ = select(this.state$, s => s?.changes ?? []);

  public readonly loading$ = select(this.state$, s => s?.loading ?? false);

  // For usage in `combineLatest` we need `startWith` such that reload$ has an
  // initial value.
  private readonly reload$: Observable<unknown> = fromEvent(
    document,
    'reload'
  ).pipe(startWith(undefined));

  private readonly reloadChangesTrigger$ = combineLatest([
    this.reload$,
    this.query$,
    this.offsetNumber$,
    this.userModel.preferenceChangesPerPage$,
  ]).pipe(
    filter(
      ([_reload, _query, _offsetNumber, changesPerPage]) =>
        changesPerPage !== undefined
    ),
    map(([_reload, query, offsetNumber, changesPerPage]) => {
      const params: [string, number, number] = [
        query ?? '',
        offsetNumber,
        changesPerPage,
      ];
      return params;
    }),
    // This is useful mostly for not using the initial default user preferences,
    // but waiting for the actual prefs being set. Thus avoiding to load twice.
    debounceTime(1)
  );

  constructor(
    private readonly restApiService: RestApiService,
    private readonly userModel: UserModel
  ) {
    super(undefined);
    this.subscriptions = [
      this.reloadChangesTrigger$
        .pipe(
          switchMap(a => this.reloadChanges(a)),
          tap(changes => this.updateState({changes, loading: false}))
        )
        .subscribe(),
    ];
  }

  // Needs to be initialized later to avoid circular dep with router.
  public navigation: NavigationService = {
    setUrl(_url: string): void {},
    replaceUrl(_url: string): void {},
  };

  private async reloadChanges([query, offset, changesPerPage]: [
    string,
    number,
    number
  ]) {
    if (this.getState() === undefined) return [];
    if (query.trim().length === 0) return [];
    this.updateState({loading: true});
    const changes = await this.restApiService.getChanges(
      changesPerPage,
      query,
      offset
    );
    if (this.redirectSingleResult(query, changes ?? [])) return [];
    return changes ?? [];
  }

  // visible for testing
  redirectSingleResult(query: string, changes: ChangeInfo[]): boolean {
    if (changes.length !== 1) return false;
    for (const queryPattern of LOOKUP_QUERY_PATTERNS) {
      if (query.match(queryPattern)) {
        // "Back"/"Forward" buttons work correctly only with replaceUrl()
        this.navigation.replaceUrl(createChangeUrl({change: changes[0]}));
        return true;
      }
    }
    return false;
  }
}
