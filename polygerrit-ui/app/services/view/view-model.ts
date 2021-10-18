import { BehaviorSubject, Observable } from "rxjs";
import { DiffViewMode } from "../../api/diff";

interface ViewState {
  // If the user screen width is too low then we want to set the diffMode to
  // Unified
  isScreenTooSmall: boolean;
  diffViewMode?: DiffViewMode;
  // If user switches the mode via the diff mode selector, then we want to
  // retain this until a new change is loaded
  temporaryDiffMode?: DiffViewMode;
}

const initialState: ViewState = {
  isScreenTooSmall: false,
};

const privateState$ = new BehaviorSubject(initialState);

export const viewState$: Observable<ViewState> = privateState$;

export function updateState(isScreenTooSmall: boolean) {
  privateState$.next({...privateState$.getValue(), isScreenTooSmall});
}

/**
  * _getDiffViewMode: Get the diff view (side-by-side or unified) based on
  * the current state.
  *
  * The expected behavior is to use the mode specified in the user's
  * preferences unless they have manually chosen the alternative view or they
  * are on a mobile device. If the user navigates up to the change view, it
  * should clear this choice and revert to the preference the next time a
  * diff is viewed.
  *
  * Use side-by-side if the user is not logged in.
  */
export function getDiffViewState() {
  const current = privateState$.getValue();
  const isScreenTooSmall = current.isScreenTooSmall;
  if (isScreenTooSmall) return DiffViewMode.UNIFIED;
  if (current.temporaryDiffMode) return current.temporaryDiffMode
  return current.diffViewMode ?? DiffViewMode.SIDE_BY_SIDE;
}

export function isScreenTooSmall() {
  return  privateState$.getValue().isScreenTooSmall;
}


