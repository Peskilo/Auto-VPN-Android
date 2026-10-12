package dev.typenil.vpnclient.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.typenil.vpnclient.R

@StringRes
internal fun expandedStateRes(expanded: Boolean): Int =
    if (expanded) R.string.a11y_expanded else R.string.a11y_collapsed

/** Disclosure button: TalkBack reads "<label>, Button, Expanded/Collapsed". */
@Composable
internal fun Modifier.disclosureSemantics(expanded: Boolean): Modifier {
    val state = stringResource(expandedStateRes(expanded))
    return semantics { role = Role.Button; stateDescription = state }
}

/** Marks a title as a heading for TalkBack heading navigation. */
internal fun Modifier.headingSemantics(): Modifier = semantics { heading() }
