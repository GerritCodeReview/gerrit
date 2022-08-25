/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {SubmitRequirementExpressionInfo} from '../api/rest-api';
import {Execution} from '../constants/reporting';
import {getAppContext} from '../services/app-context';

export enum SubmitRequirementExpressionAtomStatus {
  UNKNOWN = 'UNKNOWN',
  PASSING = 'PASSING',
  FAILING = 'FAILING',
}

export interface SubmitRequirementExpressionPart {
  value: string;
  isAtom: boolean;
  // Defined iff isAtom is true.
  atomStatus?: SubmitRequirementExpressionAtomStatus;
}

interface AtomMatch {
  start: number;
  end: number;
  isPassing: boolean;
}

function appendAllOccurences(
  text: string,
  match: string,
  isPassing: boolean,
  matchedAtoms: AtomMatch[]
) {
  for (let searchStartIndex = 0; ; ) {
    let index = text.indexOf(match, searchStartIndex);
    if (index === -1) {
      break;
    }
    searchStartIndex = index + match.length;
    // Include unary minus.
    if (index !== 0 && text[index - 1] === '-') {
      --index;
      isPassing = !isPassing;
    }
    matchedAtoms.push({start: index, end: searchStartIndex, isPassing});
  }
}

/**
 * Returns expression string split into ExpressionPart.
 *
 * Concatenation result of all parts is equal to original expression string.
 *
 * Unary minus is included in the atom and is accounted in the status.
 */
export function atomizeExpression(
  expression: SubmitRequirementExpressionInfo
): SubmitRequirementExpressionPart[] {
  const matchedAtoms: AtomMatch[] = [];
  expression.passing_atoms?.forEach(atom =>
    appendAllOccurences(
      expression.expression,
      atom,
      /* isPassing=*/ true,
      matchedAtoms
    )
  );
  expression.failing_atoms?.forEach(atom =>
    appendAllOccurences(
      expression.expression,
      atom,
      /* isPassing=*/ false,
      matchedAtoms
    )
  );
  matchedAtoms.sort((a, b) => a.start - b.start);

  const result: SubmitRequirementExpressionPart[] = [];
  let currentIndex = 0;
  for (const {start, end, isPassing} of matchedAtoms) {
    if (start < currentIndex) {
      getAppContext().reportingService.reportExecution(
        Execution.REACHABLE_CODE,
        'Overlapping atom matches in submit requirement expression.'
      );
      continue;
    }
    if (start > currentIndex) {
      result.push({
        value: expression.expression.slice(currentIndex, start),
        isAtom: false,
      });
    }
    result.push({
      value: expression.expression.slice(start, end),
      isAtom: true,
      atomStatus: isPassing
        ? SubmitRequirementExpressionAtomStatus.PASSING
        : SubmitRequirementExpressionAtomStatus.FAILING,
    });
    currentIndex = end;
  }
  if (currentIndex < expression.expression.length) {
    result.push({
      value: expression.expression.slice(currentIndex),
      isAtom: false,
    });
  }
  return result;
}
