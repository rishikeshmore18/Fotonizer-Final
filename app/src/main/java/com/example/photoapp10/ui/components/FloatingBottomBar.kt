package com.example.photoapp10.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(112.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BottomBarPillItem(
                icon = leftIcon,
                label = leftLabel,
                onClick = onLeftClick,
                modifier = Modifier.width(132.dp)
            )

            Spacer(modifier = Modifier.width(128.dp))

            BottomBarPillItem(
                icon = rightIcon,
                label = rightLabel,
                onClick = onRightClick,
                modifier = Modifier.width(132.dp)
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 6.dp)
                .size(84.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape
                ),
            shape = CircleShape,
            color = Color.White
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = Color(0xFF7B52D3)
                ) {
                    IconButton(
                        onClick = onCenterClick,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = centerIcon,
                            contentDescription = centerLabel,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingCopyMoveBar(
    onCancelClick: () -> Unit,
    menuIcon: Painter,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FloatingCopyMoveAction(
                label = "Cancel",
                onClick = onCancelClick,
                primary = false,
                modifier = Modifier.width(86.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions
            )

            Surface(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(50),
                color = Color(0xFFF0EEFA),
                tonalElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = menuIcon,
                            contentDescription = "Menu",
                            tint = Color(0xFF2B0B5F),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    menuContent()
                }
            }
        }
    }
}

@Composable
fun FloatingCopyMoveAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true
) {
    Surface(
        modifier = modifier
            .width(76.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(50),
        color = if (primary) Color(0xFF4D5AC8) else Color(0xFFF0EEFA),
        tonalElevation = 0.dp,
        shadowElevation = if (primary) 3.dp else 0.dp,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (primary) Color.White else Color(0xFF2B0B5F),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            )
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
            .fillMaxHeight(),
        shape = RoundedCornerShape(50),
        color = Color(0xFFF0EEFA),
        tonalElevation = 0.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = icon,
                    contentDescription = label,
                    tint = Color(0xFF2B0B5F),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    color = Color(0xFF2B0B5F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
