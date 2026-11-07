import type { PeerEvaluation } from '@/apis/evaluation/types'
import type { Student } from '@/apis/student/types'
import type { Criterion } from '@/apis/rubric/types'

// BR-active-peer-evaluatee / BR-submission-owed: eligibility comes from the current roster,
// independently of whether a previous submission happened to include that member.
export function prepareEligibleEvaluationRows(
  students: Student[],
  saved: PeerEvaluation[],
  criteria: Criterion[],
  evaluatorId: number,
  week: string,
  drafts: PeerEvaluation[] = []
): PeerEvaluation[] {
  const savedById = new Map(saved.map((row) => [row.evaluateeId, row]))
  const draftsById = new Map(drafts.map((row) => [row.evaluateeId, row]))
  return students.filter((student) => student.enabled === true && student.id != null).map((student) => {
    const id = student.id as number
    const persisted = savedById.get(id)
    const draft = draftsById.get(id)
    const previous = draft ?? persisted
    return {
      ...previous,
      evaluationId: persisted?.evaluationId ?? draft?.evaluationId,
      week,
      evaluatorId,
      evaluateeId: id,
      evaluateeName: `${student.firstName} ${student.lastName}`,
      ratings: previous
        ? previous.ratings.map((rating) => ({ ...rating }))
        : criteria.map((criterion) => ({
            criterionId: criterion.criterionId as number,
            criterion: criterion.criterion,
            actualScore: Math.min(10, criterion.maxScore)
          })),
      publicComment: previous?.publicComment ?? '',
      privateComment: previous?.privateComment ?? ''
    }
  })
}
