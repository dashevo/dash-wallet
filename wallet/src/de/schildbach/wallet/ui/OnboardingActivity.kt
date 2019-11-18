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
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import de.schildbach.wallet.WalletApplication
import de.schildbach.wallet.ui.preference.PinRetryController
import de.schildbach.wallet_test.R
import kotlinx.android.synthetic.main.activity_onboarding.*
import kotlinx.android.synthetic.main.activity_onboarding_perm_lock.*
import org.dash.wallet.common.ui.DialogBuilder


class OnboardingActivity : RestoreFromFileActivity() {

    private lateinit var viewModel: OnboardingViewModel

    private lateinit var walletApplication: WalletApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PinRetryController.handleLockedForever(this, false)) {
            setContentView(R.layout.activity_onboarding_perm_lock)
            getStatusBarHeightPx()
            hideSlogan()
            close_app.setOnClickListener {
                finish()
            }
            wipe_wallet.setOnClickListener {
                finish()
            }
            return
        }

        setContentView(R.layout.activity_onboarding)
        slogan.setPadding(slogan.paddingLeft, slogan.paddingTop, slogan.paddingRight, getStatusBarHeightPx())

        viewModel = ViewModelProviders.of(this).get(OnboardingViewModel::class.java)

        walletApplication = (application as WalletApplication)
        if (walletApplication.walletFileExists()) {
            regularFlow()
        } else {
            if (walletApplication.wallet == null) {
                onboarding()
            } else {
                if (walletApplication.wallet.isEncrypted) {
                    walletApplication.fullInitialization()
                    regularFlow()
                } else {
                    startActivity(SetPinActivity.createIntent(this, R.string.set_pin_create_new_wallet))
                }
            }
        }
    }

    private fun regularFlow() {
        try {
            startActivity(LockScreenActivity.createIntent(this))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (x: Exception) {
            fatal_error_message.visibility = View.VISIBLE
        }
    }

    private fun onboarding() {
        initView()
        initViewModel()
        showButtonsDelayed()
    }

    private fun initView() {
        create_new_wallet.setOnClickListener {
            viewModel.createNewWallet()
        }
        recovery_wallet.setOnClickListener {
            walletApplication.initEnvironmentIfNeeded()
            RestoreWalletFromSeedDialogFragment.show(supportFragmentManager)
        }
        restore_wallet.setOnClickListener {
            restoreWalletFromFile()
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun initViewModel() {
        viewModel.showToastAction.observe(this, Observer {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        })
        viewModel.showRestoreWalletFailureAction.observe(this, Observer {
            val message = when {
                TextUtils.isEmpty(it.message) -> it.javaClass.simpleName
                else -> it.message!!
            }
            val dialog = DialogBuilder.warn(this, R.string.import_export_keys_dialog_failure_title)
            dialog.setMessage(getString(R.string.import_keys_dialog_failure, message))
            dialog.setPositiveButton(R.string.button_dismiss, null)
            dialog.setNegativeButton(R.string.button_retry) { _, _ ->
                RestoreWalletFromSeedDialogFragment.show(supportFragmentManager)
            }
            dialog.show()
        })
        viewModel.startActivityAction.observe(this, Observer {
            startActivity(it)
        })
    }

    private fun showButtonsDelayed() {

        Handler().postDelayed({
            hideSlogan()
            findViewById<LinearLayout>(R.id.buttons).visibility = View.VISIBLE
        }, 1000)
    }

    private fun hideSlogan() {
        val sloganDrawable = (window.decorView.background as LayerDrawable).getDrawable(1)
        sloganDrawable.mutate().alpha = 0
    }

    fun restoreWalletFromSeed(words: MutableList<String>) {
        viewModel.restoreWalletFromSeed(words)
    }

    private fun getStatusBarHeightPx(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
}
