package de.schildbach.wallet;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import de.schildbach.wallet.data.BlockchainState;
import de.schildbach.wallet.data.BlockchainStateDao;
import de.schildbach.wallet.data.IdentityCreationState;
import de.schildbach.wallet.data.IdentityCreationStateDao;
import de.schildbach.wallet.data.IdentityCreationStateDaoAsync;
import de.schildbach.wallet.data.RoomConverters;
import de.schildbach.wallet.rates.ExchangeRate;
import de.schildbach.wallet.rates.ExchangeRatesDao;

/**
 * @author Samuel Barbosa
 */
@Database(entities = {ExchangeRate.class, BlockchainState.class, IdentityCreationState.class}, version = 6)
@TypeConverters({RoomConverters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase instance;

    public abstract ExchangeRatesDao exchangeRatesDao();

    public abstract BlockchainStateDao blockchainStateDao();

    public abstract IdentityCreationStateDao identityCreationStateDao();

    public abstract IdentityCreationStateDaoAsync identityCreationStateDaoAsync();

    public static AppDatabase getAppDatabase() {
        if (instance == null) {
            instance = Room.databaseBuilder(WalletApplication.getInstance(), AppDatabase.class,
                    "dash-wallet-database").fallbackToDestructiveMigration().build();
        }
        return instance;
    }

}
