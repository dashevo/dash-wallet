/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.data;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.BlockchainServiceImpl;
import de.schildbach.wallet.service.BlockchainState;

/**
 * @author Andreas Schildbach
 */
public class BlockchainStateLiveData extends LiveData<BlockchainState> implements ServiceConnection {

    private final WalletApplication application;
    private final LocalBroadcastManager broadcastManager;

    public BlockchainStateLiveData(final WalletApplication application) {
        Log.d("blockchainState", "livedata constructor");
        this.application = application;
        this.broadcastManager = LocalBroadcastManager.getInstance(application);
    }

    @Override
    protected void onActive() {
        Log.d("blockchainState", "livedata onActive");
        broadcastManager.registerReceiver(receiver, new IntentFilter(BlockchainService.ACTION_BLOCKCHAIN_STATE));
        application.bindService(new Intent(application, BlockchainService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onInactive() {
        Log.d("blockchainState", "livedata onInactive");
        application.unbindService(this);
        broadcastManager.unregisterReceiver(receiver);
    }

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        Log.d("blockchainState", "livedata onServiceConnected");
        final BlockchainService blockchainService = ((BlockchainServiceImpl.LocalBinder) service).getService();
        setValue(blockchainService.getBlockchainState());
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
        Log.d("blockchainState", "livedata onServiceDisconnected");
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent broadcast) {
            Log.d("blockchainState", "livedata onReceive");
            setValue(BlockchainState.fromIntent(broadcast));
        }
    };
}
