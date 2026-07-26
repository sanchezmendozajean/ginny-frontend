package com.ginny.frontend.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ginny.frontend.data.model.SystemCatalog
import com.ginny.frontend.data.model.SystemId
import com.ginny.frontend.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val ASSETS_DIR_NAME = "Assets"
private const val ICONS_DIR_NAME = "icons"
private const val SYSTEM_LOGO_FILE_NAME = "system.png"
private const val PROJECT_LOGOS_ASSET_DIR = "system-logos"
private const val INTERFACE_DIR_NAME = "Interface"
private const val BACKGROUND_MUSIC_DIR_NAME = "Background Music"

/**
 * Creates the `Assets/<system>/{icons/, system.png}` and `Interface/Background Music/` structure
 * inside the user-chosen "Carpeta de Usuario" the first time it's selected (or fills in whatever
 * is missing on re-selection), without ever touching an existing `icons/` folder or any custom
 * music the user already dropped in.
 *
 * Default `system.png` logos are sourced from `app/src/main/assets/system-logos/<folder>.png` when
 * present in the project (drop real artwork there and rebuild); otherwise a simple generated
 * placeholder tile is written so the app is never left without a logo.
 */
class StorageBootstrapper(private val context: Context) {

    suspend fun ensureUserFolderStructure(userTreeUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = SafUtils.treeDocument(context, userTreeUri)
                ?: throw IOException("No se pudo abrir la carpeta de Usuario seleccionada")
            val assetsDir = SafUtils.getOrCreateDirectory(root, ASSETS_DIR_NAME)
                ?: throw IOException("No se pudo crear la carpeta Assets")

            for (system in SystemCatalog.all) {
                val systemDir = SafUtils.getOrCreateDirectory(assetsDir, system.id.folderName) ?: continue
                SafUtils.getOrCreateDirectory(systemDir, ICONS_DIR_NAME)
                if (SafUtils.findChildFileIgnoreCase(systemDir, SYSTEM_LOGO_FILE_NAME) == null) {
                    writeDefaultLogo(systemDir, system.id)
                }
            }

            val interfaceDir = SafUtils.getOrCreateDirectory(root, INTERFACE_DIR_NAME)
                ?: throw IOException("No se pudo crear la carpeta Interface")
            SafUtils.getOrCreateDirectory(interfaceDir, BACKGROUND_MUSIC_DIR_NAME)
            Unit
        }
    }

    private fun writeDefaultLogo(systemDir: DocumentFile, systemId: SystemId) {
        val bytes = readBundledLogo(systemId) ?: renderPlaceholderLogo(systemId)
        val file = systemDir.createFile("image/png", SYSTEM_LOGO_FILE_NAME) ?: return
        context.contentResolver.openOutputStream(file.uri)?.use { out ->
            out.write(bytes)
        }
    }

    private fun readBundledLogo(systemId: SystemId): ByteArray? = runCatching {
        context.assets.open("$PROJECT_LOGOS_ASSET_DIR/${systemId.folderName}.png").use { it.readBytes() }
    }.getOrNull()

    private fun renderPlaceholderLogo(systemId: SystemId): ByteArray {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val hue = (systemId.ordinal * 47) % 360
        val background = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.92f))
        val foreground = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.55f))

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 48f, 48f, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foreground
            textAlign = Paint.Align.CENTER
            textSize = size * 0.34f
            isFakeBoldText = true
        }
        val label = systemId.folderName.take(4).uppercase()
        val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, size / 2f, textY, textPaint)

        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }
}
