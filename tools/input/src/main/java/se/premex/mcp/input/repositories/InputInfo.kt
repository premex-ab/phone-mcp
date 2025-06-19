package se.premex.mcp.input.repositories

data class InputInfo(
    val lastClickX: Int = 0,
    val lastClickY: Int = 0,
    val lastOperationSuccessful: Boolean = false
)
