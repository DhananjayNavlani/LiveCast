
package com.dhananjay.livecast.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dhananjay.livecast.ui.theme.LiveCastTheme

enum class LiveCastButtonVariant {
    Primary,
    Secondary,
    Outlined,
    Text
}

@Composable
fun LiveCastButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: LiveCastButtonVariant = LiveCastButtonVariant.Primary,
    enabled: Boolean = true,
) {
    val colors = LiveCastTheme.colors
    val typography = LiveCastTheme.typography
    
    when (variant) {
        LiveCastButtonVariant.Primary -> {
            Button(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledBackgroundColor = colors.disabled,
                    disabledContentColor = colors.onDisabled
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    style = typography.button
                )
            }
        }
        LiveCastButtonVariant.Secondary -> {
            Button(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = colors.secondary,
                    contentColor = colors.onSecondary,
                    disabledBackgroundColor = colors.disabled,
                    disabledContentColor = colors.onDisabled
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    style = typography.button
                )
            }
        }
        LiveCastButtonVariant.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (enabled) colors.primary else colors.disabled),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.primary,
                    disabledContentColor = colors.onDisabled
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    style = typography.button
                )
            }
        }
        LiveCastButtonVariant.Text -> {
            androidx.compose.material.TextButton(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.primary,
                    disabledContentColor = colors.onDisabled
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = text,
                    style = typography.button
                )
            }
        }
    }
}

@Composable
fun VerticalSpacer(height: Int = 16) {
    Spacer(modifier = Modifier.height(height.dp))
}

@Composable
fun HorizontalSpacer(width: Int = 16) {
    Spacer(modifier = Modifier.width(width.dp))
}
