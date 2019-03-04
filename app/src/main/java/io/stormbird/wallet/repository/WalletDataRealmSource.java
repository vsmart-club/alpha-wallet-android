package io.stormbird.wallet.repository;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.realm.Realm;
import io.realm.RealmResults;
import io.stormbird.wallet.entity.Wallet;
import io.stormbird.wallet.repository.entity.RealmWalletData;
import io.stormbird.wallet.service.RealmManager;

/**
 * Created by James on 8/11/2018.
 * Stormbird in Singapore
 */

public class WalletDataRealmSource
{
    private final RealmManager realmManager;
    public WalletDataRealmSource(RealmManager realmManager) {
        this.realmManager = realmManager;
    }

    public Single<Wallet[]> loadWallets()
    {
        return Single.fromCallable(() -> {
            List<Wallet> wallets = new ArrayList<>();
            try (Realm realm = realmManager.getWalletDataRealmInstance())
            {
                RealmResults<RealmWalletData> realmItems = realm.where(RealmWalletData.class)
                        .findAll();

                for (RealmWalletData data : realmItems)
                {
                    Wallet thisWallet = convertWallet(data);
                    wallets.add(thisWallet);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return wallets.toArray(new Wallet[0]);
        });
    }

    private Wallet convertWallet(RealmWalletData data)
    {
        Wallet wallet = new Wallet(data.getAddress());
        wallet.ENSname = data.getENSName();
        wallet.balance = data.getBalance();
        wallet.name = data.getName();
        return wallet;
    }

    public Single<Integer> storeWallets(Wallet[] wallets, boolean mainNet)
    {
        return Single.fromCallable(() -> {
            Integer updated = 0;
            try (Realm realm = realmManager.getWalletDataRealmInstance())
            {
                realm.beginTransaction();

                for (Wallet wallet : wallets)
                {
                    RealmWalletData realmWallet = realm.where(RealmWalletData.class)
                            .equalTo("address", wallet.address)
                            .findFirst();

                    if (realmWallet == null)
                    {
                        realmWallet = realm.createObject(RealmWalletData.class, wallet.address);
                        realmWallet.setENSName(wallet.ENSname);
                        if (mainNet) realmWallet.setBalance(wallet.balance);
                        realmWallet.setName(wallet.name);

                        updated++;
                    }
                    else
                    {
                        if (mainNet && (realmWallet.getBalance() == null || !wallet.balance.equals(realmWallet.getENSName())))
                            realmWallet.setBalance(wallet.balance);
                        if (wallet.ENSname != null && (realmWallet.getENSName() == null || !wallet.ENSname.equals(realmWallet.getENSName())))
                            realmWallet.setENSName(wallet.ENSname);

//                        realmWallet.setName(wallet.name);
                        updated++;
                    }
                }

                realm.commitTransaction();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            return updated;
        });
    }
}
