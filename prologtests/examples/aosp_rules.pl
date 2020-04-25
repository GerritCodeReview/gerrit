% A simplified and mocked AOSP rules.pl

%%%%% wrapper functions for unit tests

change_branch(X) :- gerrit:change_branch(X).
change_project(X) :- gerrit:change_project(X).
commit_author(U,N,M) :- gerrit:commit_author(U,N,M).
commit_delta(X) :- gerrit:commit_delta(X).
commit_delta(X,T,P) :- gerrit:commit_delta(X, T, P).
commit_label(L,U) :- gerrit:commit_label(L,U).
uploader(X) :- gerrit:uploader(X).

%%%%% true/false conditions

% Special auto-merger accounts.
is_exempt_uploader :-
  uploader(user(Id)),
  memberchk(Id, [104, 106]).

% Build cop overrides everything.
has_build_cop_override :-
  commit_label(label('Build-Cop-Override', 1), _).

is_exempt_from_reviews :-
  or(is_exempt_uploader, has_build_cop_override).

% api_review_not_required(ProjectName, AllowedRegex)
% These are exceptions to the api review rule
api_review_not_required(_, '(^|/)(xsd|xml)/'). % xsd/xml dirs in any project
api_review_not_required(_, 'CMakeLists\\.txt'). % CMakeLists and jarjar-rules in any project
api_review_not_required('device/generic/vulkan-cereal', ''). % No java APIs here
api_review_not_required('platform/external/qemu', ''). % No java APIs here
api_review_not_required('platform/hardware/interfaces', ''). % No java APIs here
api_review_not_required('platform/frameworks/av', '/xmlparser/'). % xsd here
api_review_not_required('platform/prebuilts/go/linux-x86', ''). % No java APIs here
api_review_not_required('platform/system/tools/xsdc', ''). % xsdc tool + its tests
api_review_not_required('platform/tools/base', 'build-system/').

% Changes needing api review
needs_api_review :-
  commit_delta('^(.*/)?api/.*\\.txt$', _, Path),
  change_project(Project),
  \+ (api_review_not_required(Project, R), regex_matches(R, Path)).

% Some branches need DrNo review.
needs_drno_review :-
  change_branch(Branch),
  memberchk(Branch, [
    'refs/heads/my-alpha-dev',
    'refs/heads/my-beta-dev'
  ]).

% Some author email addresses need Qualcomm-Review.
needs_qualcomm_review :-
  commit_author(_, _, M),
  regex_matches(
'.*@(qti.qualcomm.com|qca.qualcomm.com|quicinc.com|qualcomm.com)', M).

% Special projects, branches, user accounts
% can opt out owners review.
opt_out_find_owners :-
  change_branch(Branch),
  memberchk(Branch, [
    'refs/heads/my-beta-testing',
    'refs/heads/my-testing'
  ]).

% Special projects, branches, user accounts
% can opt in owners review.
% Note that opt_out overrides opt_in.
opt_in_find_owners :- true.


%%%%% Simple list filters.

remove_label(X, In, Out) :-
  gerrit:remove_label(In, label(X, _), Out).

% Slow but simple for short input list.
remove_review_categories(In, Out) :-
  remove_label('API-Review', In, L1),
  remove_label('Code-Review', L1, L2),
  remove_label('DrNo-Review', L2, L3),
  remove_label('Owner-Review-Vote', L3, L4),
  remove_label('Qualcomm-Review', L4, L5),
  remove_label('Verified', L5, Out).


%%%%% Missing rules in Gerrit Prolog Cafe.

or(InA, InB) :- once((A;B)).

not(Goal) :- Goal -> false ; true.

% memberchk(+Element, +List)
memberchk(X, [H|T]) :-
  (X = H -> true ; memberchk(X, T)).

maplist(Functor, In, Out) :-
  (In = []
  -> Out = []
  ;  (In = [X1|T1],
      Out = [X2|T2],
      Goal =.. [Functor, X1, X2],
      once(Goal),
      maplist(Functor, T1, T2)
     )
  ).


%%%%% Conditional rules and filters.

submit_filter(In, Out) :-
  (is_exempt_from_reviews
  -> remove_review_categories(In, Out)
  ;  (check_review(needs_api_review,
          'API_Review', In, L1),
      check_review(needs_drno_review,
          'DrNo-Review', L1, L2),
      check_review(needs_qualcomm_review,
          'Qualcomm-Review', L2, L3),
      check_find_owners(L3, Out)
     )
  ).

check_review(NeedReview, Label, In, Out) :-
  (NeedReview
  -> Out = In
  ;  remove_label(Label, In, Out)
  ).

% If opt_out_find_owners is true,
% remove all 'Owner-Review-Vote' label;
% else if opt_in_find_owners is true,
%      call find_owners:submit_filter;
% else default to no find_owners filter.
check_find_owners(In, Out) :-
  (opt_out_find_owners
  -> remove_label('Owner-Review-Vote', In, Temp)
  ; (opt_in_find_owners
    -> find_owners:submit_filter(In, Temp)
    ; In = Temp
    )
  ),
  Temp =.. [submit | L1],
  remove_label('Owner-Approved', L1, L2),
  maplist(owner_may_to_need, L2, L3),
  Out =.. [submit | L3].

% change may(_) to need(_) to block submit.
owner_may_to_need(In, Out) :-
  (In = label('Owner-Review-Vote', may(_))
  -> Out = label('Owner-Review-Vote', need(_))
  ;  Out = In
  ).
