package com.satvik.satvikx.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * Ensures horizontal scroll gestures inside this container (such as recommendation carousels,
 * sliders, and chip rows) are strictly isolated and never bubble up to a parent HorizontalPager.
 * When the carousel hits its start or end boundary, leftover horizontal scroll deltas and flings
 * are fully absorbed so the parent tab pager is never accidentally dragged or switched.
 */
fun Modifier.isolateHorizontalScroll(): Modifier = this.nestedScroll(
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            // Absorb any remaining horizontal scroll delta so it never propagates up to any parent pager
            return if (available.x != 0f) Offset(available.x, 0f) else Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // Absorb any remaining horizontal fling velocity so flinging a slider never triggers a page change
            return if (available.x != 0f) Velocity(available.x, 0f) else Velocity.Zero
        }
    }
)

