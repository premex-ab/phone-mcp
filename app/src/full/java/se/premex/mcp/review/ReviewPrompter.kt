package se.premex.mcp.review

import android.app.Activity

/**
 * Full-flavor no-op: the in-app review flow is only available through
 * Google Play.
 */
object ReviewPrompter {
    fun requestReview(activity: Activity) = Unit
}
