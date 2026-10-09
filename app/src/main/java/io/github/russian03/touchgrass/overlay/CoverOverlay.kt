package io.github.russian03.touchgrass.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi

/**
 * Tapa una zona de la pantalla con [CoverScreen]. Antes de mostrarse hace una captura de
 * esa zona para dibujarla desenfocada debajo del candado. La captura se reduce al momento,
 * vive solo en memoria y se descarta al ocultar la tapa.
 */
class CoverOverlay(private val service: AccessibilityService) {

    private val window = OverlayWindow(service)
    private var bounds: Rect? = null
    private var capturing = false

    val isShowing: Boolean get() = window.isShowing || capturing

    fun show(area: Rect) {
        if (window.isShowing) {
            if (area != bounds) {
                bounds = area
                window.move(area, focusable = false)
            }
            return
        }
        if (capturing) return

        bounds = area
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            capturing = true
            captureBackdrop(area) { backdrop ->
                capturing = false
                // Puede que el usuario ya se haya ido mientras se hacía la captura.
                if (bounds == area) attach(area, backdrop)
            }
        } else {
            attach(area, backdrop = null)
        }
    }

    fun hide() {
        bounds = null
        window.hide()
    }

    private fun attach(area: Rect, backdrop: Bitmap?) {
        window.show(area, focusable = false) { CoverScreen(backdrop) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureBackdrop(area: Rect, onResult: (Bitmap?) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val backdrop =
                        screenshot.hardwareBuffer.use { buffer ->
                            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.let { shrink(it, area) }
                        }
                    onResult(backdrop)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "No se pudo capturar la pantalla (código $errorCode)")
                    onResult(null)
                }
            },
        )
    }

    /**
     * Recorta [area] y la reduce mucho: al estirarla después con filtrado bilineal ya queda
     * desenfocada, incluso en versiones sin efectos de desenfoque.
     */
    private fun shrink(hardware: Bitmap, area: Rect): Bitmap? {
        val full = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        hardware.recycle()
        val clipped = Rect(area).apply { intersect(0, 0, full.width, full.height) }
        if (clipped.isEmpty) return null
        val crop = Bitmap.createBitmap(full, clipped.left, clipped.top, clipped.width(), clipped.height())
        return Bitmap.createScaledBitmap(
            crop,
            (crop.width / SHRINK_FACTOR).coerceAtLeast(1),
            (crop.height / SHRINK_FACTOR).coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val TAG = "TouchGrass"
        const val SHRINK_FACTOR = 12
    }
}
