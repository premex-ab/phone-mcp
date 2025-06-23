package se.premex.mcp.screenshot.repositories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer


interface BitmapStorage {
    suspend fun store(
        buffer: ByteBuffer,
        mWidth: Int,
        mHeight: Int,
        rowPadding: Int,
        pixelStride: Int
    )

    suspend fun retrieve(): Bitmap?
}

class InMemoryBitmapStorage() : BitmapStorage {
    var latestBitmap : Bitmap? = null
    override suspend fun store(
        buffer: ByteBuffer,
        mWidth: Int,
        mHeight: Int,
        rowPadding: Int,
        pixelStride: Int
    ) {
        try {
            latestBitmap?.recycle()

            // create bitmap
            val bitmap = createBitmap(mWidth + rowPadding / pixelStride, mHeight)
            bitmap.copyPixelsFromBuffer(buffer)

            // store the latest bitmap in memory
            latestBitmap = bitmap

            //Log.e("InMemoryBitmapStorage", "Stored bitmap of size: ${bitmap.byteCount} bytes")
        } catch (e: Exception) {
            Log.e("InMemoryBitmapStorage", "Error storing bitmap: ${e.message}")
        }
    }

    override suspend fun retrieve(): Bitmap? {
        return latestBitmap?.let {
            // Return a copy to avoid external modifications
            it.copy(it.config!!, true)
        } ?: run {
            Log.e("InMemoryBitmapStorage", "No bitmap stored in memory")
            null
        }
    }

}
class DiskBitmapStorage(
    context: Context
) : BitmapStorage {

    companion object {
        private const val TAG = "DiskBitmapStorage"
        private var IMAGES_PRODUCED = 0
    }

    private var mStoreDir: String? = null

    init {
        val externalFilesDir = context.getExternalFilesDir(null)
        if (externalFilesDir != null) {
            mStoreDir = externalFilesDir.getAbsolutePath() + "/screenshots/"
            val storeDirectory = File(mStoreDir)
            if (!storeDirectory.exists()) {
                val success = storeDirectory.mkdirs()
                if (!success) {
                    Log.e(TAG, "failed to create file storage directory.")
                    // stopSelf()
                }
            }
        } else {
            Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.")
            // stopSelf()
        }

    }

    var fos: FileOutputStream? = null

    override suspend fun store(
        buffer: ByteBuffer,
        mWidth: Int,
        mHeight: Int,
        rowPadding: Int,
        pixelStride: Int
    ) {

        var fos: FileOutputStream? = null
        var bitmap: Bitmap? = null
        try {
            // create bitmap
            bitmap = createBitmap(mWidth + rowPadding / pixelStride, mHeight)
            bitmap.copyPixelsFromBuffer(buffer)

            // write bitmap to a file
            fos = FileOutputStream("$mStoreDir/myscreen_$IMAGES_PRODUCED.png")
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)

            IMAGES_PRODUCED++
            Log.e(TAG, "captured image: $IMAGES_PRODUCED")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (fos != null) {
                try {
                    fos.close()
                } catch (ioe: IOException) {
                    ioe.printStackTrace()
                }
            }

            bitmap?.recycle()
        }

    }

    override suspend fun retrieve(): Bitmap? {
        val file = File("$mStoreDir/myscreen_${IMAGES_PRODUCED - 1}.png")
        return if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.path)
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving bitmap: ${e.message}")
                null
            }
        } else {
            Log.e(TAG, "File does not exist: ${file.path}")
            null
        }
    }
}