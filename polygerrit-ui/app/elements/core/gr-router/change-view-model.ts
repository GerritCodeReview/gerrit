/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
  BasePatchSetNum,
  ChangeInfo,
  PARENT,
} from '../../../api/rest-api';
import {Model} from '../../../models/model';
import {UrlEncodedCommentId} from '../../../types/common';
import {select} from '../../../utils/observable-util';
import {PageContextWithQueryMap} from '../../../utils/page-wrapper-utils';
import {GerritView, ViewModel, ViewState} from '../../gr-app-types';
import {convertToPatchSetNum} from '../../../utils/patch-set-util';
import {getPatchRangeExpression} from '../../../utils/router-util';
import {encodeURL} from '../../../utils/url-util';

// The change and diff views are both managed by the same change view model and
// the same change model.
export enum ChildView {
  CHANGE = 'CHANGE',
  DIFF = 'DIFF',
}

export interface ChangeViewState extends ViewState {
  view: GerritView.CHANGE;
  childView: ChildView;
  changeNum: NumericChangeId;
  project: RepoName;
  edit?: boolean;
  patchNum?: RevisionPatchSetNum;
  basePatchNum?: BasePatchSetNum;
  commentId?: UrlEncodedCommentId;
  forceReload?: boolean;
  openReplyDialog?: boolean;

  // ChildView.CHANGE specific
  // TODO: Maybe create another interface for this?
  tab?: string;
  /** regular expression for filtering check runs */
  filter?: string;
  /** regular expression for selecting check runs */
  select?: string;
  /** selected attempt for selected check runs */
  attempt?: number;
  usp?: string;
  messageHash?: string;

  // ChildView.DIFF specific
  // TODO: Maybe create another interface for this?
  path?: string;
  lineNum?: number;
  leftSide?: boolean;
  commentLink?: boolean;
}

export function changeToState(
  change: Pick<ChangeInfo, '_number' | 'project'>
): ChangeViewState {
  return {
    view: GerritView.CHANGE,
    childView: ChildView.CHANGE,
    changeNum: change._number,
    project: change.project,
  };
}

/**
 * Pattern to recognize and parse the diff line locations as they appear in
 * the hash of diff URLs. In this format, a number on its own indicates that
 * line number in the revision of the diff. A number prefixed by either an 'a'
 * or a 'b' indicates that line number of the base of the diff.
 *
 * @type {RegExp}
 */
const LINE_ADDRESS_PATTERN = /^([ab]?)(\d+)$/;

function parseLineAddress(hash: string) {
  const match = hash.match(LINE_ADDRESS_PATTERN);
  if (!match) {
    return null;
  }
  return {
    leftSide: !!match[1],
    lineNum: Number(match[2]),
  };
}

function validatePatchNums(state: Partial<ChangeViewState>) {
  // We do not allow comparing a patchset against itself.
  if (state.patchNum && state.basePatchNum === state.patchNum) {
    state.basePatchNum = PARENT;
  }
  // Regexes of type a..b set basePatchNum instead of patchNum when only one is
  // specified.
  if (
    state.patchNum === undefined &&
    state.basePatchNum !== undefined &&
    state.basePatchNum !== PARENT
  ) {
    state.patchNum = state.basePatchNum as RevisionPatchSetNum;
    state.basePatchNum = PARENT;
  }
}

export class ChangeViewModel
  extends Model<ChangeViewState>
  implements ViewModel<ChangeViewState>
{
  view = GerritView.CHANGE;

  loginRequired = true;

  routes = [
    {
      name: 'change page',
      // Matches
      // /c/<project>/+/<changeNum>/[<basePatchNum|edit>..][<patchNum|edit>].
      // TODO(kaspern): Migrate completely to project based URLs, with backwards
      // compatibility for change-only.
      pattern: /^\/c\/(.+)\/\+\/(\d+)(\/?((-?\d+|edit)(\.\.(\d+|edit))?))?\/?$/,
      urlToState: (ctx: PageContextWithQueryMap) => {
        // Parameter order is based on the regex group number matched.
        const state: Partial<ChangeViewState> = {
          view: GerritView.CHANGE,
          childView: ChildView.CHANGE,
          project: ctx.params[0] as RepoName,
          changeNum: Number(ctx.params[1]) as NumericChangeId,
          basePatchNum: convertToPatchSetNum(ctx.params[4]) as BasePatchSetNum,
          patchNum: convertToPatchSetNum(ctx.params[6]) as RevisionPatchSetNum,
        };
        validatePatchNums(state);

        if (ctx.queryMap.has('forceReload')) {
          state.forceReload = true;
          // TODO: Implement this differently.
          // history.replaceState(
          //   null,
          //   '',
          //   location.href.replace(/[?&]forceReload=true/, '')
          // );
        }

        if (ctx.queryMap.has('openReplyDialog')) {
          state.openReplyDialog = true;
          // TODO: Implement this differently.
          // history.replaceState(
          //   null,
          //   '',
          //   location.href.replace(/[?&]openReplyDialog=true/, '')
          // );
        }

        const tab = ctx.queryMap.get('tab');
        if (tab) state.tab = tab;
        const filter = ctx.queryMap.get('filter');
        if (filter) state.filter = filter;
        const select = ctx.queryMap.get('select');
        if (select) state.select = select;
        const attempt = ctx.queryMap.get('attempt');
        if (attempt) {
          const attemptInt = parseInt(attempt);
          if (!isNaN(attemptInt) && attemptInt > 0) {
            state.attempt = attemptInt;
          }
        }

        // TODO: Add model subscribers to the reporting service for replacing
        // these two lines:
        // this.reporting.setRepoName(params.project);
        // this.reporting.setChangeId(changeNum);
        return state as ChangeViewState;
      },
    },
    {
      name: 'change page with comment id',
      // Matches /c/<project>/+/<changeNum>/comments/<commentId>/
      // Navigates to the commentId inside the Comments Tab
      pattern: /^\/c\/(.+)\/\+\/(\d+)\/comments(?:\/)?(\w+)?\/?$/,
      urlToState: (ctx: PageContextWithQueryMap) => {
        const state: Partial<ChangeViewState> = {
          view: GerritView.CHANGE,
          childView: ChildView.CHANGE,
          project: ctx.params[0] as RepoName,
          changeNum: Number(ctx.params[1]) as NumericChangeId,
          commentId: ctx.params[2] as UrlEncodedCommentId,
        };
        // TODO: Add model subscribers to the reporting service for replacing
        // these two lines:
        // this.reporting.setRepoName(params.project);
        // this.reporting.setChangeId(changeNum);
        return state as ChangeViewState;
      },
    },
    {
      name: 'change page in edit mode',
      // Matches /c/<project>/+/<changeNum>/[<patchNum|edit>],edit
      pattern: /^\/c\/(.+)\/\+\/(\d+)(\/(\d+))?,edit\/?$/,
      urlToState: (ctx: PageContextWithQueryMap) => {
        const state: Partial<ChangeViewState> = {
          view: GerritView.CHANGE,
          childView: ChildView.CHANGE,
          project: ctx.params[0] as RepoName,
          changeNum: Number(ctx.params[1]) as NumericChangeId,
          patchNum: convertToPatchSetNum(ctx.params[3]) as RevisionPatchSetNum,
          edit: true,
          tab: ctx.queryMap.get('tab') ?? '',
        };
        if (ctx.queryMap.has('forceReload')) {
          state.forceReload = true;
          // TODO: Implement this differently.
          // history.replaceState(
          //   null,
          //   '',
          //   location.href.replace(/[?&]forceReload=true/, '')
          // );
        }
        // TODO: Add model subscribers to the reporting service for replacing
        // these two lines:
        // this.reporting.setRepoName(params.project);
        // this.reporting.setChangeId(changeNum);
        return state as ChangeViewState;
      },
    },
    {
      name: 'diff page',
      // Matches
      // /c/<project>/+/<changeNum>/[<basePatchNum|edit>..]<patchNum|edit>/<path>.
      // TODO(kaspern): Migrate completely to project based URLs, with backwards
      // compatibility for change-only.
      // eslint-disable-next-line max-len
      pattern:
        /^\/c\/(.+)\/\+\/(\d+)(\/((-?\d+|edit)(\.\.(\d+|edit))?(\/(.+))))\/?$/,
      urlToState: (ctx: PageContextWithQueryMap) => {
        console.log(`diff page route ${JSON.stringify(ctx.params)}`);
        const state: Partial<ChangeViewState> = {
          view: GerritView.CHANGE,
          childView: ChildView.DIFF,
          project: ctx.params[0] as RepoName,
          changeNum: Number(ctx.params[1]) as NumericChangeId,
          basePatchNum: convertToPatchSetNum(ctx.params[4]) as BasePatchSetNum,
          patchNum: convertToPatchSetNum(ctx.params[6]) as RevisionPatchSetNum,
          path: ctx.params[8],
        };
        validatePatchNums(state);

        const address = parseLineAddress(ctx.hash);
        if (address) {
          state.leftSide = address.leftSide;
          state.lineNum = address.lineNum;
        }

        return state as ChangeViewState;
      },
    },
    {
      name: 'diff page with comment id',
      // Matches /c/<project>/+/<changeNum>/comment/<commentId>/
      // Navigates to the diff view
      // This route is needed to resolve to patchNum vs latestPatchNum used in the
      // links generated in the emails.
      pattern: /^\/c\/(.+)\/\+\/(\d+)\/comment\/(\w+)\/?$/,
      urlToState: (ctx: PageContextWithQueryMap) => {
        const state: Partial<ChangeViewState> = {
          view: GerritView.CHANGE,
          childView: ChildView.DIFF,
          project: ctx.params[0] as RepoName,
          changeNum: Number(ctx.params[1]) as NumericChangeId,
          commentId: ctx.params[2] as UrlEncodedCommentId,
          commentLink: true,
        };

        return state as ChangeViewState;
      },
    },
  ];

  constructor() {
    super({
      view: GerritView.CHANGE,
      childView: ChildView.CHANGE,
      // TODO: I am not really happy with these defaults, but the base class
      // wants an initial state, so what choice do we have?
      changeNum: 0 as NumericChangeId,
      project: 'unknown' as RepoName,
    });
  }

  public readonly childView$ = select(this.state$, s => s.childView);

  public readonly project$ = select(this.state$, s => s.project);

  public readonly changeNum$ = select(this.state$, s => s.changeNum);

  public readonly patchNum$ = select(this.state$, s => s.patchNum);

  public readonly basePatchNum$ = select(this.state$, s => s.basePatchNum);

  updateState(state: ChangeViewState) {
    this.subject$.next({...state});
  }

  stateToUrl = (params: Partial<ChangeViewState>) => {
    if (!params.view) params.view = GerritView.CHANGE;
    if (!params.childView) {
      // Note that DIFF URLs for comments do not have a path, so the caller must
      // set the child view correctly.
      params.childView = params.path ? ChildView.DIFF : ChildView.CHANGE;
    }
    let suffix = '';
    const range = getPatchRangeExpression(params);
    if (range.length) {
      suffix += `/${range}`;
    }
    if (params.path) {
      suffix += `/${encodeURL(params.path, true)}`;
    }
    const queries = [];
    if (params.forceReload) {
      queries.push('forceReload=true');
    }
    if (params.openReplyDialog) {
      queries.push('openReplyDialog=true');
    }
    if (params.usp) {
      queries.push(`usp=${params.usp}`);
    }
    if (params.edit) {
      suffix += ',edit';
    }
    if (params.commentId) {
      if (params.childView === ChildView.DIFF) {
        suffix = `/comment/${params.commentId}` + suffix;
      }
      if (params.childView === ChildView.CHANGE) {
        suffix = suffix + `/comments/${params.commentId}`;
      }
    }
    if (queries.length > 0) {
      suffix += '?' + queries.join('&');
    }
    if (params.lineNum) {
      suffix += `#b${params.lineNum}`;
    }
    if (params.messageHash) {
      suffix += params.messageHash;
    }
    if (params.project) {
      const encodedProject = encodeURL(params.project, true);
      return `/c/${encodedProject}/+/${params.changeNum}${suffix}`;
    } else {
      return `/c/${params.changeNum}${suffix}`;
    }
  };
}
