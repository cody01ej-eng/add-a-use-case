// UI-contract tests with stubbed API answers. Backend authorization is verified separately.
// Oracles: UC-STU-deactivate-student steps3-6, extensions5a/3a, and design warning/cancel/confirm rows.
const john = { id: 4, username: 'j.smith@abc.edu', firstName: 'John', lastName: 'Smith', email: 'j.smith@abc.edu', enabled: true, teamId: 1, teamName: 'Team1', sectionId: 2 }
const ok = data => ({ flag: true, code: 200, message: 'Success', data })
function openStudents() {
 const token = `${btoa('{"alg":"RS256"}')}.${btoa(JSON.stringify({ userId: 2, exp: Math.floor(Date.now() / 1000) + 3600 }))}.stub`
 cy.intercept('POST', '**/students/search*', { body: ok({ content: [john], number: 0, size: 50, totalElements: 1 }) }).as('students')
 cy.visit('/students', { onBeforeLoad(win) {
  win.localStorage.setItem('token', JSON.stringify({ token }))
  win.localStorage.setItem('userInfo', JSON.stringify({ userInfo: { id: 2, firstName: 'Bill', lastName: 'Gates', roles: 'instructor' } }))
  win.localStorage.setItem('settings', JSON.stringify({ defaultSectionId: 2, defaultCourseId: 1 }))
 } })
 cy.wait('@students')
 cy.get('[data-cy=deactivate-open]').click()
}
describe('Dedicated student deactivation', () => {
 it('names the student and states all consequences before confirmation', () => {
  openStudents()
  cy.screenshot('deactivation-confirmation-warning')
  cy.get('[data-cy=deactivate-dialog]').should('contain', 'John Smith').and('contain', 'access').and('contain', 'team').and('contain', 'evaluations').and('contain', 'reminders').and('contain', 'historical')
 })
 it('sends no mutation request when canceled (5a)', () => {
  cy.intercept('PATCH', '**/students/*/deactivation', { body: ok({ ...john, enabled: false }) }).as('deactivation')
  openStudents()
  cy.get('[data-cy=deactivate-cancel]').click()
  cy.get('[data-cy=deactivate-dialog]').should('not.be.visible')
  cy.get('@deactivation.all').should('have.length', 0)
  cy.contains('Enabled').should('be.visible')
 })
 it('sends no mutation request when dismissed with Escape (5a)', () => {
  cy.intercept('PATCH', '**/students/*/deactivation', { body: ok(john) }).as('deactivation')
  openStudents()
  cy.get('body').type('{esc}')
  cy.get('[data-cy=deactivate-dialog]').should('not.be.visible')
  cy.get('@deactivation.all').should('have.length', 0)
 })
 it('sends one dedicated request, prevents duplicate confirmation, and refreshes status', () => {
  cy.intercept('PATCH', '**/students/4/deactivation', { delay: 500, body: { ...ok({ ...john, enabled: false }), message: 'Student deactivated successfully' } }).as('deactivation')
  openStudents()
  cy.intercept('POST', '**/students/search*', { body: ok({ content: [{ ...john, enabled: false }], number: 0, size: 50, totalElements: 1 }) }).as('refreshed')
  cy.get('[data-cy=deactivate-confirm]').click()
  cy.get('[data-cy=deactivate-confirm]').should('be.disabled').click({ force: true })
  cy.wait('@deactivation').its('request.method').should('eq', 'PATCH')
  cy.wait('@refreshed')
  cy.get('@deactivation.all').should('have.length', 1)
  cy.contains('Student deactivated successfully').should('be.visible')
  cy.contains('Disabled').should('be.visible')
 })
 it('keeps the dialog usable and shows no success after a failed request', () => {
  cy.intercept('PATCH', '**/students/4/deactivation', { statusCode: 500, body: { flag: false, code: 500, message: 'Request failed' } }).as('failed')
  openStudents()
  cy.get('[data-cy=deactivate-confirm]').click()
  cy.wait('@failed')
  cy.get('[data-cy=deactivate-confirm]').should('be.enabled')
  cy.get('[data-cy=deactivate-dialog]').should('be.visible')
  cy.contains('Student deactivated successfully').should('not.exist')
 })
 it('clears persisted authentication after a protected 401', () => {
  openStudents()
  cy.intercept('PATCH', '**/students/4/deactivation', { statusCode: 401, body: { flag: false, code: 401, message: 'Unauthorized' } }).as('unauthorized')
  cy.get('[data-cy=deactivate-confirm]').click()
  cy.wait('@unauthorized')
  cy.location('pathname').should('eq', '/login')
  cy.window().then(win => {
   expect(JSON.parse(win.localStorage.getItem('token')).token).to.eq('')
  })
 })
 it('uses the server message for a stale already-inactive selection (3a)', () => {
  cy.intercept('PATCH', '**/students/4/deactivation', { body: { ...ok({ ...john, enabled: false }), message: 'Student is already deactivated.' } }).as('repeat')
  openStudents()
  cy.get('[data-cy=deactivate-confirm]').click()
  cy.wait('@repeat')
  cy.contains('Student is already deactivated.').should('be.visible')
 })
})
