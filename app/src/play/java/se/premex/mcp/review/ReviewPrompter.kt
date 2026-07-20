package se.premex.mcp.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Play-flavor implementation backed by the Play In-App Review API.
 */
object ReviewPrompter {
    fun requestReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                manager.launchReviewFlow(activity, request.result)
            }
        }
    }
}
