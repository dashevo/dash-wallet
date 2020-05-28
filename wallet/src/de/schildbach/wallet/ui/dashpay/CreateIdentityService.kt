package de.schildbach.wallet.ui.dashpay

import android.content.Context
import android.content.Intent
import android.os.Handler
import androidx.lifecycle.LifecycleService
import de.schildbach.wallet.Constants
import de.schildbach.wallet.WalletApplication
import de.schildbach.wallet.data.BlockchainIdentityData
import de.schildbach.wallet.data.BlockchainIdentityData.CreationState
import de.schildbach.wallet.ui.security.SecurityGuard
import de.schildbach.wallet.ui.send.DecryptSeedTask
import de.schildbach.wallet.ui.send.DeriveKeyTask
import kotlinx.coroutines.*
import org.bitcoinj.core.RejectMessage
import org.bitcoinj.core.RejectedTransactionException
import org.bitcoinj.core.TransactionConfidence
import org.bitcoinj.crypto.KeyCrypterException
import org.bitcoinj.evolution.CreditFundingTransaction
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.Wallet
import org.bouncycastle.crypto.params.KeyParameter
import org.dashevo.dashpay.BlockchainIdentity
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class CreateIdentityService : LifecycleService() {

    companion object {
        private val log = LoggerFactory.getLogger(CreateIdentityService::class.java)

        private const val ACTION_CREATE_IDENTITY = "org.dash.dashpay.action.CREATE_IDENTITY"

        private const val EXTRA_USERNAME = "org.dash.dashpay.extra.USERNAME"

        @JvmStatic
        fun createIntent(context: Context, username: String): Intent {
            return Intent(context, CreateIdentityService::class.java).apply {
                action = ACTION_CREATE_IDENTITY
                putExtra(EXTRA_USERNAME, username)
            }
        }
    }

    private val walletApplication by lazy { application as WalletApplication }
    private val platformRepo by lazy { PlatformRepo(walletApplication) }
    private lateinit var securityGuard: SecurityGuard

    private val createIdentityNotification by lazy { CreateIdentityNotification(this) }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    lateinit var blockchainIdentity: BlockchainIdentity
    lateinit var blockchainIdentityData: BlockchainIdentityData

    override fun onCreate() {
        super.onCreate()
        try {
            securityGuard = SecurityGuard()
        } catch (e: Exception) {
            log.error("unable to instantiate SecurityGuard", e)
            stopSelf()
            return
        }
        createIdentityNotification.startServiceForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent != null) {

            when (intent.action) {
                ACTION_CREATE_IDENTITY -> handleCreateIdentityAction(intent)
            }
        }

        return START_NOT_STICKY
    }

    private fun handleCreateIdentityAction(intent: Intent) {
        val username = intent.getStringExtra(EXTRA_USERNAME)

        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            GlobalScope.launch {
                log.error("[${blockchainIdentityData.creationState}(error)]", exception)
                platformRepo.updateCreationState(blockchainIdentityData, blockchainIdentityData.creationState, true)
                if (this@CreateIdentityService::blockchainIdentity.isInitialized) {
                    platformRepo.updateBlockchainIdentityData(blockchainIdentityData, blockchainIdentity)
                }
            }
        }

        serviceScope.launch(exceptionHandler) {
            createIdentity(username)
            stopSelf()
        }
    }

    private suspend fun createIdentity(username: String) {
        log.info("username registration starting")

        blockchainIdentityData = platformRepo.initBlockchainIdentityData(username)

        if (blockchainIdentityData.creationState != CreationState.NONE || blockchainIdentityData.creationStateError) {
            log.info("resuming identity creation process [${blockchainIdentityData.creationState}${if (blockchainIdentityData.creationStateError) "(error)" else ""}]")
        }

        val handler = Handler()
        val wallet = walletApplication.wallet
        val password = securityGuard.retrievePassword()

        val encryptionKey = deriveKey(handler, wallet, password)

        if (blockchainIdentityData.creationState <= CreationState.UPGRADING_WALLET) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.UPGRADING_WALLET)
            val seed = decryptSeed(handler, wallet, encryptionKey)
            platformRepo.addWalletAuthenticationKeysAsync(seed, encryptionKey)
        }

        val blockchainIdentity = platformRepo.initBlockchainIdentity(blockchainIdentityData, wallet)

        if (blockchainIdentityData.creationState <= CreationState.CREDIT_FUNDING_TX_CREATING) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.CREDIT_FUNDING_TX_CREATING, true)
            //
            // Step 2: Create and send the credit funding transaction
            //
            platformRepo.createCreditFundingTransactionAsync(blockchainIdentity, encryptionKey)
        }

        if (blockchainIdentityData.creationState <= CreationState.CREDIT_FUNDING_TX_SENDING) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.CREDIT_FUNDING_TX_SENDING)
            sendTransaction(blockchainIdentity.creditFundingTransaction!!)
        }

        if (blockchainIdentityData.creationState <= CreationState.CREDIT_FUNDING_TX_CONFIRMED) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.CREDIT_FUNDING_TX_CONFIRMED)
            // If the tx is in a block, seen by a peer, InstantSend lock, then it is considered confirmed
            platformRepo.updateBlockchainIdentityData(blockchainIdentityData, blockchainIdentity)
        }

        if (blockchainIdentityData.creationState <= CreationState.IDENTITY_REGISTERING) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.IDENTITY_REGISTERING)
            //
            // Step 3: Register the identity
            //
            platformRepo.registerIdentityAsync(blockchainIdentity, encryptionKey)
            platformRepo.updateBlockchainIdentityData(blockchainIdentityData, blockchainIdentity)
        }

        if (blockchainIdentityData.creationState <= CreationState.IDENTITY_REGISTERED) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.IDENTITY_REGISTERED)
            //
            // Step 3: Verify that the identity was registered
            //
            platformRepo.verifyIdentityRegisteredAsync(blockchainIdentity)
            platformRepo.updateBlockchainIdentityData(blockchainIdentityData, blockchainIdentity)
        }

        if (blockchainIdentityData.creationState <= CreationState.PREORDER_REGISTERING) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.PREORDER_REGISTERING)
            //
            // Step 4: Preorder the username
            //
            if (blockchainIdentity.currentUsername != username) {
                blockchainIdentity.addUsername(username)
            }
            platformRepo.preorderNameAsync(blockchainIdentity, encryptionKey)
            platformRepo.updateBlockchainIdentityData(blockchainIdentityData, blockchainIdentity)
        }

        if (blockchainIdentityData.creationState <= CreationState.PREORDER_REGISTERED) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.PREORDER_REGISTERED)
            //
            // Step 4: Verify that the username was preordered
            //
            platformRepo.isNamePreorderedAsync(blockchainIdentity)
            platformRepo.updateBlockchainIdentityData(blockchainIdentityData, blockchainIdentity)
        }

        if (blockchainIdentityData.creationState <= CreationState.USERNAME_REGISTERING) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.USERNAME_REGISTERING)
            //
            // Step 5: Register the username
            //
            platformRepo.registerNameAsync(blockchainIdentity, encryptionKey)
            platformRepo.updateBlockchainIdentityData(blockchainIdentityData, blockchainIdentity)
        }

        if (blockchainIdentityData.creationState <= CreationState.USERNAME_REGISTERED) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.USERNAME_REGISTERED)
            //
            // Step 5: Verify that the username was registered
            //
            platformRepo.isNameRegisteredAsync(blockchainIdentity)
            platformRepo.updateBlockchainIdentityData(blockchainIdentityData, blockchainIdentity)
        }

        if (blockchainIdentityData.creationState <= CreationState.DASHPAY_PROFILE_CREATING) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.DASHPAY_PROFILE_CREATING)
            // Step 6: Profile Creation
            platformRepo.createDashPayProfile(blockchainIdentity, encryptionKey)
            platformRepo.updateBlockchainIdentityData(blockchainIdentityData, blockchainIdentity)
        }

        if (blockchainIdentityData.creationState <= CreationState.DASHPAY_PROFILE_CREATED) {
            platformRepo.updateCreationState(blockchainIdentityData, CreationState.DASHPAY_PROFILE_CREATED)
            platformRepo.updateBlockchainIdentityData(blockchainIdentityData)
        }

        // aaaand we're done :)
        log.info("username registration complete")
    }

    /**
     * Wraps callbacks of DeriveKeyTask as Coroutine
     */
    private suspend fun deriveKey(handler: Handler, wallet: Wallet, password: String): KeyParameter {
        return suspendCoroutine { continuation ->
            object : DeriveKeyTask(handler, walletApplication.scryptIterationsTarget()) {

                override fun onSuccess(encryptionKey: KeyParameter, wasChanged: Boolean) {
                    continuation.resume(encryptionKey)
                }

                override fun onFailure(ex: KeyCrypterException?) {
                    log.error("unable to decrypt wallet", ex)
                    continuation.resumeWithException(ex as Throwable)
                }

            }.deriveKey(wallet, password)
        }
    }

    /**
     * Wraps callbacks of DecryptSeedTask as Coroutine
     */
    private suspend fun decryptSeed(handler: Handler, wallet: Wallet, encryptionKey: KeyParameter): DeterministicSeed {
        return suspendCoroutine { continuation ->
            object : DecryptSeedTask(handler) {
                override fun onSuccess(seed: DeterministicSeed) {
                    continuation.resume(seed)
                }

                override fun onBadPassphrase() {
                    continuation.resumeWithException(IOException("this should never happen in this scenario"))

                }
            }.decryptSeed(wallet.activeKeyChain.seed, wallet.keyCrypter, encryptionKey)
        }
    }

    /**
     * Send the credit funding transaction and wait for confirmation from other nodes that the
     * transaction was sent.  InstantSendLock, in a block or seen by more than one peer.
     *
     * Exceptions are returned in the case of a reject message (may not be sent with Dash Core 0.16)
     * or in the case of a double spend or some other error.
     *
     * @param cftx The credit funding transaction to send
     * @return True if successful
     */
    private suspend fun sendTransaction(cftx: CreditFundingTransaction): Boolean {
        log.info("Sending credit funding transaction: ${cftx.txId}")
        return suspendCoroutine { continuation ->
            cftx.confidence.addEventListener(object : TransactionConfidence.Listener {
                override fun onConfidenceChanged(confidence: TransactionConfidence?, reason: TransactionConfidence.Listener.ChangeReason?) {
                    when (reason) {
                        // If this transaction is in a block, then it has been sent successfully
                        TransactionConfidence.Listener.ChangeReason.DEPTH -> {
                            confidence!!.removeEventListener(this)
                            continuation.resumeWith(Result.success(true))
                        }
                        // If this transaction is InstantSend Locked, then it has been sent successfully
                        TransactionConfidence.Listener.ChangeReason.IX_TYPE -> {
                            if (confidence!!.isTransactionLocked) {
                                confidence.removeEventListener(this)
                                continuation.resumeWith(Result.success(true))
                            }
                        }
                        // If this transaction has been seen by more than 1 peer, then it has been sent successfully
                        TransactionConfidence.Listener.ChangeReason.SEEN_PEERS -> {
                            if (confidence!!.numBroadcastPeers() > 1) {
                                confidence.removeEventListener(this)
                                continuation.resumeWith(Result.success(true))
                            }
                        }
                        // If this transaction was rejected, then it was not sent successfully
                        TransactionConfidence.Listener.ChangeReason.REJECT -> {
                            if (confidence!!.hasRejections() && confidence.rejections.size >= 1) {
                                confidence.removeEventListener(this)
                                log.info("Error sending ${cftx.txId}: ${confidence.rejectedTransactionException.rejectMessage.reasonString}")
                                continuation.resumeWithException(confidence.rejectedTransactionException)
                            }
                        }
                        TransactionConfidence.Listener.ChangeReason.TYPE -> {
                            if (confidence!!.hasErrors()) {
                                confidence.removeEventListener(this)
                                val code = when (confidence.confidenceType) {
                                    TransactionConfidence.ConfidenceType.DEAD -> RejectMessage.RejectCode.INVALID
                                    TransactionConfidence.ConfidenceType.IN_CONFLICT -> RejectMessage.RejectCode.DUPLICATE
                                    else -> RejectMessage.RejectCode.OTHER
                                }
                                val rejectMessage = RejectMessage(Constants.NETWORK_PARAMETERS, code, confidence.transactionHash,
                                        "Credit funding transaction is dead or double-spent", "cftx-dead-or-double-spent")
                                log.info("Error sending ${cftx.txId}: ${rejectMessage.reasonString}")
                                continuation.resumeWithException(RejectedTransactionException(cftx, rejectMessage))
                            }
                        }
                    }
                }
            })
            walletApplication.broadcastTransaction(cftx)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
