/*
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of BetterAIChat2.
 *
 * BetterAIChat2 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * BetterAIChat2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * BetterAIChat2. If not, see <https://www.gnu.org/licenses/>.
 */

package com.verlintas.baic2.tools.geo

import android.content.Context
import com.verlintas.baic2.core.model.GazetteerIndex
import com.verlintas.baic2.core.model.GazetteerPlace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Offline China gazetteer: provinces, cities, counties, towns and larger
 * villages with WGS-84 coordinates (GeoNames, CC BY 4.0). Loaded lazily from
 * the bundled asset; no Geocoder, no network, no Play Services.
 */
@Singleton
class Gazetteer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mutex = Mutex()

    @Volatile
    private var index: GazetteerIndex? = null

    suspend fun nearest(
        latitude: Double,
        longitude: Double,
        limit: Int = 4,
        kinds: IntRange = GazetteerPlace.KIND_PROVINCE..GazetteerPlace.KIND_VILLAGE,
    ): List<GazetteerPlace> = withContext(Dispatchers.IO) {
        index().nearest(latitude, longitude, limit, kinds)
    }

    suspend fun index(): GazetteerIndex {
        index?.let { return it }
        return mutex.withLock {
            index ?: load().also { index = it }
        }
    }

    private fun load(): GazetteerIndex {
        val places = ArrayList<GazetteerPlace>(32_000)
        context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size < 6) return@forEachLine
                places += GazetteerPlace(
                    name = parts[0],
                    pinyin = parts[1],
                    latitude = parts[2].toDoubleOrNull() ?: return@forEachLine,
                    longitude = parts[3].toDoubleOrNull() ?: return@forEachLine,
                    kind = parts[4].toIntOrNull() ?: return@forEachLine,
                    population = parts[5].toLongOrNull() ?: 0L,
                )
            }
        }
        return GazetteerIndex(places)
    }

    companion object {
        const val ASSET_PATH = "gazetteer/cn-places.tsv"
        const val ATTRIBUTION = "GeoNames, CC BY 4.0"
    }
}
