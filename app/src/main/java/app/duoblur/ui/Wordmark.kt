package app.duoblur.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "Duo" solid, then "Flow" typed out letter by letter with a small blur
 * radius growing across it — the same "none at the near edge, full strength
 * at the far edge" grading the app's own Blur slider applies to the frame,
 * just borrowed for the wordmark instead of a captured screenshot.
 */
@Composable
internal fun DuoFlowWordmark() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "Duo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        "Flow".forEachIndexed { index, letter ->
            Text(
                letter.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.blur((index * 0.6f).dp)
            )
        }
    }
}
