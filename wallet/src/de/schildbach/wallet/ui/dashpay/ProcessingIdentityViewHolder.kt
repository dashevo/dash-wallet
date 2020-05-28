package de.schildbach.wallet.ui.dashpay

import android.graphics.drawable.AnimationDrawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import de.schildbach.wallet.data.BlockchainIdentityBaseData
import de.schildbach.wallet.data.BlockchainIdentityData
import de.schildbach.wallet_test.R
import kotlinx.android.synthetic.main.identity_creation_state.view.*

class ProcessingIdentityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(blockchainIdentityData: BlockchainIdentityBaseData) {
        if (blockchainIdentityData.creationStateError) {
            if (blockchainIdentityData.creationState == BlockchainIdentityData.CreationState.USERNAME_REGISTERING) {
                itemView.title.text = itemView.context.getString(R.string.processing_username_unavailable_title)
                itemView.subtitle.visibility = View.VISIBLE
                itemView.icon.setImageResource(R.drawable.ic_username_unavailable)
                itemView.retry_icon.visibility = View.GONE
                itemView.forward_arrow.visibility = View.VISIBLE
            } else {
                itemView.title.text = itemView.context.getString(R.string.processing_error_title)
                itemView.subtitle.visibility = View.GONE
                itemView.icon.setImageResource(R.drawable.ic_error)
                itemView.retry_icon.visibility = View.VISIBLE
                itemView.forward_arrow.visibility = View.GONE
            }
        } else {
            itemView.title.text = itemView.context.getString(R.string.processing_home_title)
            itemView.subtitle.visibility = View.VISIBLE
            itemView.icon.setImageResource(R.drawable.identity_processing)
            (itemView.icon.drawable as AnimationDrawable).start()
            if (blockchainIdentityData.creationState == BlockchainIdentityData.CreationState.DASHPAY_PROFILE_CREATED) {
                itemView.icon.visibility = View.GONE
            } else {
                itemView.icon.visibility = View.VISIBLE
            }
            itemView.retry_icon.visibility = View.GONE
            itemView.forward_arrow.visibility = View.GONE
        }

        when (blockchainIdentityData.creationState) {
            BlockchainIdentityData.CreationState.UPGRADING_WALLET,
            BlockchainIdentityData.CreationState.CREDIT_FUNDING_TX_CREATING,
            BlockchainIdentityData.CreationState.CREDIT_FUNDING_TX_SENDING,
            BlockchainIdentityData.CreationState.CREDIT_FUNDING_TX_SENT,
            BlockchainIdentityData.CreationState.CREDIT_FUNDING_TX_CONFIRMED -> {
                itemView.progress.visibility = View.VISIBLE
                itemView.progress.progress = 20
                itemView.subtitle.setText(R.string.processing_home_step_1)
            }
            BlockchainIdentityData.CreationState.IDENTITY_REGISTERING,
            BlockchainIdentityData.CreationState.IDENTITY_REGISTERED -> {
                itemView.progress.progress = 40
                itemView.subtitle.setText(R.string.processing_home_step_2)
            }
            BlockchainIdentityData.CreationState.PREORDER_REGISTERING,
            BlockchainIdentityData.CreationState.PREORDER_REGISTERED,
            BlockchainIdentityData.CreationState.USERNAME_REGISTERING,
            BlockchainIdentityData.CreationState.USERNAME_REGISTERED -> {
                itemView.progress.progress = 60
                itemView.subtitle.setText(
                        if (blockchainIdentityData.creationStateError) R.string.processing_username_unavailable_subtitle
                        else R.string.processing_home_step_3
                )
            }
            BlockchainIdentityData.CreationState.DASHPAY_PROFILE_CREATING -> {
                itemView.progress.progress = 80
                itemView.subtitle.setText(R.string.processing_home_step_4)
            }
            BlockchainIdentityData.CreationState.DASHPAY_PROFILE_CREATED -> {
                itemView.icon.visibility = View.GONE
                itemView.forward_arrow.visibility = View.VISIBLE
                itemView.progress.visibility = View.GONE
                itemView.title.text = itemView.context.getString(R.string.processing_done_title,
                        blockchainIdentityData.username)
                itemView.subtitle.setText(R.string.processing_done_subtitle)
            }
        }
    }
}