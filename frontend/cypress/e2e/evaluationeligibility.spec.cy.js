// UI-contract tests: APIs are stubbed. Service and repository tests own backend policy evidence.
// Oracles: BR-active-peer-evaluatee, BR-submission-owed, BR-inactive-student-reporting;
// design rows eligible-ui/historical-report and EVA extension9a.
const ok = data => ({ flag: true, code: 200, message: 'Success', data })
const john = { id: 4, username: 'j.smith@abc.edu', firstName: 'John', lastName: 'Smith', enabled: true, teamId: 1, sectionId: 2 }
const eric = { ...john, id: 5, firstName: 'Eric', lastName: 'Hudson', enabled: false }
const jerry = { ...john, id: 6, firstName: 'Jerry', lastName: 'Moon' }
const rating = { criterionId: 1, criterion: 'Quality', actualScore: 7 }
const evaluation = (id, target) => ({ evaluationId: id, evaluateeId: target.id, evaluateeName: `${target.firstName} ${target.lastName}`, evaluatorId: 4, week: '2023-W32', ratings: [{ ...rating }], publicComment: 'Saved', privateComment: 'Private' })

function visitAs(path, student = true) {
 const token = `${btoa('{"alg":"RS256"}')}.${btoa(JSON.stringify({ userId: student ? 4 : 2, exp: Math.floor(Date.now() / 1000) + 3600 }))}.stub`
 cy.intercept('GET', '**/sections/2', { body: ok({ sectionId: 2, rubricId: 1, activeWeeks: ['2023-W31', '2023-W32', '2023-W33'] }) })
 cy.intercept('GET', '**/rubrics/1', { body: ok({ rubricId: 1, criteria: [{ ...rating, description: 'Quality', maxScore: 10 }] }) })
 cy.intercept('POST', '**/evaluations/weeks/*/receipt', { body: ok(null) }).as('receipt')
 cy.clock(new Date('2023-08-20T12:00:00Z').getTime(), ['Date'])
 cy.visit(path, { onBeforeLoad(win) {
  win.localStorage.setItem('token', JSON.stringify({ token }))
  win.localStorage.setItem('userInfo', JSON.stringify({ userInfo: student ? { ...john, roles: 'student' } : { id: 2, firstName: 'Bill', roles: 'instructor' } }))
  win.localStorage.setItem('settings', JSON.stringify({ defaultSectionId: 2, defaultCourseId: 1 }))
 } })
}

describe('Current peer evaluation eligibility', () => {
 it('offers only active targets for new evaluations', () => {
  cy.intercept('GET', '**/students/teams/1', { body: ok([john, eric]) })
  cy.intercept('GET', '**/evaluations/evaluators/4/week/*', { body: ok([]) })
  visitAs('/evaluations/submit-evaluations')
  cy.get('[data-cy=evaluation-table]').should('contain', 'John Smith').and('not.contain', 'Eric Hudson')
 })

 it('retains saved identity and submits missing active members individually, never an inactive saved target', () => {
  cy.intercept('GET', '**/students/teams/1', { body: ok([john, eric, jerry]) })
  cy.intercept('GET', '**/evaluations/evaluators/4/week/*', { body: ok([evaluation(10, john), evaluation(11, eric)]) })
  cy.intercept('PUT', '**/evaluations/10', { body: ok(evaluation(10, john)) }).as('updated')
  cy.intercept('POST', '**/evaluations', { body: ok(evaluation(12, jerry)) }).as('created')
  cy.intercept('PUT', '**/evaluations/11', { body: ok(evaluation(11, eric)) }).as('inactiveUpdate')
  visitAs('/evaluations/submit-evaluations')
  cy.get('[data-cy=evaluation-table]').should('contain', 'John Smith').and('contain', 'Jerry Moon').and('not.contain', 'Eric Hudson')
  cy.get('[data-cy=evaluation-save]').click()
  cy.wait('@updated').its('request.body.evaluateeId').should('eq', 4)
  cy.wait('@created').its('request.body.evaluateeId').should('eq', 6)
  cy.get('@inactiveUpdate.all').should('have.length', 0)
  cy.wait('@receipt')
 })

 it('refreshes a stale target after rejection without success or a receipt (9a)', () => {
  let rosterReads = 0
  cy.intercept('GET', '**/students/teams/1', req => req.reply({ body: ok(++rosterReads === 1 ? [john, jerry] : [{ ...john, enabled: false }, jerry]) })).as('roster')
  cy.intercept('GET', '**/evaluations/evaluators/4/week/*', { body: ok([]) })
  cy.intercept('POST', '**/evaluations', req => {
   if (req.body.evaluateeId === 4) req.reply({ statusCode: 400, body: { flag: false, code: 400, message: 'Peer evaluations cannot be submitted for an inactive student.' } })
   else req.reply({ body: ok(evaluation(12, jerry)) })
  }).as('save')
  visitAs('/evaluations/submit-evaluations')
  cy.get('[data-cy=evaluation-table]').should('contain', 'John Smith')
  cy.get('[data-cy=evaluation-save]').click()
  cy.wait('@roster')
  cy.get('[data-cy=evaluation-table]').should('not.contain', 'John Smith').and('contain', 'Jerry Moon')
  cy.get('[data-cy=evaluation-save]').should('be.enabled')
  cy.get('@receipt.all').should('have.length', 0)
  cy.contains('Peer evaluations submitted successfully').should('not.exist')
 })
})

describe('Historical inactive-account report presentation', () => {
 it('labels an included inactive result and displays the preserved score', () => {
  cy.intercept('GET', '**/evaluations/sections/2/week/*', { body: ok({ sectionName: 'Section2', week: '2023-W32', peerEvaluationAverages: [{ studentId: 5, firstName: 'Eric', lastName: 'Hudson', enabled: false, averageTotalScore: 7, publicComments: ['Saved'], privateComments: [], ratingAverages: [{ criterionId: 1, averageScore: 7 }] }] }) }).as('report')
  cy.intercept('GET', '**/sections/2/submission-status*', { body: ok({ teams: [] }) })
  visitAs('/section-evaluations', false)
  cy.wait('@report')
  cy.get('[data-cy=inactive-report-label]').should('contain', 'Inactive')
  cy.contains('Hudson, Eric').should('be.visible')
  cy.contains('7.00').should('be.visible')
 })
})
