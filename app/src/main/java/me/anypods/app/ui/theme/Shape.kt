package me.anypods.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material Design 3 Expressive Shape Tokens
// https://m3.material.io/styles/shape/shape-scale-tokens
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val PillShape = RoundedCornerShape(50)
val SquircleShapeLarge = RoundedCornerShape(28.dp)
val SquircleShapeExtraLarge = RoundedCornerShape(32.dp)
val SquircleShapeMedium = RoundedCornerShape(16.dp)
