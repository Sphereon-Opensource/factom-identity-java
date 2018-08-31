package org.blockchain_innovation.factom.client.impl;

import org.blockchain_innovation.factom.client.api.FactomException;
import org.blockchain_innovation.factom.client.api.FactomResponse;
import org.blockchain_innovation.factom.client.api.model.Chain;
import org.blockchain_innovation.factom.client.api.model.Entry;
import org.blockchain_innovation.factom.client.api.model.response.CommitAndRevealChainResponse;
import org.blockchain_innovation.factom.client.api.model.response.CommitAndRevealEntryResponse;
import org.blockchain_innovation.factom.client.api.model.response.factomd.CommitChainResponse;
import org.blockchain_innovation.factom.client.api.model.response.factomd.CommitEntryResponse;
import org.blockchain_innovation.factom.client.api.model.response.factomd.EntryTransactionResponse;
import org.blockchain_innovation.factom.client.api.model.response.factomd.RevealResponse;
import org.blockchain_innovation.factom.client.api.model.response.walletd.ComposeResponse;
import org.blockchain_innovation.factom.client.impl.listeners.ChainCommitAndRevealListener;
import org.blockchain_innovation.factom.client.impl.listeners.EntryCommitAndRevealListener;
import org.blockchain_innovation.factom.client.impl.listeners.SimpleChainCommitAndRevealListener;
import org.blockchain_innovation.factom.client.impl.listeners.SimpleEntryCommitAndRevealListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class EntryClient {

    private static final int ENTRY_REVEAL_WAIT = 2000;
    private final Logger logger = LoggerFactory.getLogger(EntryClient.class);
    private int transactionAcknowledgeTimeout = 10000; // 10 sec
    private int commitConfirmedTimeout = 15 * 60000; // 15 min

    private FactomdClient factomdClient;
    private WalletdClient walletdClient;

    private FactomdClient getFactomdClient() throws FactomException.ClientException {
        if (factomdClient == null) {
            throw new FactomException.ClientException("factomd client not provided");
        }
        return factomdClient;
    }

    public EntryClient setFactomdClient(FactomdClient factomdClient) {
        this.factomdClient = factomdClient;
        return this;
    }

    private WalletdClient getWalletdClient() throws FactomException.ClientException {
        if (walletdClient == null) {
            throw new FactomException.ClientException("walletd client not provided");
        }
        return walletdClient;
    }

    public EntryClient setWalletdClient(WalletdClient walletdClient) {
        this.walletdClient = walletdClient;
        return this;
    }

    public int getTransactionAcknowledgeTimeout() {
        return transactionAcknowledgeTimeout;
    }

    public EntryClient setTransactionAcknowledgeTimeout(int transactionAcknowledgeTimeout) {
        this.transactionAcknowledgeTimeout = transactionAcknowledgeTimeout;
        return this;
    }

    public int getCommitConfirmedTimeout() {
        return commitConfirmedTimeout;
    }

    public EntryClient setCommitConfirmedTimeout(int commitConfirmedTimeout) {
        this.commitConfirmedTimeout = commitConfirmedTimeout;
        return this;
    }

    /**
     * Compose, reveal and commit a chain
     *
     * @param chain
     * @param entryCreditAddress
     * @throws FactomException.ClientException
     */
    public CompletableFuture<CommitAndRevealChainResponse> commitAndRevealChain(Chain chain, String entryCreditAddress) throws FactomException.ClientException {
        return commitAndRevealChain(chain, entryCreditAddress, new SimpleChainCommitAndRevealListener());
    }

    /**
     * Compose, reveal and commit a chain
     *
     * @param chain
     * @param entryCreditAddress
     * @param listener
     * @return
     */
    public CompletableFuture<CommitAndRevealChainResponse> commitAndRevealChain(Chain chain, String entryCreditAddress, final ChainCommitAndRevealListener listener) {
        // after compose chain combine commit and reveal chain
        CompletableFuture<CommitAndRevealChainResponse> commitAndRevealChainFuture = composeChainFuture(chain, entryCreditAddress)
                .thenApply(_composeChainResponse -> handleResponse(listener, listener::onCompose, _composeChainResponse))
                // commit chain
                .thenCompose(_composeChainResponse -> commitChainFuture(_composeChainResponse)
                        .thenApply(_commitChainResponse -> handleResponse(listener, listener::onCommit, _commitChainResponse))
                        // wait to transaction is known
                        .thenCompose(_commitChainResponse -> waitFuture()
                                // reveal chain
                                .thenCompose(_void -> revealChainFuture(_composeChainResponse)
                                        .thenApply(_revealChainResponse -> handleResponse(listener, listener::onReveal, _revealChainResponse))
                                        // wait for transaction acknowledgement
                                        .thenCompose(_revealChainResponse -> transactionAcknowledgeConfirmation(_revealChainResponse)
                                                .thenApply(_transactionAcknowledgeResponse -> handleResponse(listener, listener::onTransactionAcknowledged, _transactionAcknowledgeResponse))
                                                .thenCompose(_transactionAcknowledgeResponse -> transactionCommitConfirmation(_revealChainResponse)
                                                        .thenApply(_commitConfirmedResponse -> {
                                                            handleResponse(listener, listener::onCommitConfirmed, _commitConfirmedResponse);
                                                            // create response
                                                            CommitAndRevealChainResponse response = new CommitAndRevealChainResponse();
                                                            response.setCommitChainResponse(_commitChainResponse.getResult());
                                                            response.setRevealResponse(_revealChainResponse.getResult());
                                                            return response;
                                                        }))))));
        return commitAndRevealChainFuture;
    }

    /**
     * Compose, reveal and commit an entry
     *
     * @param entry
     * @param entryCreditAddress
     * @throws FactomException.ClientException
     */
    public CompletableFuture<CommitAndRevealEntryResponse> commitAndRevealEntry(Entry entry, String entryCreditAddress) throws FactomException.ClientException {
        SimpleEntryCommitAndRevealListener listener = new SimpleEntryCommitAndRevealListener();
        return commitAndRevealEntry(entry, entryCreditAddress, listener);
    }

    public CompletableFuture<CommitAndRevealEntryResponse> commitAndRevealEntry(Entry entry, String entryCreditAddress, EntryCommitAndRevealListener listener) throws FactomException.ClientException {
        // after compose entry combine commit and reveal entry
        CompletableFuture<CommitAndRevealEntryResponse> commitAndRevealEntryFuture = composeEntryFuture(entry, entryCreditAddress)
                .thenApply(_composeEntryResponse -> handleResponse(listener, listener::onCompose, _composeEntryResponse))
                // commit chain
                .thenCompose(_composeEntryResponse -> commitEntryFuture(_composeEntryResponse)
                        .thenApply(_commitEntryResponse -> handleResponse(listener, listener::onCommit, _commitEntryResponse))
                        // wait to transaction is known
                        .thenCompose(_commitEntryResponse -> waitFuture()
                                // reveal chain
                                .thenCompose(_void -> revealEntryFuture(_composeEntryResponse)
                                        .thenApply(_revealEntryResponse -> handleResponse(listener, listener::onReveal, _revealEntryResponse))
                                        // wait for transaction acknowledgement
                                        .thenCompose(_revealEntryResponse -> transactionAcknowledgeConfirmation(_revealEntryResponse)
                                                .thenApply(_transactionAcknowledgeResponse -> handleResponse(listener, listener::onTransactionAcknowledged, _transactionAcknowledgeResponse))
                                                // wait for block confirmed
                                                .thenCompose(_transactionAcknowledgeResponse -> transactionCommitConfirmation(_revealEntryResponse)
                                                        .thenApply(_commitConfirmedResponse -> {
                                                            handleResponse(listener, listener::onCommitConfirmed, _commitConfirmedResponse);
                                                            // create response
                                                            CommitAndRevealEntryResponse response = new CommitAndRevealEntryResponse();
                                                            response.setCommitEntryResponse(_commitEntryResponse.getResult());
                                                            response.setRevealResponse(_revealEntryResponse.getResult());
                                                            return response;
                                                        }))))));

        return commitAndRevealEntryFuture;
    }

    private <T> FactomResponse<T> handleResponse(ChainCommitAndRevealListener listener, Consumer<T> listenerCall, FactomResponse<T> response) {
        if (response.hasErrors()) {
            listener.onError(response.getRpcErrorResponse());
        } else {
            listenerCall.accept(response.getResult());
        }
        return response;
    }

    private <T> FactomResponse<T> handleResponse(EntryCommitAndRevealListener listener, Consumer<T> listenerCall, FactomResponse<T> response) {
        if (response.hasErrors()) {
            listener.onError(response.getRpcErrorResponse());
        } else {
            listenerCall.accept(response.getResult());
        }
        return response;
    }

    private CompletableFuture<Void> waitFuture() {
        return CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(ENTRY_REVEAL_WAIT);
            } catch (InterruptedException e) {
                throw new FactomException.ClientException("interrupted while waiting on confirmation", e);
            }
        });
    }

    private CompletionStage<FactomResponse<EntryTransactionResponse>> transactionAcknowledgeConfirmation(FactomResponse<RevealResponse> revealChainResponse) {
        String entryHash = revealChainResponse.getResult().getEntryHash();
        String chainId = revealChainResponse.getResult().getChainId();
        return transactionConfirmation(entryHash, chainId, EntryTransactionResponse.Status.TransactionACK, transactionAcknowledgeTimeout, 1000);
    }

    private CompletableFuture<FactomResponse<EntryTransactionResponse>> transactionCommitConfirmation(FactomResponse<RevealResponse> revealChainResponse) {
        String entryHash = revealChainResponse.getResult().getEntryHash();
        String chainId = revealChainResponse.getResult().getChainId();
        return transactionConfirmation(entryHash, chainId, EntryTransactionResponse.Status.DBlockConfirmed, commitConfirmedTimeout, 60000);
    }

    private CompletableFuture<FactomResponse<EntryTransactionResponse>> transactionConfirmation(String entryHash, String chainId, EntryTransactionResponse.Status desiredStatus, int timeout, int sleepTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                FactomResponse<EntryTransactionResponse> transactionsResponse = null;
                boolean confirmed = false;
                int maxSeconds = timeout / sleepTime;
                int seconds = 0;
                while (!confirmed && seconds < maxSeconds) {
                    Thread.sleep(sleepTime);

                    logger.debug("Transaction verification of chain id={}, entry hash={} at {}", chainId, entryHash, seconds);
                    transactionsResponse = getFactomdClient().ackTransactions(entryHash, chainId, EntryTransactionResponse.class).join();

                    if (!transactionsResponse.hasErrors()) {
                        confirmed = desiredStatus == transactionsResponse.getResult().getCommitData().getStatus();
                    }
                    seconds++;
                }

                if (transactionsResponse == null) {
                    throw new FactomException.ClientException(String.format("Transaction of chain id=%s, entry hash=%s didn't return a response after %s. Probably will not succeed! ", chainId, entryHash, seconds));
                } else if (transactionsResponse.hasErrors()) {
                    logger.error("Transaction of chain id={}, entry hash={} received error after {}, errors={}. Probably will not succeed! ", chainId, entryHash, seconds, transactionsResponse.getRpcErrorResponse());
                } else if (!confirmed) {
                    EntryTransactionResponse.Status status = transactionsResponse.getResult().getCommitData().getStatus();
                    logger.error("Transaction of chain id={}, entry hash={} still not in desired status after {}, state = {}. Probably will not succeed! ", chainId, entryHash, seconds, status);
                }
                return transactionsResponse;
            } catch (InterruptedException e) {
                throw new FactomException.ClientException("interrupted while waiting on confirmation", e);
            }
        });
    }

    private CompletableFuture<FactomResponse<ComposeResponse>> composeChainFuture(Chain chain, String entryCreditAddress) {
        return getWalletdClient().composeChain(chain, entryCreditAddress);
    }

    private CompletableFuture<FactomResponse<CommitChainResponse>> commitChainFuture(FactomResponse<ComposeResponse> composeChain) {
        return getFactomdClient().commitChain(composeChain.getResult().getCommit().getParams().getMessage());
    }

    private CompletableFuture<FactomResponse<RevealResponse>> revealChainFuture(FactomResponse<ComposeResponse> composeChain) {
        return getFactomdClient().revealChain(composeChain.getResult().getReveal().getParams().getEntry());
    }

    private CompletableFuture<FactomResponse<ComposeResponse>> composeEntryFuture(Entry entry, String entryCreditAddress) {
        logger.info("commitEntryFuture");
        return getWalletdClient().composeEntry(entry, entryCreditAddress);
    }

    private CompletableFuture<FactomResponse<CommitEntryResponse>> commitEntryFuture(FactomResponse<ComposeResponse> composeEntry) {
        logger.info("commitEntryFuture");
        return getFactomdClient().commitEntry(composeEntry.getResult().getCommit().getParams().getMessage());
    }

    private CompletableFuture<FactomResponse<RevealResponse>> revealEntryFuture(FactomResponse<ComposeResponse> composeEntry) {
        logger.info("revealEntryFuture");
        return getFactomdClient().revealEntry(composeEntry.getResult().getReveal().getParams().getEntry());
    }
}
