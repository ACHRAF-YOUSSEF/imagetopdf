package tech.youssefachraf.image_to_pdf.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tech.youssefachraf.image_to_pdf.utils.PdfFileInfo
import java.util.Locale

private val Context.appPreferencesDataStore by preferencesDataStore(name = "imagetopdf_prefs")

/**
 * Persists user-starred PDFs, the linked SAF storage folder URI, and banner dismissal locally
 * using Jetpack Preferences DataStore.
 */
class FavoritesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.appPreferencesDataStore)

    private val favoritesKey = stringSetPreferencesKey("favorite_pdf_uris")
    private val linkedTreeUriKey = stringPreferencesKey("linked_pdf_tree_uri")
    private val dismissedBannerKey = booleanPreferencesKey("dismissed_folder_banner")

    val favoriteUris: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[favoritesKey] ?: emptySet()
    }

    val linkedTreeUri: Flow<String?> = dataStore.data.map { prefs ->
        prefs[linkedTreeUriKey]
    }

    val isFolderBannerDismissed: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[dismissedBannerKey] ?: false
    }

    suspend fun toggleFavorite(uriString: String, pdfName: String? = null) {
        dataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: emptySet()
            val nameKey = toNameKey(pdfName)
            val wasFavorite = uriString in current || (nameKey != null && nameKey in current)
            prefs[favoritesKey] = if (wasFavorite) {
                current - setOfNotNull(uriString, nameKey)
            } else {
                current + (nameKey ?: uriString)
            }
        }
    }

    suspend fun renameFavorite(oldUriString: String, oldName: String, newName: String) {
        dataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: return@edit
            val oldNameKey = toNameKey(oldName)
            val wasFavorite = oldUriString in current || (oldNameKey != null && oldNameKey in current)
            if (!wasFavorite) return@edit
            val newNameKey = toNameKey(newName) ?: return@edit
            prefs[favoritesKey] = (current - setOfNotNull(oldUriString, oldNameKey)) + newNameKey
        }
    }

    suspend fun removeFavorite(uriString: String, pdfName: String? = null) {
        dataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: return@edit
            val keysToRemove = setOfNotNull(uriString, toNameKey(pdfName))
            val updated = current - keysToRemove
            if (updated.size != current.size) {
                prefs[favoritesKey] = updated
            }
        }
    }

    suspend fun setLinkedTreeUri(uriString: String?) {
        dataStore.edit { prefs ->
            if (uriString.isNullOrBlank()) {
                prefs.remove(linkedTreeUriKey)
            } else {
                prefs[linkedTreeUriKey] = uriString
            }
        }
    }

    suspend fun setFolderBannerDismissed(dismissed: Boolean) {
        dataStore.edit { prefs ->
            prefs[dismissedBannerKey] = dismissed
        }
    }

    private fun toNameKey(pdfName: String?): String? =
        pdfName?.trim()?.takeIf { it.isNotEmpty() }?.let { "$NAME_PREFIX${it.lowercase(Locale.ROOT)}" }

    companion object {
        private const val NAME_PREFIX = "name:"

        fun isFavorite(pdf: PdfFileInfo, favoriteKeys: Set<String>): Boolean =
            pdf.uri.toString() in favoriteKeys ||
                "$NAME_PREFIX${pdf.name.lowercase(Locale.ROOT)}" in favoriteKeys
    }
}
