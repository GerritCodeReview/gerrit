% This file can be replaced with the following submit requirements
%
% 1) Code-Style (not applicable for the refs/meta/config branch)
%   applicability_expr = -branch:refs/meta/config
%   submittability_expr = label:Code-Style=MAX
%   		     AND -label:Code-Style=MIN
%   override_expr =
%
% 2) Verified (not applicable for the refs/meta/config branch)
%   applicability_expr = -branch:refs/meta/config
%   submittability_expr = label:Verified=MAX
%   		     AND -label:Verified=MIN
%   override_expr =
%
% 3) Library-Compliance
%   applicability_expr = -branch:refs/meta/config AND (file:^lib/ OR
%   			 file:^WORKSPACE$ OR file:^.gitmodules$)
%   submittability_expr = label:Library-Compliance=MAX
%   		    AND  -label:Library-Compliance=MIN
%   override_expr =
%

submit_rule(submit(CS, V, L)) :-
  needs_library_compliance,
  !,
  base(CS, V),
  gerrit:max_with_block(-1, 1, 'Library-Compliance', L).

submit_rule(submit(CS, V)) :-
  base(CS, V).

base(CS, V) :-
  gerrit:max_with_block(-1, 1, 'Code-Style', CS),
  gerrit:max_with_block(-1, 1, 'Verified', V).

needs_library_compliance :- gerrit:commit_delta('^lib/'), !.
needs_library_compliance :- gerrit:commit_delta('^WORKSPACE$'), !.
needs_library_compliance :- gerrit:commit_delta('^.gitmodules$'), !.
