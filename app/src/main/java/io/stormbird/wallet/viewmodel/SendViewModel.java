package io.stormbird.wallet.viewmodel;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.stormbird.wallet.entity.Token;
import io.stormbird.wallet.entity.Wallet;
import io.stormbird.wallet.interact.ENSInteract;
import io.stormbird.wallet.repository.EthereumNetworkRepositoryType;
import io.stormbird.wallet.router.ConfirmationRouter;
import io.stormbird.wallet.router.MyAddressRouter;
import io.stormbird.wallet.service.AssetDefinitionService;

import java.math.BigInteger;

public class SendViewModel extends BaseViewModel {
    private final MutableLiveData<String> ensResolve = new MutableLiveData<>();
    private final MutableLiveData<String> ensFail = new MutableLiveData<>();

    private final ConfirmationRouter confirmationRouter;
    private final MyAddressRouter myAddressRouter;
    private final ENSInteract ensInteract;
    private final AssetDefinitionService assetDefinitionService;
    private final EthereumNetworkRepositoryType networkRepository;

    public SendViewModel(ConfirmationRouter confirmationRouter,
                         MyAddressRouter myAddressRouter,
                         ENSInteract ensInteract,
                         AssetDefinitionService assetDefinitionService,
                         EthereumNetworkRepositoryType ethereumNetworkRepositoryType) {
        this.confirmationRouter = confirmationRouter;
        this.myAddressRouter = myAddressRouter;
        this.ensInteract = ensInteract;
        this.assetDefinitionService = assetDefinitionService;
        this.networkRepository = ethereumNetworkRepositoryType;
    }

    public LiveData<String> ensResolve() { return ensResolve; }
    public LiveData<String> ensFail() { return ensFail; }

    public void openConfirmation(Context context, String to, BigInteger amount, String contractAddress, int decimals, String symbol, boolean sendingTokens, String ensDetails) {
        confirmationRouter.open(context, to, amount, contractAddress, decimals, symbol, sendingTokens, ensDetails);
    }

    public void showMyAddress(Context context, Wallet wallet) {
        myAddressRouter.open(context, wallet);
    }

    public void showContractInfo(Context ctx, Wallet wallet, Token token)
    {
        myAddressRouter.open(ctx, wallet, token);
    }

    public String getChainName(int chainId)
    {
        return networkRepository.getNameById(chainId);
    }

    public void checkENSAddress(String name)
    {
        if (name == null || name.length() < 1) return;
        disposable = ensInteract.checkENSAddress (name)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ensResolve::postValue, throwable -> ensFail.postValue(""));
    }

    public boolean hasIFrame(String address)
    {
        return assetDefinitionService.hasIFrame(address);
    }

    public String getTokenData(String address)
    {
        return assetDefinitionService.getIntroductionCode(address);
    }
}
