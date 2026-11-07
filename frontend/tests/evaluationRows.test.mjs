import test from 'node:test'
import assert from 'node:assert/strict'
import { prepareEligibleEvaluationRows } from '../src/pages/evaluations/peerEvaluationRows.ts'

const active = { id: 4, enabled: true, firstName: 'John', lastName: 'Smith' }
const inactive = { id: 5, enabled: false, firstName: 'Eric', lastName: 'Hudson' }
const criteria = [{ criterionId: 1, criterion: 'Quality', maxScore: 10 }]
const saved = { evaluationId: 20, evaluateeId: 4, week: '2023-W32', ratings: [{ criterionId: 1, actualScore: 7 }], publicComment: 'Keep this' }

// Oracles: BR-active-peer-evaluatee, BR-submission-owed, design row eligible-ui.
test('new rows include only currently active team members', () => {
 const rows = prepareEligibleEvaluationRows([active, inactive], [], criteria, 4, '2023-W32')
 assert.deepEqual(rows.map(row => row.evaluateeId), [4])
 assert.equal(rows[0].evaluationId, undefined)
 assert.equal(rows[0].evaluatorId, 4)
})
test('existing inactive evaluation is not offered for update and is not mutated', () => {
 const oldInactive = { ...saved, evaluationId: 21, evaluateeId: 5 }
 const before = structuredClone(oldInactive)
 const rows = prepareEligibleEvaluationRows([active, inactive], [oldInactive], criteria, 4, '2023-W32')
 assert.deepEqual(rows.map(row => row.evaluateeId), [4])
 assert.deepEqual(oldInactive, before)
 assert.equal(rows[0].evaluationId, undefined)
})
test('a saved row and a new active member coexist without losing the saved identity', () => {
 const newMember = { ...active, id: 6, firstName: 'Jerry' }
 const rows = prepareEligibleEvaluationRows([active, newMember], [saved], criteria, 4, '2023-W32')
 assert.equal(rows[0].evaluationId, 20)
 assert.equal(rows[0].publicComment, 'Keep this')
 assert.equal(rows[1].evaluateeId, 6)
 assert.equal(rows[1].evaluationId, undefined)
})
test('refresh retains active unsaved edits but adopts a saved identity after partial success', () => {
 const edit = { ...saved, evaluationId: undefined, publicComment: 'Unsaved edit' }
 const rows = prepareEligibleEvaluationRows([active], [saved], criteria, 4, '2023-W32', [edit])
 assert.equal(rows[0].evaluationId, 20)
 assert.equal(rows[0].publicComment, 'Unsaved edit')
})
test('refresh removes a now-inactive target even when its unsaved row exists', () => {
 const rows = prepareEligibleEvaluationRows([{ ...active, enabled: false }], [saved], criteria, 4, '2023-W32', [saved])
 assert.deepEqual(rows, [])
})
test('new criterion defaults respect its maximum and have independent rating arrays', () => {
 const rows = prepareEligibleEvaluationRows([active, { ...active, id: 6 }], [], [{ ...criteria[0], maxScore: 5 }], 4, '2023-W32')
 assert.equal(rows[0].ratings[0].actualScore, 5)
 rows[0].ratings[0].actualScore = 2
 assert.equal(rows[1].ratings[0].actualScore, 5)
})
