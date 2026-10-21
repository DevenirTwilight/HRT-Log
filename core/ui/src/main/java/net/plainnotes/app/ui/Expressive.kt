package net.plainnotes.app.ui

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- UI-07 D: motion -------------------------------------------------------------------------------------------------

/**
 * True when motion should be skipped: the system "Remove animations" setting (animator duration scale 0). Every UI-07
 * animation reads it and switches at once. Tests provide it directly.
 */
val LocalReduceMotion = compositionLocalOf { false }

/** The system setting behind [LocalReduceMotion]; [override] lets a test force it either way. */
object ReduceMotion {
    @Volatile var override: Boolean? = null
    fun fromSystem(context: android.content.Context): Boolean = override
        ?: (runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) == 0f)
}


/** The UI-07 spring: damping 0.6, medium-low stiffness. */
object Motion {
    const val DAMPING = 0.6f
    const val STIFFNESS = Spring.StiffnessMediumLow
    /** For the "logged" icon pop: a little more bounce, about half a second. */
    const val POP_DAMPING = 0.45f
    const val POP_STIFFNESS = Spring.StiffnessLow
    const val SLIDE_IN_DP = 14
    const val PRESS_SCALE = 0.96f
    val PRESS_RADIUS = 16.dp
}

/** The UI-07 spring, or an instant switch when motion is reduced. */
fun <T> motionSpec(reduce: Boolean, damping: Float = Motion.DAMPING, stiffness: Float = Motion.STIFFNESS): AnimationSpec<T> =
    if (reduce) snap() else spring(dampingRatio = damping, stiffness = stiffness)

// --- UI-07 B: press morph --------------------------------------------------------------------------------------------

/** The live corner radius and scale of a pressable control, for its shape and graphics layer. */
@Immutable data class PressMorph(val radius: Dp, val scale: Float) { val shape: Shape get() = RoundedCornerShape(radius) }

/** Tests read the animated values here (not spoken). */
val PressMorphKey = SemanticsPropertyKey<PressMorph>("PressMorph")
var SemanticsPropertyReceiver.pressMorph by PressMorphKey

/**
 * UI-07 B: while [interaction] is pressed the corners spring from [rest] to [pressed] and the control shrinks to 96 %;
 * on release they spring back. Reduced motion jumps straight to each end.
 */
@Composable fun rememberPressMorph(interaction: MutableInteractionSource, rest: Dp, pressed: Dp = Motion.PRESS_RADIUS): PressMorph {
    val down by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val radius by animateDpAsState(if (down) pressed else rest, motionSpec(reduce), label = "pressRadius")
    val scale by animateFloatAsState(if (down) Motion.PRESS_SCALE else 1f, motionSpec(reduce), label = "pressScale")
    return PressMorph(radius, scale)
}

/** Applies a [PressMorph]'s scale and exposes it to tests; pass [PressMorph.shape] as the control's shape. */
fun Modifier.pressMorph(m: PressMorph): Modifier = graphicsLayer { scaleX = m.scale; scaleY = m.scale }.semantics { pressMorph = m }

// --- UI-07 C: emphasised type ------------------------------------------------------------------------------------------

/** Emphasised styles on top of the Material type scale. Body text stays weight 400; no new font family. */
@Immutable data class TypeExtra(val heroNumber: TextStyle, val statNumber: TextStyle, val sectionTitle: TextStyle, val cardTitle: TextStyle)

/** Large numbers stop growing at 1.3× system font size; everything else scales as usual. */
const val NUMBER_FONT_SCALE_CAP = 1.3f

/** [sp] at font scale 1, as the size it must be passed in so that, after the system scale, it never exceeds 1.3×. */
fun cappedSp(sp: Float, fontScale: Float): TextUnit = (sp * minOf(fontScale, NUMBER_FONT_SCALE_CAP) / fontScale).sp

@Composable @ReadOnlyComposable fun currentTypeExtra(): TypeExtra {
    val fs = LocalDensity.current.fontScale
    val numbers = TextStyle(fontFeatureSettings = "tnum")
    return TypeExtra(
        heroNumber = numbers.copy(fontSize = cappedSp(64f, fs), lineHeight = cappedSp(68f, fs), fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp),
        statNumber = numbers.copy(fontSize = cappedSp(28f, fs), lineHeight = cappedSp(34f, fs), fontWeight = FontWeight.ExtraBold),
        sectionTitle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
        cardTitle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    )
}

// --- UI-07 E: connected grouped list -----------------------------------------------------------------------------------

/** Corners of item [index] of [count]: 24 dp at the outer ends of the group, 6 dp where items meet. */
fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp; val inner = 6.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** The gap between grouped items. */
val GroupedGap = 3.dp

/**
 * UI-07 E: a list whose items are separate containers 3 dp apart, rounded 24 dp at the group's ends and 6 dp between items;
 * each at least 56 dp tall. [onClick] (when given) makes the whole item clickable with the platform press feedback; the
 * content keeps its own tags and semantics.
 */
@Composable fun <T> GroupedList(items: List<T>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                onClick: ((T) -> Unit)? = null, itemModifier: (T) -> Modifier = { Modifier }, content: @Composable (T) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GroupedGap)) {
        items.forEachIndexed { i, item -> GroupedItem(i, items.size, color, onClick?.let { { it(item) } }, itemModifier(item)) { content(item) } }
    }
}

/** One item of a [GroupedList], for lists that build their items themselves (e.g. inside a LazyColumn). */
@Composable fun GroupedItem(index: Int, count: Int, color: Color = MaterialTheme.colorScheme.surfaceContainerLowest, onClick: (() -> Unit)? = null,
                            modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = remember(index, count) { groupedShape(index, count) }
    val m = modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { groupedShape = shape }
    if (onClick != null) Surface(onClick = onClick, shape = shape, color = color, modifier = m) { content() }
    else Surface(shape = shape, color = color, modifier = m) { content() }
}

/** Tests read an item's shape here (not spoken). */
val GroupedShapeKey = SemanticsPropertyKey<RoundedCornerShape>("GroupedShape")
var SemanticsPropertyReceiver.groupedShape by GroupedShapeKey
