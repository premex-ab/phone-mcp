package se.premex.mcp.input.repositories

interface InputRepository {
    /**
     * Performs a click at the specified screen coordinates
     * @param x The X-coordinate on the screen
     * @param y The Y-coordinate on the screen
     * @return True if the click was performed successfully, false otherwise
     */
    fun performClick(x: Int, y: Int): Boolean

    /**
     * Gets information about the last input operation
     * @return InputInfo containing details about the last input operation
     */
    fun getInputInfo(): InputInfo
}

