submit_rule(submit(CR)) :-
  gerrit:change_branch('refs/meta/config'),
  !,
  gerrit:max_with_block(-2, 2, 'Code-Review', CR).

submit_rule(submit(CR, CS, V, L, RN)) :-
  needs_library_compliance,
  !,
  base(CR, CS, V, RN),
  gerrit:max_with_block(-1, 1, 'Library-Compliance', L).

submit_rule(submit(CR, CS, V, RN)) :-
  base(CR, CS, V, RN).

base(CR, CS, V, RN) :-
  release_notes(RN),
  gerrit:max_with_block(-2, 2, 'Code-Review', CR),
  gerrit:max_with_block(-1, 1, 'Code-Style', CS),
  gerrit:max_with_block(-1, 1, 'Verified', V).

% Require a 'Release-Notes' footer.
release_notes(RN) :-
  gerrit:commit_message_matches('^Release-Notes:.*'),
  !,
  gerrit:commit_author(A),
  RN = label(‘Contains-Release-Notes-Footer', ok(A)).

release_notes(RN) :-
  RN = label('Contains-Release-Notes-Footer', need(_)).

needs_library_compliance :- gerrit:commit_delta('^lib/'), !.
needs_library_compliance :- gerrit:commit_delta('^WORKSPACE$'), !.
needs_library_compliance :- gerrit:commit_delta('^.gitmodules$'), !.
