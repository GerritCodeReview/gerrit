import {BehaviorSubject, Observable} from 'rxjs';
import {DiffViewMode} from '../../api/diff';
import { distinctUntilChanged, map } from 'rxjs/operators';
import { getDiffViewMode } from '../user/user-model';

interface ViewState {
  // If the user screen width is too low then we want to set the diffMode to
  // Unified
  isScreenTooSmall: boolean;
}

const initialState: ViewState = {
  isScreenTooSmall: false,
};

const privateState$ = new BehaviorSubject(initialState);

export const viewState$: Observable<ViewState> = privateState$;

export function updateState(isScreenTooSmall: boolean) {
  privateState$.next({...privateState$.getValue(), isScreenTooSmall});
}

export const isScreenTooSmall$ = viewState$.pipe(
  map(state => state.isScreenTooSmall),
  distinctUntilChanged()
);


/**
 * _getDiffViewMode: Get the diff view (side-by-side or unified) based on
 * the current state and user preferences.
 *
 * The expected behavior is to use the mode specified in the user's
 * preferences or unified mode if their screen width is too small.
 *
 * Use side-by-side if the user is not logged in.
 */
export function getDiffViewState() {
  // get diff mode from user preference
  const current = privateState$.getValue();
  const isScreenTooSmall = current.isScreenTooSmall;
  if (isScreenTooSmall) return DiffViewMode.UNIFIED;
  return getDiffViewMode();
}

export function isScreenTooSmall() {
  return privateState$.getValue().isScreenTooSmall;
}
