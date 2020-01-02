/*
 * Copyright 2019 Dash Core Group
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

package de.schildbach.wallet.ui

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import de.schildbach.wallet.WalletApplication

@SuppressLint("Registered")
open class SessionActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SESSION_PIN = "extra_session_pin"
    }

    protected fun saveSessionPin(pin: String?) {
        this.intent.putExtra(EXTRA_SESSION_PIN, pin)
    }

    protected fun resetSessionPin() {
        this.intent.removeExtra(EXTRA_SESSION_PIN)
    }

    override fun startActivity(intent: Intent) {
        intent.putExtra(EXTRA_SESSION_PIN, this.intent.getStringExtra(EXTRA_SESSION_PIN))
        super.startActivity(intent)
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        intent.putExtra(EXTRA_SESSION_PIN, this.intent.getStringExtra(EXTRA_SESSION_PIN))
        super.startActivityForResult(intent, requestCode)
    }

    fun getSessionPin(): String? {
        return intent.getStringExtra(EXTRA_SESSION_PIN)
    }

    override fun onUserInteraction() {
        (application as WalletApplication).resetAutoLogoutTimer()
    }
}
