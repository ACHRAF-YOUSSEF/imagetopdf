package tech.youssefachraf.image_to_pdf.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPreferencesDataStore by preferencesDataStore(name = "imagetopdf_prefs")

/**
 * Persists user-starred PDF URIs locally using Jetpack Preferences DataStore.
 */
class FavoritesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.appPreferencesDataStore)

    private val favoritesKey = stringSetPreferencesKey("favorite_pdf_uris")

    val favoriteUris: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[favoritesKey] ?: emptySet()
    }

    suspend fun toggleFavorite(uriString: String) {
        dataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: emptySet()
            prefs[favoritesKey] = if (uriString in current) {
                current - uriString
            } else {
                current + uriString
            }
        }
    }

    suspend fun removeFavorite(uriString: String) {
        dataStore.edit { prefs ->
            val current = prefs[favoritesKey] ?: return@edit
            if (uriString in current) {
                prefs[favoritesKey] = current - uriString
            }
        }
    }
}
