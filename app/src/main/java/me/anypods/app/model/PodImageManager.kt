package me.anypods.app.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * PodImageManager
 * Automatically discovers, downloads, caches, and provides high-resolution product
 * photos from the internet for ANY Bluetooth earbud/headphone model in the world.
 */
object PodImageManager {
    private const val TAG = "PodImageManager"
    private const val DIR_NAME = "device_images"
    private val scope = CoroutineScope(Dispatchers.IO)

    // Flow that emits whenever an image is downloaded or updated to trigger Compose recomposition
    private val _imageUpdateFlow = MutableStateFlow(0L)
    val imageUpdateFlow: StateFlow<Long> = _imageUpdateFlow.asStateFlow()

    private val _isFetchingFlow = MutableStateFlow(false)
    val isFetchingFlow: StateFlow<Boolean> = _isFetchingFlow.asStateFlow()

    private val _lastStatusMessage = MutableStateFlow<String?>(null)
    val lastStatusMessage: StateFlow<String?> = _lastStatusMessage.asStateFlow()

    fun sanitizeDeviceName(name: String): String {
        return name.lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(40)
    }

    fun getDeviceImageFile(context: Context, deviceName: String): File? {
        if (deviceName.isBlank() || deviceName == "Wireless Earbuds") return null
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${sanitizeDeviceName(deviceName)}.png")
        return if (file.exists() && file.length() > 2048) file else null
    }

    fun removeWhiteBackground(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val corner0 = pixels[0]
        val rBg = (corner0 shr 16) and 0xFF
        val gBg = (corner0 shr 8) and 0xFF
        val bBg = corner0 and 0xFF
        val isLightBg = rBg > 210 && gBg > 210 && bBg > 210

        if (isLightBg) {
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                if (r > 238 && g > 238 && b > 238) {
                    pixels[i] = 0x00000000
                } else if (r > 215 && g > 215 && b > 215) {
                    val minVal = minOf(r, minOf(g, b))
                    val alpha = (255 * (1f - (minVal - 215f) / 23f)).toInt().coerceIn(0, 255)
                    pixels[i] = (alpha shl 24) or (c and 0x00FFFFFF)
                }
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    fun getDeviceBitmap(context: Context, deviceName: String): Bitmap? {
        val file = getDeviceImageFile(context, deviceName) ?: return null
        return try {
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            if (bmp.getPixel(0, 0) != 0) {
                val transparent = removeWhiteBackground(bmp)
                try {
                    FileOutputStream(file).use { fos ->
                        transparent.compress(Bitmap.CompressFormat.PNG, 95, fos)
                    }
                } catch (e: Exception) {
                    // Ignore save error
                }
                transparent
            } else {
                bmp
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap for $deviceName", e)
            null
        }
    }

    fun deleteDeviceImage(context: Context, deviceName: String) {
        val file = getDeviceImageFile(context, deviceName)
        file?.delete()
        _imageUpdateFlow.value = System.currentTimeMillis()
    }

    fun autoFetchIfMissing(context: Context, deviceName: String) {
        if (deviceName.isBlank() || deviceName == "Wireless Earbuds") return
        val existing = getDeviceImageFile(context, deviceName)
        if (existing == null && !_isFetchingFlow.value) {
            fetchDeviceImage(context, deviceName)
        }
    }

    fun fetchDeviceImage(
        context: Context,
        deviceName: String,
        searchQuery: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (deviceName.isBlank() || deviceName == "Wireless Earbuds") {
            onComplete?.invoke(false)
            return
        }

        scope.launch {
            _isFetchingFlow.value = true
            _lastStatusMessage.value = "Searching internet for '$deviceName' photo..."
            var success = false

            try {
                val query = (searchQuery ?: "$deviceName earbuds").trim()
                val encoded = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://www.bing.com/images/async?q=$encoded&first=0&count=12&mmasync=1"

                val conn = URL(searchUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                val html = conn.inputStream.bufferedReader().use { it.readText() }

                val pattern = Pattern.compile("murl&quot;:&quot;(https?://[^&\"]+?)&quot;")
                val matcher = pattern.matcher(html)
                val candidateUrls = mutableListOf<String>()
                while (matcher.find() && candidateUrls.size < 8) {
                    matcher.group(1)?.let { candidateUrls.add(it) }
                }

                Log.d(TAG, "Found ${candidateUrls.size} candidate images for query '$query'")

                for (imgUrl in candidateUrls) {
                    try {
                        val imgConn = URL(imgUrl).openConnection() as HttpURLConnection
                        imgConn.connectTimeout = 6000
                        imgConn.readTimeout = 6000
                        imgConn.setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                        )
                        // ponytail: 5MB memory cap to prevent OOM on malicious or high-res image downloads
                        val MAX_IMAGE_BYTES = 5 * 1024 * 1024
                        val contentLength = imgConn.contentLength
                        if (contentLength > MAX_IMAGE_BYTES) {
                            Log.w(TAG, "Skipping image: Content-Length $contentLength exceeds 5MB limit")
                            continue
                        }

                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var totalRead = 0
                        var exceeded = false
                        imgConn.inputStream.use { stream ->
                            var n: Int
                            while (stream.read(chunk).also { n = it } != -1) {
                                totalRead += n
                                if (totalRead > MAX_IMAGE_BYTES) {
                                    exceeded = true
                                    break
                                }
                                buffer.write(chunk, 0, n)
                            }
                        }

                        if (exceeded) {
                            Log.w(TAG, "Skipping image: Stream exceeded 5MB cap")
                            continue
                        }

                        val bytes = buffer.toByteArray()

                        if (bytes.size > 3000) {
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null && bitmap.width >= 120 && bitmap.height >= 120) {
                                val cleanBitmap = removeWhiteBackground(bitmap)
                                val dir = File(context.filesDir, DIR_NAME)
                                if (!dir.exists()) dir.mkdirs()
                                val outFile = File(dir, "${sanitizeDeviceName(deviceName)}.png")
                                FileOutputStream(outFile).use { fos ->
                                    cleanBitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
                                }
                                Log.i(TAG, "Successfully downloaded, stripped background & cached image for '$deviceName' (${bytes.size} bytes)")
                                success = true
                                _lastStatusMessage.value = "Transparent photo saved for $deviceName!"
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Candidate image failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed searching internet for image", e)
                _lastStatusMessage.value = "Failed fetching photo: ${e.message}"
            } finally {
                _isFetchingFlow.value = false
                if (success) {
                    _imageUpdateFlow.value = System.currentTimeMillis()
                }
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(success)
                }
            }
        }
    }
}
