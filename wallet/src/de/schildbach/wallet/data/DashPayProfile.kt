/*
 * Copyright 2020 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.schildbach.wallet.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.dashevo.dpp.document.Document
import org.dashevo.dpp.identifier.Identifier

@Parcelize
@Entity(tableName = "dashpay_profile")
data class DashPayProfile(@PrimaryKey val userId: String,
                          val username: String,
                          var displayName: String = "",
                          var publicMessage: String = "",
                          var avatarUrl: String = "",
                          val createdAt: Long = 0,
                          val updatedAt: Long = 0) : Parcelable {
    companion object {

        private fun getField(document: Document, field: String, defaultValue: String = ""): String {
            return if (document.data.containsKey(field)) {
                document.data[field] as String
            } else {
                defaultValue
            }
        }

        fun fromDocument(document: Document, username: String): DashPayProfile? {

            val displayName = getField(document, "displayName")
            val publicMessage = getField(document, "publicMessage")
            val avatarUrl = getField(document, "avatarUrl")

            return DashPayProfile(document.ownerId.toString(),
                    username,
                    displayName,
                    publicMessage,
                    avatarUrl,
                    if (document.createdAt != null) document.createdAt!! else 0L,
                    if (document.updatedAt != null) document.updatedAt!! else 0L)
        }
    }

    @IgnoredOnParcel
    @delegate:Ignore
    val userIdentifier by lazy {
        Identifier.from(userId)
    }
    @IgnoredOnParcel
    @delegate:Ignore
    val rawUserId by lazy {
        userIdentifier.toBuffer()
    }
}
