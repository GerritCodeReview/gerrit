/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {combineLatest, forkJoin, from, fromEvent, Observable, of} from 'rxjs';
import {
  filter,
  map,
  startWith,
  switchMap,
  tap,
  withLatestFrom,
} from 'rxjs/operators';
import {
  BranchName,
  ChangeInfo,
  NumericChangeId,
  RepoName,
  SubmitRequirementResultInfo,
  TopicName,
} from '../../api/rest-api';
import {NavigationService} from '../../elements/core/gr-navigation/gr-navigation';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {GerritView} from '../../services/router/router-model';
import {accountKey} from '../../utils/account-util';
import {select} from '../../utils/observable-util';
import {escapeAndWrapSearchOperatorValue} from '../../utils/string-util';
import {encodeURL, getBaseUrl} from '../../utils/url-util';
import {define, Provider} from '../dependency';
import {Model} from '../base/model';
import {UserModel} from '../user/user-model';
import {ViewState} from './base';
import {createChangeUrl} from './change';
import {listChangesOptionsToHex} from '../../utils/change-util';
import {ListChangesOption} from '../../types/common';

const USER_QUERY_PATTERN = /^owner:\s?("[^"]+"|[^ ]+)$/;

const REPO_QUERY_PATTERN =
  /^project:\s?("[^"]+"|[^ ]+)(\sstatus\s?:(open|"open"))?$/;

const LOOKUP_QUERY_PATTERNS: RegExp[] = [
  /^\s*i?[0-9a-f]{7,40}\s*$/i, // CHANGE_ID
  /^\s*[1-9][0-9]*\s*$/g, // CHANGE_NUM
  /[0-9a-f]{40}/, // COMMIT
];

export interface SearchViewState extends ViewState {
  view: GerritView.SEARCH;

  /**
   * The query for searching changes.
   *
   * Changing this to something non-empty will trigger search.
   */
  query: string;

  /**
   * How many initial search results should be skipped? This is for showing
   * more than one search result page. This must be a non-negative number.
   * If the string is not provided or cannot be parsed as expected, then the
   * offset falls back to 0.
   *
   * TODO: Consider converting from string to number before writing to the
   * state object.
   */
  offset?: string;

  /**
   * Is a search API call currrently in progress?
   */
  loading: boolean;

  /**
   * The search results for the current query.
   * `undefined` must be allowed here, because updating state with a partial
   * state without `changes` must be possible without overwriting existing
   * changes.
   * TODO: We should consider moving `changes` to a another model. This is not
   * really "view" state. View state must directly correlate to the URL.
   */
  changes?: ChangeInfo[];
}

export interface SearchUrlOptions {
  query?: string;
  offset?: number;
  repo?: RepoName;
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
    return `${getBaseUrl()}/q/${encodeURL(params.query)}${offsetExpr}`;
  }

  const operators: string[] = [];
  if (params.owner) {
    operators.push('owner:' + encodeURL(params.owner));
  }
  if (params.repo) {
    operators.push('project:' + encodeURL(params.repo));
  }
  if (params.branch) {
    operators.push('branch:' + encodeURL(params.branch));
  }
  if (params.topic) {
    operators.push(
      'topic:' + escapeAndWrapSearchOperatorValue(encodeURL(params.topic))
    );
  }
  if (params.hashtag) {
    operators.push(
      'hashtag:' +
        escapeAndWrapSearchOperatorValue(
          encodeURL(params.hashtag.toLowerCase())
        )
    );
  }
  if (params.statuses) {
    if (params.statuses.length === 1) {
      operators.push('status:' + encodeURL(params.statuses[0]));
    } else if (params.statuses.length > 1) {
      operators.push(
        '(' +
          params.statuses.map(s => `status:${encodeURL(s)}`).join(' OR ') +
          ')'
      );
    }
  }

  return `${getBaseUrl()}/q/${operators.join('+')}${offsetExpr}`;
}

export const searchViewModelToken =
  define<SearchViewModel>('search-view-model');

/**
 * This is the view model for the search page.
 *
 * It keeps track of the overall search view state and provides selectors for
 * subscribing to certain slices of the state.
 *
 * It manages loading the changes to be shown on the search page by providing
 * `changes` in its state. Changes to the view state or certain user preferences
 * will automatically trigger reloading the changes.
 */
export class SearchViewModel extends Model<SearchViewState | undefined> {
  public readonly query$ = select(this.state$, s => s?.query ?? '');

  private readonly offset$ = select(this.state$, s => s?.offset ?? '0');

  /**
   * Convenience selector for getting the `offset` as a number.
   *
   * TODO: Consider changing the type of `offset$` and `state.offset` to
   * `number`.
   */
  public readonly offsetNumber$ = select(this.offset$, offset => {
    const offsetNumber = Number(offset);
    return Number.isFinite(offsetNumber) ? offsetNumber : 0;
  });

  public readonly changes$ = select(this.state$, s => s?.changes ?? []);

  public readonly userId$ = select(
    combineLatest([this.query$, this.changes$]),
    ([query, changes]) => {
      if (changes.length === 0) return undefined;
      if (!USER_QUERY_PATTERN.test(query)) return undefined;
      const ownerKey = accountKey(changes[0].owner);
      if (changes.some(change => accountKey(change.owner) !== ownerKey)) {
        return undefined;
      }
      return ownerKey;
    }
  );

  public readonly repo$ = select(
    combineLatest([this.query$, this.changes$]),
    ([query, changes]) => {
      if (changes.length === 0) return undefined;
      if (!REPO_QUERY_PATTERN.test(query)) return undefined;
      return changes[0].project;
    }
  );

  public readonly loading$ = select(this.state$, s => s?.loading ?? false);

  // For usage in `combineLatest` we need `startWith` such that reload$ has an
  // initial value.
  private readonly reload$: Observable<unknown> = fromEvent(
    document,
    'reload'
  ).pipe(startWith(undefined));

  private readonly reloadChangesTrigger$;

  constructor(
    private readonly restApiService: RestApiService,
    private readonly userModel: UserModel,
    private readonly getNavigation: Provider<NavigationService>
  ) {
    super(undefined);

    this.reloadChangesTrigger$ = combineLatest([
      this.reload$,
      this.query$,
      this.offsetNumber$,
      this.userModel.preferenceChangesPerPage$,
    ]).pipe(
      map(([_reload, query, offsetNumber, changesPerPage]) => {
        const params: [string, number, number] = [
          query,
          offsetNumber,
          changesPerPage,
        ];
        return params;
      })
    );

    this.subscriptions = [
      this.reloadChangesTrigger$
        .pipe(
          switchMap(a => {
            const [changes, changesWithSubmitRequirements] =
              this.reloadChanges(a);
            return from(changes).pipe(
              map(
                fetchedChanges =>
                  [fetchedChanges || [], changesWithSubmitRequirements] as const
              )
            );
          }),
          switchMap(([changes, srPromise]) => {
            this.updateState({changes, loading: false});
            return forkJoin([of(changes), from(srPromise)]);
          }),
          tap(([changes, changesWithSubmitRequirements]) => {
            changes = this.populateSubmitRequirements(
              changes,
              changesWithSubmitRequirements
            );
            this.updateState({changes, loading: false});
          })
        )
        .subscribe(),
      this.changes$
        .pipe(
          filter(changes => changes.length === 1),
          withLatestFrom(this.query$)
        )
        .subscribe(([changes, query]) =>
          this.redirectSingleResult(query, changes)
        ),
    ];
  }

  private reloadChanges([query, offset, changesPerPage]: [
    string,
    number,
    number
  ]): [Promise<ChangeInfo[] | undefined>, Promise<ChangeInfo[] | undefined>] {
    if (this.getState() === undefined || query.trim().length === 0)
      return [Promise.resolve([]), Promise.resolve([])];
    this.updateState({loading: true});
    const changes = this.restApiService.getChanges(
      changesPerPage,
      query,
      offset
    );
    const changesWithSubmitRequirements = this.restApiService.getChanges(
      changesPerPage,
      query,
      offset,
      listChangesOptionsToHex(ListChangesOption.SUBMIT_REQUIREMENTS)
    );
    return [changes, changesWithSubmitRequirements];
  }

  private populateSubmitRequirements(
    changes: ChangeInfo[],
    changesWithSubmitRequirements: ChangeInfo[] | undefined
  ) {
    if (!changesWithSubmitRequirements) {
      return changes;
    }
    const sr = new Map<NumericChangeId, SubmitRequirementResultInfo[]>();
    for (const c of changesWithSubmitRequirements) {
      sr.set(c._number, c.submit_requirements!);
    }
    const updated_changes = [];
    for (const c of changes) {
      updated_changes.push({
        ...c,
        submit_requirements: sr.get(c._number) || [],
      });
    }
    return updated_changes;
  }

  // visible for testing
  redirectSingleResult(query: string, changes: ChangeInfo[]): void {
    if (changes.length !== 1) return;
    for (const queryPattern of LOOKUP_QUERY_PATTERNS) {
      if (query.match(queryPattern)) {
        // "Back"/"Forward" buttons work correctly only with replaceUrl()
        this.getNavigation().replaceUrl(createChangeUrl({change: changes[0]}));
        return;
      }
    }
  }
}
