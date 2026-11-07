<template>
  <el-card class="page-container">
    <template #header>
      <div class="header">
        <span
          >Submit Peer Evaluations for week:
          <el-tag type="primary" style="font-size: 14px; font-weight: bold">{{
            previousWeekRange
          }}</el-tag></span
        >
      </div>
      <div style="margin-top: 10px">
        <span>
          The default score for each criterion is 10. You can change it to a value between 0 and 10.
        </span>
        <!-- Remind the user that "You have NOT submitted the evaluation" -->
        <el-tag v-if="isNewSubmission" type="danger" style="font-size: 14px; font-weight: bold"
          >You have NOT submitted the evaluation. Click the "Submit" button.</el-tag
        >
      </div>
    </template>
    <!-- Evaluation Table, adding height can fix the table header -->
    <el-table
      :data="peerEvalutions"
      data-cy="evaluation-table"
      style="width: 100%"
      stripe
      border
      height="600"
      v-loading="loading"
      scrollbar-always-on
    >
      <el-table-column
        label="Student Name"
        prop="evaluateeName"
        min-width="100"
        fixed
      ></el-table-column>
      <el-table-column v-for="criterion in criteria" :key="criterion.criterionId" min-width="150">
        <template #header>
          <el-tooltip effect="dark" :content="criterion.criterion" placement="top">
            <span>{{ criterion.description }}</span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <div style="display: flex; justify-content: center; align-items: center">
            <el-input-number
              v-model="
                row.ratings.find((r: Rating) => r.criterionId === criterion.criterionId).actualScore
              "
              :min="0"
              :max="criterion.maxScore"
              :step="1"
              controls-position="right"
              style="width: 80px"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column min-width="200px">
        <template #header>
          <el-tooltip
            effect="dark"
            content="Public comments are available to the evaluatee."
            placement="top"
          >
            <span>Public Comment</span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <el-input
            v-model="row.publicComment"
            type="textarea"
            :rows="5"
            placeholder="Enter public comment"
            max-length="255"
            show-word-limit
          />
        </template>
      </el-table-column>
      <el-table-column min-width="200px">
        <template #header>
          <el-tooltip
            effect="dark"
            content="Private comments are ONLY available to the instructor."
            placement="top"
          >
            <span>Private Comment</span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <el-input
            v-model="row.privateComment"
            type="textarea"
            :rows="5"
            placeholder="Enter private comment"
            max-length="255"
            show-word-limit
          />
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No data is available." />
      </template>
    </el-table>
    <!-- submit button on the right side-->
    <div style="display: flex; justify-content: flex-end">
      <el-button
        type="primary"
        @click="savePeerEvaluations"
        data-cy="evaluation-save"
        :disabled="!peerEvalutions?.length"
        v-if="isNewSubmission"
        :loading="isLoading"
        >Submit</el-button
      >
      <el-button type="warning" @click="savePeerEvaluations" data-cy="evaluation-save" :disabled="!peerEvalutions?.length" :loading="isLoading" v-else
        >Update</el-button
      >
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { prepareEligibleEvaluationRows } from './peerEvaluationRows'
import { findStudentsByTeamId } from '@/apis/student'
import type { Student, FindStudentByTeamIdResponse } from '@/apis/student/types'
import { findSectionById } from '@/apis/section'
import { findRubricById } from '@/apis/rubric'
import type { FindRubricByIdResponse, Criterion } from '@/apis/rubric/types'
import { getPreviousWeek, getPreviousWeekRange } from '@/utils/week'
import { useUserInfoStore } from '@/stores/userInfo'
import type { FindSectionByIdResponse } from '@/apis/section/types'
import {
  submitEvaluation,
  getEvaluationsByEvaluatorIdAndWeek,
  updateEvaluation,
  sendPeerEvaluationSubmissionConfirmationEmail
} from '@/apis/evaluation'
import { ElMessage } from 'element-plus'
import type {
  FetchPeerEvaluationsResponse,
  PeerEvaluation,
  Rating
} from '@/apis/evaluation/types'

const previousWeek = ref<string>('')
const previousWeekRange = ref<string>('')

const peerEvalutions = ref<PeerEvaluation[]>()

const loading = ref<boolean>(false)

const isNewSubmission = ref(true) // Rows can contain a mix of existing and new evaluations

const teamId = ref<number>(NaN) // Team ID of the student

const students = ref<Student[]>([]) // Students on the same team
const sectionId = ref<number>() // Section ID of the team
const criteria = ref<Criterion[]>([]) // Criteria used for peer evaluations
const isLoading = ref(false)

const userInfoStore = useUserInfoStore() // Need team ID to search for students

// Load data when the component is mounted
onMounted(async () => {
  previousWeek.value = getPreviousWeek()
  previousWeekRange.value = getPreviousWeekRange()
  teamId.value = (userInfoStore.userInfo as Student).teamId as number
  if (!teamId.value) {
    ElMessage.error('You have not been assigned a team yet.')
    return
  }
  loading.value = true
  try {
    await loadStudents()
    sectionId.value = (userInfoStore.userInfo as Student).sectionId
    await loadCriteria()
    await prepareStartingPeerEvaluations()
  } finally {
    loading.value = false
  }
})

// Load activities based on the search criteria
async function loadStudents() {
  // On the back end, the page number starts from 0, so subtract 1 here
  const result: FindStudentByTeamIdResponse = await findStudentsByTeamId(teamId.value)
  students.value = result.data
}

// Load criteria information
async function loadCriteria() {
  const sectionResult: FindSectionByIdResponse = await findSectionById(sectionId.value as number)
  const rubricResult: FindRubricByIdResponse = await findRubricById(
    sectionResult.data.rubricId as number
  )
  criteria.value = rubricResult.data.criteria!
}

// Eligibility is current roster state, including when loading previously saved rows.
async function prepareStartingPeerEvaluations(preserveDrafts = false) {
  const drafts = preserveDrafts ? peerEvalutions.value ?? [] : []
  const result: FetchPeerEvaluationsResponse = await getEvaluationsByEvaluatorIdAndWeek(
    (userInfoStore.userInfo as Student).id as number,
    previousWeek.value
  )
  peerEvalutions.value = prepareEligibleEvaluationRows(
    students.value,
    result.data,
    criteria.value,
    (userInfoStore.userInfo as Student).id as number,
    previousWeek.value,
    drafts
  )
  isNewSubmission.value = peerEvalutions.value.some((row) => row.evaluationId == null)
}

async function savePeerEvaluations() {
  if (isLoading.value || !peerEvalutions.value?.length) return
  isLoading.value = true
  const hadNewRows = peerEvalutions.value.some((row) => row.evaluationId == null)
  try {
    // Existing API calls save individual evaluations, not an atomic whole-team batch.
    // Wait for every request before refreshing after a partial failure.
    const outcomes = await Promise.allSettled(peerEvalutions.value.map((evaluation) =>
      evaluation.evaluationId == null ? submitEvaluation(evaluation) : updateEvaluation(evaluation)
    ))
    const failure = outcomes.find((outcome) => outcome.status === 'rejected')
    if (failure?.status === 'rejected') throw failure.reason
    peerEvalutions.value = outcomes.flatMap((outcome) =>
      outcome.status === 'fulfilled' ? [outcome.value.data] : []
    )
    isNewSubmission.value = false
    ElMessage.success(hadNewRows ? 'Peer evaluations submitted successfully' : 'Peer evaluations updated successfully')
    if (hadNewRows) {
      await sendPeerEvaluationSubmissionConfirmationEmail(previousWeekRange.value)
      ElMessage.success('A confirmation email has been sent to you.')
    }
  } catch {
    // Shared request handling reports the error. Refresh current eligibility and
    // saved ids while retaining drafts for active members; never offer inactive edits.
    try {
      await loadStudents()
      await prepareStartingPeerEvaluations(true)
    } catch {
      // The shared client also reports a failed refresh; finally releases the button.
    }
  } finally {
    isLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100%;
  box-sizing: border-box;
  .header {
    display: flex;
    align-items: center;
    gap: 20px;
  }
}
</style>
