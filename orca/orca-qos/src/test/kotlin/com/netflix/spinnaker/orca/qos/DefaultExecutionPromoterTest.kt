/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.qos

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.NOT_STARTED
import com.netflix.spinnaker.orca.api.test.pipeline
import com.netflix.spinnaker.orca.notifications.AlwaysUnlockedNotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek

class DefaultExecutionPromoterTest : SubjectSpek<DefaultExecutionPromoter>({

  val executionLauncher: ExecutionLauncher = mock()
  val executionRepository: ExecutionRepository = mock()
  val policy: PromotionPolicy = mock()

  subject(CachingMode.GROUP) {
    DefaultExecutionPromoter(executionLauncher, executionRepository, listOf(policy), NoopRegistry(), 5000, AlwaysUnlockedNotificationClusterLock())
  }

  fun resetMocks() = reset(executionRepository, policy)

  describe("promoting executions") {
    given("buffered executions") {
      val execution1 = pipeline {
        application = "orca"
      }
      val execution2 = pipeline {
        application = "keel"
      }
      val execution3 = pipeline {
        application = "clouddriver"
      }

      beforeGroup {
        whenever(executionRepository.retrieveBufferedExecutions()) doReturn listOf(execution1, execution2, execution3)
        whenever(policy.apply(any())) doReturn PromotionResult(
          candidates = listOf(execution1, execution2),
          finalized = false,
          reason = "Testing"
        )
      }

      afterGroup(::resetMocks)

      on("promote schedule") {
        subject.tick()
      }

      it("promotes all policy-selected candidate executions via status update") {
        assertThat(execution1.status).isEqualTo(NOT_STARTED)
        assertThat(execution2.status).isEqualTo(NOT_STARTED)
        verify(executionRepository).updateStatus(execution1)
        verify(executionRepository).updateStatus(execution2)
      }

      it("starts the executions immediately") {
        verify(executionLauncher).start(execution1)
        verify(executionLauncher).start(execution2)
        verifyNoMoreInteractions(executionLauncher)
      }
    }
  }
})
