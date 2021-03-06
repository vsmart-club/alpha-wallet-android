package io.stormbird.wallet.ui;

import android.arch.lifecycle.ViewModelProviders;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import dagger.android.AndroidInjection;
import io.stormbird.wallet.C;
import io.stormbird.wallet.R;
import io.stormbird.wallet.entity.*;
import io.stormbird.wallet.repository.TokenRepositoryType;
import io.stormbird.wallet.ui.widget.adapter.AutoCompleteUrlAdapter;
import io.stormbird.wallet.ui.widget.entity.AmountEntryItem;
import io.stormbird.wallet.ui.widget.entity.ENSHandler;
import io.stormbird.wallet.ui.widget.entity.ItemClickListener;
import io.stormbird.wallet.ui.zxing.FullScannerFragment;
import io.stormbird.wallet.ui.zxing.QRScanningActivity;
import io.stormbird.wallet.util.BalanceUtils;
import io.stormbird.wallet.util.KeyboardUtils;
import io.stormbird.wallet.util.QRURLParser;
import io.stormbird.wallet.viewmodel.SendViewModel;
import io.stormbird.wallet.viewmodel.SendViewModelFactory;
import io.stormbird.wallet.widget.AWalletAlertDialog;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import static io.stormbird.wallet.C.Key.WALLET;
import static io.stormbird.wallet.repository.EthereumNetworkRepository.MAINNET_ID;

public class SendActivity extends BaseActivity implements Runnable, ItemClickListener, AmountUpdateCallback {
    private static final int BARCODE_READER_REQUEST_CODE = 1;

    @Inject
    SendViewModelFactory sendViewModelFactory;
    SendViewModel viewModel;
    @Inject
    protected TokenRepositoryType tokenRepository;

    // In case we're sending tokens
    private boolean sendingTokens = false;
    private String myAddress;
    private int decimals;
    private String symbol;
    private Wallet wallet;
    private Token token;
    private String contractAddress;
    private ENSHandler ensHandler;
    private Handler handler;
    private AWalletAlertDialog dialog;
    private int chainId;

    private ImageButton scanQrImageView;
    private TextView tokenBalanceText;
    private TextView tokenSymbolText;
    private AutoCompleteTextView toAddressEditText;
    private TextView pasteText;
    private Button nextBtn;
    private String currentAmount;

    private AmountEntryItem amountInput;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AndroidInjection.inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);
        toolbar();
        setTitle("");

        viewModel = ViewModelProviders.of(this, sendViewModelFactory)
                .get(SendViewModel.class);
        handler = new Handler();

        contractAddress = getIntent().getStringExtra(C.EXTRA_CONTRACT_ADDRESS);
        decimals = getIntent().getIntExtra(C.EXTRA_DECIMALS, C.ETHER_DECIMALS);
        symbol = getIntent().getStringExtra(C.EXTRA_SYMBOL);
        symbol = symbol == null ? C.ETH_SYMBOL : symbol;
        sendingTokens = getIntent().getBooleanExtra(C.EXTRA_SENDING_TOKENS, false);
        wallet = getIntent().getParcelableExtra(WALLET);
        token = getIntent().getParcelableExtra(C.EXTRA_TOKEN_ID);
        chainId = getIntent().getIntExtra(C.EXTRA_NETWORKID, MAINNET_ID);
        myAddress = wallet.address;

        setupTokenContent();
        initViews();
        setupAddressEditField();

        if (token.addressMatches(myAddress)) {
            amountInput = new AmountEntryItem(this, tokenRepository, symbol, true);
        } else {
            //currently we don't evaluate ERC20 token value. TODO: Should we?
            amountInput = new AmountEntryItem(this, tokenRepository, symbol, false);
        }
    }

    private void initViews() {

        toAddressEditText = findViewById(R.id.edit_to_address);

        pasteText = findViewById(R.id.paste);
        pasteText.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            try {
                CharSequence textToPaste = clipboard.getPrimaryClip().getItemAt(0).getText();
                toAddressEditText.setText(textToPaste);
            } catch (Exception e) {
                Log.e(SendActivity.class.getSimpleName(), e.getMessage(), e);
            }
        });

        nextBtn = findViewById(R.id.button_next);
        nextBtn.setOnClickListener(v -> {
            onNext();
        });

        scanQrImageView = findViewById(R.id.img_scan_qr);
        scanQrImageView.setOnClickListener(v -> {
            Intent intent = new Intent(this, QRScanningActivity.class);
            startActivityForResult(intent, BARCODE_READER_REQUEST_CODE);
        });
    }

    private void setupAddressEditField() {
        AutoCompleteUrlAdapter adapterUrl = new AutoCompleteUrlAdapter(getApplicationContext(), C.ENS_HISTORY);
        adapterUrl.setListener(this);
        ENSCallback ensCallback = new ENSCallback() {
            @Override
            public void ENSComplete() {
                onNext();
            }

            @Override
            public void ENSCheck(String name) {
                viewModel.checkENSAddress(name);
            }
        };
        ensHandler = new ENSHandler(this, handler, adapterUrl, this, ensCallback);
        viewModel.ensResolve().observe(this, ensHandler::onENSSuccess);
        viewModel.ensFail().observe(this, ensHandler::hideENS);
    }

    private void onNext() {
        KeyboardUtils.hideKeyboard(getCurrentFocus());
        boolean isValid = amountInput.checkValidAmount();

        if (!isBalanceEnough(currentAmount)) {
            amountInput.setError(R.string.error_insufficient_funds);
            isValid = false;
        }

        String to = ensHandler.getAddressFromEditView();
        if (to == null) return;

        if (isValid) {
            BigInteger amountInSubunits = BalanceUtils.baseToSubunit(currentAmount, decimals);
            viewModel.openConfirmation(this, to, amountInSubunits, contractAddress, decimals, symbol, sendingTokens, ensHandler.getEnsName());
        }
    }

    private void onBack() {
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBack();
                break;
            }
            case R.id.action_qr:
                viewModel.showContractInfo(this, wallet, token);
                break;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        onBack();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BARCODE_READER_REQUEST_CODE) {
            if (resultCode == FullScannerFragment.SUCCESS) {
                if (data != null) {
                    String barcode = data.getParcelableExtra(FullScannerFragment.BarcodeObject);
                    if (barcode == null)
                        barcode = data.getStringExtra(FullScannerFragment.BarcodeObject);

                    //if barcode is still null, ensure we don't GPF
                    if (barcode == null) {
                        //Toast.makeText(this, R.string.toast_qr_code_no_address, Toast.LENGTH_SHORT).show();
                        displayScanError();
                        return;
                    }

                    QRURLParser parser = QRURLParser.getInstance();
                    QrUrlResult result = parser.parse(barcode);
                    String extracted_address = null;
                    if (result != null)
                    {
                        extracted_address = result.getAddress();
                        switch (result.getProtocol())
                        {
                            case "address":
                                break;
                            case "ethereum":
                                //EIP681 protocol
                                validateEIP681Request(result);
                                break;
                            default:
                                break;
                        }

                        toAddressEditText.setText(extracted_address);
                    }

                    if (extracted_address == null)
                    {
                        displayScanError();
                    }
                }
            } else {
                Log.e("SEND", String.format(getString(R.string.barcode_error_format),
                        "Code: " + String.valueOf(resultCode)
                ));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void validateEIP681Request(QrUrlResult result)
    {
        //check chain
        if (result.chainId == 0)
        {
            displayScanError();
        }
        else if (result.chainId != chainId)
        {
            //name of correct chain
            String chainName = viewModel.getChainName(result.chainId);
            String message = getString(R.string.wrong_chain, chainName);
            displayScanError(R.string.wrong_chain_title, message);
        }
        else if (result.getFunction().length() == 0 && !sendingTokens)
        {
            //correct chain and asset type
            String ethAmount = BalanceUtils.weiToEth(new BigDecimal(result.weiValue)).setScale(4, RoundingMode.HALF_DOWN).stripTrailingZeros().toPlainString();
            amountInput.setAmount(ethAmount);
            TextView sendText = findViewById(R.id.text_payment_request);
            sendText.setVisibility(View.VISIBLE);
            sendText.setText(R.string.transfer_request);
        }
        else if (result.getFunction().length() > 0 && result.getAddress().equals(token.getAddress()))
        {
            //sending ERC20 and we're on the correct asset
            BigDecimal decimalDivisor = new BigDecimal(Math.pow(10, token.tokenInfo.decimals));
            BigDecimal sendAmount = new BigDecimal(result.weiValue);
            BigDecimal erc20Amount = token.tokenInfo.decimals > 0
                    ? sendAmount.divide(decimalDivisor) : sendAmount;
            String erc20AmountStr = erc20Amount.setScale(4, RoundingMode.HALF_DOWN).stripTrailingZeros().toPlainString();
            amountInput.setAmount(erc20AmountStr);

            //show function which will be called:
            TextView sendText = findViewById(R.id.text_payment_request);
            sendText.setVisibility(View.VISIBLE);
            sendText.setText(R.string.token_transfer_request);

            TextView contractText = findViewById(R.id.text_contract_call);
            contractText.setVisibility(View.VISIBLE);
            contractText.setText(result.functionDetail);
        }
        else
        {
            //TODO: fetch Token name
            String message = getString(R.string.wrong_token, result.getAddress());
            displayScanError(R.string.wrong_token_title, message);
        }
    }

    private void displayScanError()
    {
        dialog = new AWalletAlertDialog(this);
        dialog.setIcon(AWalletAlertDialog.ERROR);
        dialog.setTitle(R.string.toast_qr_code_no_address);
        dialog.setButtonText(R.string.dialog_ok);
        dialog.setButtonListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void displayScanError(int titleId, String message)
    {
        dialog = new AWalletAlertDialog(this);
        dialog.setIcon(AWalletAlertDialog.ERROR);
        dialog.setTitle(titleId);
        dialog.setMessage(message);
        dialog.setButtonText(R.string.dialog_ok);
        dialog.setButtonListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        amountInput.onClear();
    }

    boolean isBalanceEnough(String eth) {
        try {
            BigDecimal amount = new BigDecimal(BalanceUtils.EthToWei(eth));
            BigDecimal balance = new BigDecimal(BalanceUtils.EthToWei(tokenBalanceText.getText().toString()));
            return (balance.subtract(amount).compareTo(BigDecimal.ZERO) == 0 || balance.subtract(amount).compareTo(BigDecimal.ZERO) > 0);
        } catch (Exception e) {
            return false;
        }
    }

    public void setupTokenContent() {
        tokenBalanceText = findViewById(R.id.balance_eth);
        tokenSymbolText = findViewById(R.id.symbol);

        tokenSymbolText.setText(TextUtils.isEmpty(token.tokenInfo.name)
                ? token.tokenInfo.symbol.toUpperCase()
                : getString(R.string.token_name, token.tokenInfo.name, token.tokenInfo.symbol.toUpperCase()));

        TokenInfo tokenInfo = token.tokenInfo;
        BigDecimal decimalDivisor = new BigDecimal(Math.pow(10, tokenInfo.decimals));
        BigDecimal ethBalance = tokenInfo.decimals > 0
                ? token.balance.divide(decimalDivisor) : token.balance;
        ethBalance = ethBalance.setScale(4, RoundingMode.HALF_DOWN).stripTrailingZeros();
        String value = ethBalance.compareTo(BigDecimal.ZERO) == 0 ? "0" : ethBalance.toPlainString();
        tokenBalanceText.setText(value);

        tokenBalanceText.setVisibility(View.VISIBLE);
    }

    @Override
    public void run() {
        ensHandler.checkENS();
    }

    @Override
    public void onItemClick(String url) {
        ensHandler.handleHistoryItemClick(url);
    }

    @Override
    public void amountChanged(String newAmount)
    {
        currentAmount = newAmount;
    }
}
