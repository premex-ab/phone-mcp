package se.premex.mcp.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Play Billing for the remote-access subscription.
 * A purchase is only trusted once the relay has verified its token against
 * the Play Developer API; [acknowledge] is called after that verification.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "remote_access"
        private const val TAG = "BillingManager"
    }

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)

    /** Null until Play answers — e.g. on sideloaded builds without Play. */
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _pendingPurchase = MutableStateFlow<Purchase?>(null)

    /** Latest purchase the relay has not verified yet. */
    val pendingPurchase: StateFlow<Purchase?> = _pendingPurchase.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryExistingPurchases()
                } else {
                    Log.i(TAG, "Billing unavailable: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // Billing 8: the callback carries a QueryProductDetailsResult
                _productDetails.value = details.productDetailsList.firstOrNull()
            }
        }
    }

    /** Re-surface an existing subscription, e.g. after a reinstall. */
    private fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.firstOrNull { PRODUCT_ID in it.products }?.let {
                    _pendingPurchase.value = it
                }
            }
        }
    }

    /**
     * Opens the Play purchase sheet for the given base plan ("monthly"/"yearly"),
     * falling back to the first offer. False when billing is unavailable.
     */
    fun launchPurchase(activity: Activity, basePlanId: String?): Boolean {
        val details = _productDetails.value ?: return false
        val offers = details.subscriptionOfferDetails ?: return false
        val offerToken = (offers.firstOrNull { it.basePlanId == basePlanId } ?: offers.firstOrNull())
            ?.offerToken ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode ==
            BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.firstOrNull { PRODUCT_ID in it.products }?.let {
                _pendingPurchase.value = it
            }
        }
    }

    /** Must be called within 3 days of purchase or Play refunds it. */
    fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { result ->
            Log.i(TAG, "Acknowledge result: ${result.responseCode}")
        }
    }

    fun clearPending() {
        _pendingPurchase.value = null
    }
}
