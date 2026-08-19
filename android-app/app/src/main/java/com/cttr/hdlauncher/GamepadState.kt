package com.cttr.hdlauncher

/**
 * Snapshot of the on-screen gamepad state.
 * Analog axes are normalized to [-1, 1]; up is negative Y, left is negative X.
 */
data class GamepadState(
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val buttons: Set<GameButton> = emptySet()
) {
    fun isPressed(b: GameButton): Boolean = buttons.contains(b)
}

enum class GameButton {
    UP, DOWN, LEFT, RIGHT,
    CROSS, CIRCLE, TRIANGLE, SQUARE,
    L1, R1, L2, R2,
    START, SELECT
}
