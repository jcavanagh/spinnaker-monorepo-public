/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.persistence;

import static com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionComparator.START_TIME_OR_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import org.junit.jupiter.api.Test;

final class PipelineExecutionRepositoryTest {
  private static final String APPLICATION = "myapp";

  private static final long EARLIER_TIME = 629510400000L;
  private static final String EARLIER_ULID = "00JA8VWT00BNG9YKW1B9YEVX7Y";

  private static final long LATER_TIME = 1576195200000L;
  private static final String LATER_ULID_1 = "01DVY8W500KZ1D9TSSDM4D30A8";
  private static final String LATER_ULID_2 = "01DVY8W500S4M3CXD2QJ7P2877";

  @Test
  void nullSortsFirst() {
    PipelineExecutionImpl a = withStartTimeAndId(null, EARLIER_ULID);
    PipelineExecutionImpl b = withStartTimeAndId(EARLIER_TIME, EARLIER_ULID);

    assertThat(START_TIME_OR_ID.compare(a, a)).isEqualTo(0);
    assertThat(START_TIME_OR_ID.compare(a, b)).isLessThan(0);
    assertThat(START_TIME_OR_ID.compare(b, a)).isGreaterThan(0);
  }

  @Test
  void nullStartTimesSortById() {
    PipelineExecutionImpl a = withStartTimeAndId(null, LATER_ULID_1);
    PipelineExecutionImpl b = withStartTimeAndId(null, EARLIER_ULID);

    assertThat(START_TIME_OR_ID.compare(a, a)).isEqualTo(0);
    assertThat(START_TIME_OR_ID.compare(a, b)).isLessThan(0);
    assertThat(START_TIME_OR_ID.compare(b, a)).isGreaterThan(0);
  }

  @Test
  void laterStartTimeSortsFirst() {
    PipelineExecutionImpl a = withStartTimeAndId(LATER_TIME, EARLIER_ULID);
    PipelineExecutionImpl b = withStartTimeAndId(EARLIER_TIME, EARLIER_ULID);

    assertThat(START_TIME_OR_ID.compare(a, a)).isEqualTo(0);
    assertThat(START_TIME_OR_ID.compare(a, b)).isLessThan(0);
    assertThat(START_TIME_OR_ID.compare(b, a)).isGreaterThan(0);
  }

  @Test
  void laterIdSortsFirst() {
    PipelineExecutionImpl a = withStartTimeAndId(LATER_TIME, LATER_ULID_2);
    PipelineExecutionImpl b = withStartTimeAndId(LATER_TIME, LATER_ULID_1);

    assertThat(START_TIME_OR_ID.compare(a, a)).isEqualTo(0);
    assertThat(START_TIME_OR_ID.compare(a, b)).isLessThan(0);
    assertThat(START_TIME_OR_ID.compare(b, a)).isGreaterThan(0);
  }

  @Test
  void fullSort() {
    PipelineExecutionImpl nullStartTime = withStartTimeAndId(null, EARLIER_ULID);
    PipelineExecutionImpl earlierStartTime = withStartTimeAndId(EARLIER_TIME, EARLIER_ULID);
    PipelineExecutionImpl laterStartTimeEarlierId = withStartTimeAndId(LATER_TIME, LATER_ULID_1);
    PipelineExecutionImpl laterStartTimeLaterId = withStartTimeAndId(LATER_TIME, LATER_ULID_2);

    // The order of this list is not significant, only that it is some random non-sorted order
    ImmutableList<PipelineExecutionImpl> executions =
        ImmutableList.of(
            laterStartTimeEarlierId, earlierStartTime, laterStartTimeLaterId, nullStartTime);

    assertThat(ImmutableList.sortedCopyOf(START_TIME_OR_ID, executions))
        .containsExactly(
            nullStartTime, laterStartTimeLaterId, laterStartTimeEarlierId, earlierStartTime);
  }

  private PipelineExecutionImpl withStartTimeAndId(Long startTime, String id) {
    PipelineExecutionImpl execution =
        new PipelineExecutionImpl(ExecutionType.PIPELINE, id, APPLICATION);
    execution.setStartTime(startTime);
    return execution;
  }
}
