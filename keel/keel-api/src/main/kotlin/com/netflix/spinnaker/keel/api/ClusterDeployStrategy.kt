package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.DeployHealth.AUTO
import com.netflix.spinnaker.keel.api.schema.Discriminator
import java.time.Duration
import java.time.Duration.ZERO

abstract class ClusterDeployStrategy {
  @Discriminator abstract val strategy: String
  open val isStaggered: Boolean = false
  open val stagger: List<StaggeredRegion> = emptyList()
  abstract val health: DeployHealth

  companion object {
    val DEFAULT_WAIT_FOR_INSTANCES_UP: Duration = Duration.ofMinutes(30)
    const val RED_BLACK_STRATEGY = "red-black"
    const val HIGHLANDER_STRATEGY = "highlander"
    const val NONE_STRATEGY = "none"
    const val ROLLING_PUSH_STRATEGY = "rolling-push"
  }
}

data class RedBlack(
  override val health: DeployHealth = AUTO,
  // defaulting to false because this rollback behavior doesn't seem to play nice with managed delivery
  val rollbackOnFailure: Boolean? = false,
  val resizePreviousToZero: Boolean? = false,
  val maxServerGroups: Int? = 2,
  val delayBeforeDisable: Duration? = ZERO,
  val delayBeforeScaleDown: Duration? = ZERO,
  val waitForInstancesUp: Duration? = DEFAULT_WAIT_FOR_INSTANCES_UP,
  // The order of this list is important for pauseTime based staggers
  override val stagger: List<StaggeredRegion> = emptyList()
) : ClusterDeployStrategy() {
  override val strategy = RED_BLACK_STRATEGY

  override val isStaggered: Boolean
    get() = stagger.isNotEmpty()
}

data class Highlander(
  override val health: DeployHealth = AUTO
) : ClusterDeployStrategy() {
  override val strategy = HIGHLANDER_STRATEGY
}

data class NoStrategy(
  override val health: DeployHealth = AUTO
): ClusterDeployStrategy() {
  override val strategy = NONE_STRATEGY
}

data class RollingPush(
  override val health: DeployHealth = AUTO,
  val relaunchAllInstances: Boolean = true,
  /** Number of instances to terminate and relaunch at the same time */
  val numConcurrentRelaunches: Int? = null,
  val totalRelaunches: Int? = null,
  val terminationOrder: TerminationOrder? = null
): ClusterDeployStrategy() {
  override val strategy = ROLLING_PUSH_STRATEGY
}

enum class TerminationOrder {
  /** Terminates the newest instances first */
  NEWEST_FIRST,
  /** Terminates the oldest instances first */
  OLDEST_FIRST
}

enum class DeployHealth {
  /** Use Orca's default (Discovery and ELB/Target group if attached). */
  AUTO,
  /** Use only cloud provider health. */
  NONE
}

fun ClusterDeployStrategy.withDefaultsOmitted(): ClusterDeployStrategy =
  when (this) {
    is RedBlack -> {
      val defaults = RedBlack()
      RedBlack(
        maxServerGroups = nullIfDefault(maxServerGroups, defaults.maxServerGroups),
        delayBeforeDisable = nullIfDefault(delayBeforeDisable, defaults.delayBeforeDisable),
        delayBeforeScaleDown = nullIfDefault(delayBeforeScaleDown, defaults.delayBeforeScaleDown),
        resizePreviousToZero = nullIfDefault(resizePreviousToZero, defaults.resizePreviousToZero),
        rollbackOnFailure = nullIfDefault(rollbackOnFailure, defaults.rollbackOnFailure)
      )
    }
    else -> this
  }

/**
 * Allows the deployment of multi-region clusters to be staggered by region.
 *
 * @param region: The region to stagger
 * @param hours: If set, this region will only be deployed to during these hours.
 *  Should be a single range (i.e. 9-17) The timezone will be whatever is used in
 *  orca for for RestrictedExcutionWindows (defaults in Orca to America/Los_Angeles)
 * @param pauseTime: If set, pause for the given duration AFTER the deployment
 *  of this region completes
 *
 * Any regions omitted are expected to be deployed in parallel after the final staggered
 * region (and its optional [pauseTime]) have completed.
 *
 */
data class StaggeredRegion(
  val region: String,
  val hours: String? = null,
  val pauseTime: Duration? = null
) {
  init {
    require(hours != null || pauseTime != null) {
      "one of allowedHours or pauseTime must be set"
    }

    if (hours != null) {
      require(hours.matches(HOUR_RANGE_PATTERN)) {
        "hours should contain a single range, i.e. 9-17 or 22-2"
      }
    }
  }

  companion object {
    private val HOUR_RANGE_PATTERN =
      """^\d+-\d+$""".toRegex()
  }
}

private fun <T> nullIfDefault(value: T, default: T): T? =
  if (value == default) null else value
