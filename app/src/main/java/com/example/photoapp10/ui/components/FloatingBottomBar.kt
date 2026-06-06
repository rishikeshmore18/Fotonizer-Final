package com.example.photoapp10.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloatingBottomBar(
    leftIcon: Painter,
    leftLabel: String,
    onLeftClick: () -> Unit,
    centerIcon: Painter,
    centerLabel: String,
    onCenterClick: () -> Unit,
    rightIcon: Painter,
    rightLabel: String,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .height(104.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarPillItem(
                icon = leftIcon,
                label = leftLabel,
                onClick = onLeftClick,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(118.dp))

            BottomBarPillItem(
                icon = rightIcon,
                label = rightLabel,
                onClick = onRightClick,
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(94.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    spotColor = Color(0x55906FDC)
                ),
            shape = CircleShape,
            color = Color.White
        ) {
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onCenterClick,
                    modifier = Modifier
                        .size(78.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF9E83E6), Color(0xFF7C63D8))
                            ),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        painter = centerIcon,
                        contentDescription = centerLabel,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarPillItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(60.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(30.dp),
                spotColor = Color(0x22906FDC)
            ),
        shape = RoundedCornerShape(30.dp),
        color = Color(0xFFFCF8FF)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = icon,
                    contentDescription = label,
                    tint = Color(0xFF2B0B5F),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = label,
                    color = Color(0xFF2B0B5F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
