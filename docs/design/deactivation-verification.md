# Deactivation build evidence

This is agent execution evidence and an independent agent review, not a statement that the student personally reviewed the code. Build is implemented; Verify is **Partial**. Design gate: merged PR #2, `08045da`. No database schema or Actions changes.

## Executed checks

| Check | Observed result |
|---|---|
| Backend runnable suite: `./mvnw test -DexcludedGroups=integration '-Dtest=*Test,!WebConfigSpaForwardingTest'` | 190 invocations passed. Local sandbox required the Mockito core Java agent; no POM workaround committed. |
| Full backend `./mvnw test` | 216 invocations, zero assertion failures, 26 initialization errors: Docker/Testcontainers unavailable. This is **not** a passing full suite. |
| Validator trunk | 46 invocations passed; inspected before leaves; fresh independent trunk review passed. |
| `node --experimental-strip-types --test tests/evaluationRows.test.mjs` | Six tests passed on Node 24. |
| `npm run build-only` | Vite production bundle built. |
| `npm run type-check` | Fails with exactly the same 18 diagnostics observed on the untouched base. No diagnostic disabled; full `npm run build` is consequently not claimed green. |
| New Cypress files | Syntax checked only. Cypress binary download returned an unavailable HTML response; browser specs unexecuted. A fallback browser also could not launch under runtime restrictions. |

Reproduction on Windows: start Docker Desktop, then run `backend\mvnw.cmd test`; from frontend run Node tests, `npm run type-check`, `npm run build-only`, then the two new Cypress specs with Vite running. API responses in those Cypress specs are stubbed: they test UI contracts, not backend authorization. The warning spec writes a screenshot when actually run; no screenshot is claimed captured here.

## Mutation evidence

Agent selected `EvaluationEligibilityTest.refusesInactiveUpdateBeforeCommentsOrRatingsChange` because its preservation assertions initially used empty ratings. Rewrote the fixture to contain criterion score **8**, supplied a valid replacement score **2**, and asserted original score, total, both comments and target identity remain unchanged.

Changed exactly one production condition in `EvaluationService.requireActiveEvaluatee`: `if (!evaluatee.isEnabled())` → `if (false && !evaluatee.isEnabled())`. Ran `./mvnw test -Dtest=EvaluationEligibilityTest#refusesInactiveUpdateBeforeCommentsOrRatingsChange`: **one failure**, “Expecting code to raise a throwable.” Restored production and reran the identical selector: **one test passed**. The mutation is absent from committed production. An earlier empty-update fixture failed for a criteria mismatch rather than a successful bad write; it was strengthened before the final mutation run.

## Design test list: actual coverage

| Design row | Evidence | Limit |
|---|---|---|
| authorized-deactivation | StudentLifecycleServiceTest; StudentLifecycleSecurityTest | MySQL preservation/filter-chain tests exist but unrun; activity/evaluation count assertion is weaker than reload-and-compare contents. |
| no-team | StudentLifecycleServiceTest.supportsStudentWithoutTeam | Unit passed. |
| repeat | StudentLifecycleServiceTest.deactivatesOnlyEnabledAndRepeatsWithoutSaving | Integration all-inactive case exists, unrun. |
| forbidden (2a) | StudentLifecycleSecurityTest denies student/unrelated admin | Real filter-chain instructor/foreign-admin/repeat assertions exist, unrun. |
| missing-auth / unresolved-target | StudentDeactivationIntegrationTest | Compiled, unrun. |
| warning | deactivation.spec.cy.js named warning + screenshot command | Unrun; screenshot pending. |
| cancel (5a) | Cypress Cancel and Escape zero-PATCH assertions | Unrun; backdrop/close-icon assertions absent. |
| confirm | Cypress one PATCH/pending guard/refreshed status/failure/repeat | Unrun. |
| profile-bypass | StudentLifecycleServiceTest refuses before name/email/account mutation | Integration exists, unrun; read-only active-profile browser assertion missing. |
| live-token | ActiveAccountDecoderTest with actual signed token before/after state change | Passed at real decoder; full protected-route integration exists, unrun. |
| auth-regression | ActiveAccountValidatorTest; decoder expiry/wrong-signature tests | Passed; disabled-login integration unrun. |
| auth-lookup-failure | ActiveAccountValidatorTest fails closed | Passed; Cypress protected 401 clearing/redirect source exists, unrun. |
| create-inactive | EvaluationEligibilityTest inactive stale payload, active success | Unit passed; real-DB scenario missing. |
| update-inactive | EvaluationEligibilityTest original score 8/comments unchanged | Unit passed and mutation checked; real-DB scenario missing. |
| update-target-identity | Same test: active replacement payload, disabled stored target | Unit passed; real-DB scenario missing. |
| eligible-ui | Six Node projection tests; Cypress mixed new/saved/stale refresh | Node passed; Cypress unrun. |
| historical-report | EvaluationEligibilityTest selected-week finder, inactive/no-history omission and average 8; Cypress inactive label | Unit passed; other-week-only real-DB case missing; Cypress unrun. |
| historical-retention | Unit includes inactive author and target removed from team | Unit passed; persisted reload/content comparison missing. |
| reminder-regression | Existing SubmissionStatusServiceTest | Inactive account and active teammate obligations covered in runnable suite. |
| scoped-persistence | Service tests require lifecycle/report/team/evaluator scoped finders | Unit passed; lifecycle/report repository integration source exists but unrun; evaluation repository integration missing. |
| existing-negative-flows | Existing EvaluationServiceTest week/window/no-team/foreign-team/duplicates/rubric regressions | Runnable suite passed; API integration unrun; explicit report invalid-input/empty-report extension coverage remains incomplete. |

## Independent fresh review and dispositions

A fresh reviewer received the whole diff, merged design and relevant UC/BR texts, read-only, without rerunning tests. No blocking production flaw confirmed. Findings:

1. Empty rating fixtures and missing real-DB evaluation/history cases: **fixed in part**, now original rating/total/average 8 are asserted and mutation proved; **follow-up** for persisted create/update/replacement-target/history-selected-week queries. The publish script files the follow-up and links it from the PR.
2. Browser gaps: **fixed in part**, added Escape and protected-401 test source; all browser execution still blocked here. **Follow-up** for active-profile read-only and remaining dismissal controls.
3. Old trace/auth prose: **fixed**, trace now Built/Partial independently, auth prose documents current-state validation while retaining the individual-token revocation limitation; design records the typed outcome and pure roster helper.

Review triage performed by agents: shared validator trunk `0c95adb` read deeply, dedicated SecurityConfiguration change and scope queries read deeply, lifecycle/evaluation services and changed UI leaves read against each design obligation. Existing reminder implementation was retained and its tests rerun. A request already executing at deactivation commit is not retroactively canceled; no concurrency stress test was run. The student's own code read, triage and labeled diff comment remain a human task and are not invented by this report.

## Kept tests and their external oracles

Every new test is listed below. Existing service/integration fixtures changed only for the specified generic-disable refusal, authenticated evaluator lookup and active-account report field; their existing use-case/window/rubric oracles remain in their DisplayNames. No test was deleted to suppress a regression.

### ActiveAccountValidatorTest
- `acceptsAnActivePersistedAccount` — FR-SEC-active-account; design auth-regression/auth-lookup-failure/live-token rows.
- `rejectsAnInactivePersistedAccountDespiteTokenEnabledClaim` — FR-SEC-active-account; design auth-regression/auth-lookup-failure/live-token rows.
- `resolvesByUserIdRatherThanSubjectOrTokenStatus` — FR-SEC-active-account; design auth-regression/auth-lookup-failure/live-token rows.
- `rejectsADeletedAccount` — FR-SEC-active-account; design auth-regression/auth-lookup-failure/live-token rows.
- `failsClosedWhenTheDatabaseLookupFails` — FR-SEC-active-account; design auth-regression/auth-lookup-failure/live-token rows.
- `rereadsPersistedStateOnEveryValidationOfTheSameToken` — FR-SEC-active-account; design auth-regression/auth-lookup-failure/live-token rows.
- `acceptsExactIntegralIdsWithoutNarrowingToADifferentAccount` — FR-SEC-active-account; design auth-regression/auth-lookup-failure/live-token rows.
- `rejectsAMissingUserIdWithoutLookingUpAnAccount` — FR-SEC-active-account; design auth-regression/auth-lookup-failure/live-token rows.
- `rejectsMalformedOrOutOfRangeIdsWithoutLookingUpAnotherAccount` — FR-SEC-active-account; design auth-regression/auth-lookup-failure/live-token rows.

### ActiveAccountDecoderTest
- `rejectsPreviouslyIssuedTokenAfterAccountStateChanges` — live-token: the actual configured decoder rejects the same signed token after deactivation.
- `stillRejectsExpiredSignedTokens` — auth-regression: adding account validation does not remove token expiry validation.
- `stillRejectsWrongSignature` — auth-regression: a token signed with a different key remains invalid.

### StudentLifecycleServiceTest
- `deactivatesOnlyEnabledAndRepeatsWithoutSaving` — authorized-deactivation/repeat: disable only the account and preserve its team identity.
- `supportsStudentWithoutTeam` — no-team: an enrolled student can be deactivated without acquiring membership.
- `refusesUnscopedTargetWithoutMutation` — scoped-persistence: service alone refuses a target outside the actor's scope.
- `refusesGenericDeactivationBeforeAnyProfileWrite` — profile-bypass: reject before changing name, email, account state or saving.

### StudentLifecycleSecurityTest
- `deniesStudent` — forbidden: a student cannot use the lifecycle action.
- `allowsOwningCourseAdmin` — authorized-deactivation: course owner does not need the target as default section.
- `allowsAssignedInstructor` — authorized-deactivation: assigned instructor uses target's actual course section.
- `deniesUnrelatedAdmin` — forbidden: an unrelated admin's global role is insufficient.
- `failsClosedOnLookupFailure` — unresolved-target: lookup failures deny without exception disclosure.

### StudentDeactivationIntegrationTest
- `deactivatesAndPreservesHistory` — authorized-deactivation: owner and assigned instructor preserve membership and all stored work.
- `alreadyInactiveIsHarmless` — repeat3a: authorized inactive repeat succeeds even when all student accounts are inactive.
- `deniesUnauthorizedIncludingRepeat` — forbidden2a: a student and non-owning admin cannot deactivate an active or inactive target.
- `deniesInstructorNotAssignedToTargetSection` — forbidden2a: instructor membership in another course section does not suffice.
- `rejectsExistingTokenAndNewLogin` — live-token: the exact token issued before deactivation loses access and disabled login fails.
- `scopesRepositoryQueriesIndependentlyOfRoutes` — scoped-persistence: caller scope is enforced by actual lifecycle/report JPQL finders.
- `rejectsGenericProfileDeactivation` — profile-bypass: failed generic deactivation leaves names and account state unchanged.
- `handlesMissingAuthenticationAndUnknownTargets` — missing-auth/unresolved-target: route is closed for absent credentials and bad identifiers.

### EvaluationEligibilityTest
- `rejectsInactiveCreate` — create-inactive: persisted disabled target rejects a stale active payload before saving.
- `refusesForeignCreate` — scoped-persistence: a foreign target is refused by a team-bound finder.
- `allowsActiveCreate` — create-inactive regression: active persisted target still saves in an open window.
- `refusesInactiveUpdateBeforeCommentsOrRatingsChange` — update-inactive/update-target-identity: replacement active payload cannot bypass stored disabled target.
- `refusesForeignUpdate` — scoped-persistence: service denies another evaluator's record independently of route guard.
- `includesOnlyInactiveTargetsWithSelectedWeekHistory` — historical-report/retention: include inactive target with this week's results after team removal; retain inactive author's comments.
- `deniesForeignSectionReport` — scoped-persistence: foreign course section report returns no student data.

### evaluationRows.test.mjs
- new rows include only currently active team members — BR-active-peer-evaluatee; BR-submission-owed; design eligible-ui.
- existing inactive evaluation is not offered for update and is not mutated — BR-active-peer-evaluatee; BR-submission-owed; design eligible-ui.
- a saved row and a new active member coexist without losing the saved identity — BR-active-peer-evaluatee; BR-submission-owed; design eligible-ui.
- refresh retains active unsaved edits but adopts a saved identity after partial success — BR-active-peer-evaluatee; BR-submission-owed; design eligible-ui.
- refresh removes a now-inactive target even when its unsaved row exists — BR-active-peer-evaluatee; BR-submission-owed; design eligible-ui.
- new criterion defaults respect its maximum and have independent rating arrays — BR-active-peer-evaluatee; BR-submission-owed; design eligible-ui.

### deactivation.spec.cy.js
- /students — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- @students — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- names the student and states all consequences before confirmation — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- sends no mutation request when canceled (5a) — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- sends no mutation request when dismissed with Escape (5a) — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- sends one dedicated request, prevents duplicate confirmation, and refreshes status — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- @deactivation — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- @refreshed — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- keeps the dialog usable and shows no success after a failed request — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- @failed — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- clears persisted authentication after a protected 401 — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- @unauthorized — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- uses the server message for a stale already-inactive selection (3a) — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.
- @repeat — UC-STU-deactivate-student steps 3–6, extensions 3a/5a; FR-SEC-active-account for 401.

### evaluationeligibility.spec.cy.js
- offers only active targets for new evaluations — BR-active-peer-evaluatee; BR-inactive-student-reporting; EVA extension 9a.
- retains saved identity and submits missing active members individually, never an inactive saved target — BR-active-peer-evaluatee; BR-inactive-student-reporting; EVA extension 9a.
- @updated — BR-active-peer-evaluatee; BR-inactive-student-reporting; EVA extension 9a.
- @created — BR-active-peer-evaluatee; BR-inactive-student-reporting; EVA extension 9a.
- @receipt — BR-active-peer-evaluatee; BR-inactive-student-reporting; EVA extension 9a.
- refreshes a stale target after rejection without success or a receipt (9a) — BR-active-peer-evaluatee; BR-inactive-student-reporting; EVA extension 9a.
- @roster — BR-active-peer-evaluatee; BR-inactive-student-reporting; EVA extension 9a.
- labels an included inactive result and displays the preserved score — BR-active-peer-evaluatee; BR-inactive-student-reporting; EVA extension 9a.
- @report — BR-active-peer-evaluatee; BR-inactive-student-reporting; EVA extension 9a.
