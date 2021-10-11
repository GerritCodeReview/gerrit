% This file can be replaced with the following submit requirements
%
% 1) Code-Review
%   applicability_expr =
%   submittability_expr = label:Code-Review=MAX
%   			 AND -label:Code-Review=MIN
%   override_expr =
%
% 2) Code-Style (not applicable for the refs/meta/config branch)
%   applicability_expr = -branch:refs/meta/config
%   submittability_expr = label:Code-Style=MAX
%   			 AND -label:Code-Style=MIN
%   override_expr =
%
% 3) Verified (not applicable for the refs/meta/config branch)
%   applicability_expr = -branch:refs/meta/config
%   submittability_expr = label:Verified=MAX
%   			 AND -label:Verified=MIN
%   override_expr =
%
% 4) Library-Compliance
%   applicability_expr = -branch:refs/meta/config AND (file:^lib/ OR
%   			 file:^WORKSPACE$ OR file:^.gitmodules$)
%   submittability_expr = label:Library-Compliance=MAX
%   			 AND -label:Library-Compliance=MIN
%   override_expr =
%

submit_rule(submit(CR)) :-
  gerrit:change_branch('refs/meta/config'),
  !,
  gerrit:max_with_block(-2, 2, 'Code-Review', CR).

submit_rule(submit(CR, CS, V, L)) :-
  needs_library_compliance,
  !,
  base(CR, CS, V),
  gerrit:max_with_block(-1, 1, 'Library-Compliance', L).

submit_rule(submit(CR, CS, V)) :-
  base(CR, CS, V).

base(CR, CS, V) :-
  gerrit:max_with_block(-2, 2, 'Code-Review', CR),
  gerrit:max_with_block(-1, 1, 'Code-Style', CS),
  gerrit:max_with_block(-1, 1, 'Verified', V).

needs_library_compliance :- gerrit:commit_delta('^lib/'), !.
needs_library_compliance :- gerrit:commit_delta('^WORKSPACE$'), !.
needs_library_compliance :- gerrit:commit_delta('^.gitmodules$'), !.
